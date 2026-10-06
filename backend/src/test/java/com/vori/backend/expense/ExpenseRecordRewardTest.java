package com.vori.backend.expense;

import com.vori.backend.category.Category;
import com.vori.backend.category.CategoryRepository;
import com.vori.backend.common.StatType;
import com.vori.backend.expense.dto.ExpenseCreateRequest;
import com.vori.backend.goal.GoalRepository;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.stats.UserStatStats;
import com.vori.backend.stats.UserStatStatsRepository;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 지출 기록 습관 보상(코인 100)은 하루 한 번만 준다.
 *
 * 등록할 때마다 100 을 주고 삭제해도 회수하지 않아, 등록→삭제를 반복하면 코인이 끝없이 쌓였다.
 * 지급 여부를 지출 행이 아니라 사용자(마지막 지급일)에 남겨, 지출을 지워도 다시 받지 못한다.
 */
class ExpenseRecordRewardTest {
    private static final long USER_ID = 1L;
    private static final long CATEGORY_ID = 3L;

    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final UserStatStatsRepository stats = mock(UserStatStatsRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ExpenseService service = new ExpenseService(expenses, categories, stats, users,
            mock(GoalRepository.class), mock(ApplicationEventPublisher.class),
            new ExpenseCalculationService(mock(SignalConfigService.class)), mock(AiInquiryRepository.class));

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).role(Role.USER).gameMoney(0).totalSaved(0).build();
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(categories.findById(CATEGORY_ID)).thenReturn(Optional.of(
                Category.builder().id(CATEGORY_ID).name("식비").statType(StatType.ENERGY).build()));
        when(stats.findByUserIdAndStatType(USER_ID, StatType.ENERGY)).thenReturn(Optional.of(
                UserStatStats.builder().userId(USER_ID).statType(StatType.ENERGY)
                        .meanEma(new BigDecimal("10000.00")).stddevEma(new BigDecimal("2000.00"))
                        .sampleCount(1).build()));
        when(expenses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ExpenseCreateRequest request() {
        return new ExpenseCreateRequest(CATEGORY_ID, 20000, "점심", null, null, null, null, false);
    }

    @Test
    void 같은_날_여러_번_기록해도_보상은_한_번() {
        // 등록 → (삭제) → 다시 등록. 삭제는 지급 기록을 건드리지 않으므로 지출 행이 없어도 같다.
        service.createExpense(USER_ID, request());
        service.createExpense(USER_ID, request());
        service.createExpense(USER_ID, request());

        assertEquals(100, user.getGameMoney());
    }

    @Test
    void 다음_날_첫_기록에는_다시_준다() {
        user.grantDailyRecordReward(LocalDate.now().minusDays(1), 100);

        service.createExpense(USER_ID, request());

        assertEquals(200, user.getGameMoney());
    }
}
