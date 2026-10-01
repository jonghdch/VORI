package com.vori.backend.expense;

import com.vori.backend.category.Category;
import com.vori.backend.category.CategoryRepository;
import com.vori.backend.common.PaymentMethod;
import com.vori.backend.common.StatType;
import com.vori.backend.expense.dto.ExpenseUpdateRequest;
import com.vori.backend.goal.GoalRepository;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.stats.UserStatStats;
import com.vori.backend.stats.UserStatStatsRepository;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 지출 수정이 누적 절약액(users.total_saved)을 같이 맞추는지 확인한다.
 *
 * 등록은 절약액만큼 total_saved 를 올리고 삭제는 그만큼 내리는데, 수정은 지출의 saved_amount 만
 * 다시 계산하고 total_saved 는 그대로 두었다. 5,000원을 50,000원으로 고치면 그 지출은 더 이상
 * 절약이 아닌데도 누적 절약액에는 남았다.
 */
class ExpenseServiceUpdateTest {
    private static final long USER_ID = 1L;
    private static final long EXPENSE_ID = 10L;
    private static final long CATEGORY_ID = 3L;

    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final UserStatStatsRepository stats = mock(UserStatStatsRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GoalRepository goals = mock(GoalRepository.class);
    private final PetRepository pets = mock(PetRepository.class);
    private final PetGrowthLogRepository growthLogs = mock(PetGrowthLogRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final SignalConfigService signalConfig = mock(SignalConfigService.class);
    private final AiInquiryRepository inquiries = mock(AiInquiryRepository.class);
    private final ExpenseService service = new ExpenseService(expenses, categories, stats, users,
            goals, pets, growthLogs, events, signalConfig, inquiries);

    @BeforeEach
    void setUp() {
        when(categories.findById(CATEGORY_ID)).thenReturn(Optional.of(
                Category.builder().id(CATEGORY_ID).name("식비").statType(StatType.ENERGY).build()));
        // 평균 10,000원. 표본이 N_MIN 미만이라 z 를 계산하지 않고 GREEN 으로 판정한다.
        when(stats.findByUserIdAndStatType(USER_ID, StatType.ENERGY)).thenReturn(Optional.of(
                UserStatStats.builder().userId(USER_ID).statType(StatType.ENERGY)
                        .meanEma(new BigDecimal("10000.00")).stddevEma(new BigDecimal("2000.00"))
                        .sampleCount(1).build()));
        when(inquiries.findByExpenseId(EXPENSE_ID)).thenReturn(Optional.empty());
    }

    @Test
    void raisingAmountAboveAverageTakesItsSavingOutOfTotalSaved() {
        User user = userWithTotalSaved(5000);
        Expense expense = savedExpense(5000, 5000);

        service.updateExpense(USER_ID, EXPENSE_ID, request(50000));

        assertEquals(-40000, expense.getSavedAmount());
        assertEquals(0, user.getTotalSaved());
    }

    @Test
    void loweringAmountAddsOnlyTheDifference() {
        User user = userWithTotalSaved(7000);
        Expense expense = savedExpense(8000, 2000);

        service.updateExpense(USER_ID, EXPENSE_ID, request(4000));

        assertEquals(6000, expense.getSavedAmount());
        assertEquals(11000, user.getTotalSaved());
    }

    @Test
    void droppingBelowAverageStartsCountingTheSaving() {
        User user = userWithTotalSaved(0);
        savedExpense(30000, -20000);

        service.updateExpense(USER_ID, EXPENSE_ID, request(9000));

        assertEquals(1000, user.getTotalSaved());
    }

    @Test
    void totalSavedNeverGoesBelowZero() {
        // 삭제와 같은 클램프 — 다른 경로로 누적값이 이미 줄어 있어도 음수가 되지 않는다.
        User user = userWithTotalSaved(1000);
        savedExpense(5000, 5000);

        service.updateExpense(USER_ID, EXPENSE_ID, request(50000));

        assertEquals(0, user.getTotalSaved());
    }

    @Test
    void sameSavingLeavesUserUntouched() {
        savedExpense(5000, 5000);

        service.updateExpense(USER_ID, EXPENSE_ID, request(5000));

        verifyNoInteractions(users);
    }

    private User userWithTotalSaved(int totalSaved) {
        User user = User.builder().id(USER_ID).role(Role.USER).totalSaved(totalSaved).build();
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    private Expense savedExpense(int amount, int savedAmount) {
        Expense expense = Expense.builder().id(EXPENSE_ID).userId(USER_ID).amount(amount)
                .item("점심").categoryId(CATEGORY_ID).statType(StatType.ENERGY)
                .savedAmount(savedAmount).build();
        when(expenses.findById(EXPENSE_ID)).thenReturn(Optional.of(expense));
        return expense;
    }

    private ExpenseUpdateRequest request(int amount) {
        return new ExpenseUpdateRequest("점심", amount, CATEGORY_ID, PaymentMethod.DEBIT);
    }
}
