package com.vori.backend.judgment;

import com.vori.backend.attendance.UserStatItemRepository;
import com.vori.backend.budget.SpendingPlanService;
import com.vori.backend.budget.UserStatBudget;
import com.vori.backend.budget.UserStatBudgetRepository;
import com.vori.backend.common.StatType;
import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.Signal;
import com.vori.backend.furniture.UserFurnitureRepository;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetStatRewardService;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 하루 판정 — 스탯 그룹별 월 예산을 하루 "봉투" 로 나눠 오늘 지출과 비교한다(SpendingPlan).
 * 남긴 예산 1,000원당 스탯 1(그룹당 최대 15), 코인은 남긴 금액의 1/100.
 */
class DailyJudgmentServiceTest {
    private final DailyJudgmentRepository judgments = mock(DailyJudgmentRepository.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PetRepository pets = mock(PetRepository.class);
    private final PetGrowthLogRepository growthLogs = mock(PetGrowthLogRepository.class);
    private final UserFurnitureRepository furniture = mock(UserFurnitureRepository.class);
    private final UserStatBudgetRepository budgets = mock(UserStatBudgetRepository.class);
    private final DailyJudgmentService service = new DailyJudgmentService(judgments, expenses, users, pets, growthLogs, furniture,
            mock(SpendingPlanService.class), budgets,
            new PetStatRewardService(mock(UserStatItemRepository.class)),
            mock(com.vori.backend.notification.NotificationService.class),
            mock(org.springframework.context.ApplicationEventPublisher.class));

    /** 그날 봉투가 하루 몫(3만원) 이상이 되도록 에너지 월 예산을 잡는다. 다른 그룹은 예산 0. */
    private void energyBudget(LocalDate date) {
        YearMonth month = YearMonth.from(date);
        when(budgets.findByUserIdAndYearMonth(1L, month.toString())).thenReturn(List.of(
                UserStatBudget.builder().userId(1L).yearMonth(month.toString())
                        .statType(StatType.ENERGY).amount(30_000 * month.lengthOfMonth()).build()));
    }

    private Pet activePet() {
        Pet pet = Pet.builder().id(9L).userId(1L).build();
        when(pets.findByUserIdAndReleasedAtIsNull(1L)).thenReturn(List.of(pet));
        return pet;
    }

    @Test
    void 예산을_남기면_그_그룹에_스탯과_코인을_준다() {
        LocalDate date = LocalDate.now();
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        Pet pet = activePet();
        energyBudget(date);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(judgments.findByUserIdAndJudgmentDate(1L, date)).thenReturn(Optional.empty());
        when(judgments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DailyJudgmentResponse result = service.judgeDate(user.getId(), user.getRole(), date);

        assertEquals(Signal.GREEN, result.signal());
        assertEquals(15, result.statRewards().get(StatType.ENERGY), "남긴 금액이 커도 그룹당 최대 15");
        assertEquals(0, result.statRewards().get(StatType.CHARM));
        assertEquals(15, pet.getStatEnergy());
        assertEquals(result.savedAmount() / 100, result.coinReward());
        assertEquals(result.coinReward(), user.getGameMoney());
    }

    @Test
    void 그날_봉투를_넘겨_쓰면_빨강이고_보상이_없다() {
        LocalDate date = LocalDate.now();
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        Pet pet = activePet();
        energyBudget(date);
        Expense big = Expense.builder().userId(1L).statType(StatType.ENERGY).amount(10_000_000)
                .signalFinal(Signal.RED).spentAt(date.atStartOfDay()).build();
        when(expenses.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(1L, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(big));
        when(expenses.findByUserIdAndSpentAtBetween(any(), any(), any())).thenReturn(List.of(big));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(judgments.findByUserIdAndJudgmentDate(1L, date)).thenReturn(Optional.empty());
        when(judgments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DailyJudgmentResponse result = service.judgeDate(user.getId(), user.getRole(), date);

        assertEquals(Signal.RED, result.signal());
        assertEquals(0, result.statRewards().get(StatType.ENERGY));
        assertEquals(0, pet.statTotal());
    }

    @Test
    void 이미_판정한_날은_다시_보상하지_않는다() {
        LocalDate date = LocalDate.now();
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        DailyJudgment existing = DailyJudgment.builder().userId(1L).judgmentDate(date)
                .signal(Signal.GREEN).expenseCount(0).coinReward(500).statRewardPerType(11)
                .judgedAt(LocalDateTime.now()).build();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(judgments.findByUserIdAndJudgmentDate(1L, date)).thenReturn(Optional.of(existing));

        DailyJudgmentResponse result = service.judgeDate(user.getId(), user.getRole(), date);

        assertTrue(result.alreadyJudged());
        assertEquals(0, user.getGameMoney());
        verifyNoInteractions(pets, growthLogs, furniture);
        verify(judgments, never()).save(any());
    }

    @Test
    void 일반_사용자는_다른_날짜를_판정할_수_없다() {
        User user = User.builder().id(2L).role(Role.USER).build();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.judgeDate(user.getId(), user.getRole(), LocalDate.now().minusDays(1)));
        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(expenses, judgments, users, pets, growthLogs, furniture);
    }

    @Test
    void 일반_사용자는_여는_시각_전에_판정할_수_없다() {
        // 24 는 어느 시각에 돌려도 "아직 안 열림" 이다.
        ReflectionTestUtils.setField(service, "openHour", 24);
        User user = User.builder().id(2L).role(Role.USER).build();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.judgeDate(user.getId(), user.getRole(), LocalDate.now()));

        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(expenses, judgments, users, pets, growthLogs, furniture);
    }

    @Test
    void 관리자는_여는_시각_전에도_지난_날짜를_다시_판정한다() {
        // 화면(config.canUseAiJudge)은 관리자를 시각과 상관없이 들여보낸다. 서버도 같아야 한다.
        // 관리자는 이미 판정한 날도 다시 계산해 갱신한다(시연·검증용).
        ReflectionTestUtils.setField(service, "openHour", 24);
        LocalDate date = LocalDate.now().minusDays(8);
        User admin = User.builder().id(3L).role(Role.ADMIN).gameMoney(0).build();
        DailyJudgment existing = DailyJudgment.builder().userId(3L).judgmentDate(date)
                .signal(Signal.RED).expenseCount(0).coinReward(0).statRewardPerType(0)
                .judgedAt(LocalDateTime.now()).build();
        when(users.findByIdForUpdate(3L)).thenReturn(Optional.of(admin));
        when(judgments.findByUserIdAndJudgmentDate(3L, date)).thenReturn(Optional.of(existing));

        DailyJudgmentResponse result = service.judgeDate(admin.getId(), admin.getRole(), date);

        assertFalse(result.alreadyJudged());
        assertEquals(Signal.GREEN, existing.getSignal(), "지출이 없으니 다시 계산하면 초록으로 갱신");
    }
}
