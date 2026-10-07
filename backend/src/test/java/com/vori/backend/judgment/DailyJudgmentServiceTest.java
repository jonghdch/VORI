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
import com.vori.backend.inquiry.AiInquiry;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.inquiry.ReasonCategory;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetStatRewardService;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    // 기본은 빈 목록 — 답할 예외 지출이 없어 1차 판정에서 바로 확정된다
    private final AiInquiryRepository inquiries = mock(AiInquiryRepository.class);
    private final DailyJudgmentService service = new DailyJudgmentService(judgments, expenses, users, pets, growthLogs, furniture,
            mock(SpendingPlanService.class), budgets,
            new PetStatRewardService(mock(UserStatItemRepository.class)),
            mock(com.vori.backend.notification.NotificationService.class),
            mock(org.springframework.context.ApplicationEventPublisher.class),
            inquiries);

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
    void 예산을_남기면_그_그룹에_스탯과_코인을_정하고_자정에_지급한다() {
        LocalDate date = LocalDate.now();
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        Pet pet = activePet();
        energyBudget(date);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        rememberSavedJudgment(date);

        DailyJudgmentResponse result = service.judgeDate(user.getId(), user.getRole(), date);

        assertEquals(Signal.GREEN, result.signal());
        assertEquals(JudgmentStatus.FINALIZED, result.status(), "답할 예외 지출이 없으면 바로 최종 판정");
        assertEquals(15, result.statRewards().get(StatType.ENERGY), "남긴 금액이 커도 그룹당 최대 15");
        assertEquals(0, result.statRewards().get(StatType.CHARM));
        assertEquals(result.savedAmount() / 100, result.coinReward());
        assertFalse(result.rewarded());
        assertEquals(0, user.getGameMoney(), "보상은 판정 때가 아니라 그날 자정에 준다");
        assertEquals(0, pet.getStatEnergy());

        // 판정 뒤에 지출이 바뀌어도 보상은 판정 때 정한 값 그대로 준다
        Expense later = Expense.builder().userId(1L).statType(StatType.ENERGY).amount(10_000_000)
                .signalInitial(Signal.RED).signalFinal(Signal.RED).spentAt(date.atStartOfDay()).build();
        when(expenses.findByUserIdAndSpentAtBetween(any(), any(), any())).thenReturn(List.of(later));

        assertTrue(service.payReward(SAVED_ID));
        assertEquals(result.coinReward(), user.getGameMoney());
        assertEquals(15, pet.getStatEnergy());
        assertFalse(service.payReward(SAVED_ID), "자정 정산이 두 번 돌아도 한 번만 준다");
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
    void 다시_열어도_판정한_순간과_같은_스탯별_결과와_절약액을_돌려준다() {
        LocalDate date = LocalDate.now();
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        activePet();
        energyBudget(date);
        Expense lunch = Expense.builder().userId(1L).statType(StatType.ENERGY).amount(1_234)
                .signalFinal(Signal.GREEN).spentAt(date.atStartOfDay()).build();
        when(expenses.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(1L, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(lunch));
        when(expenses.findByUserIdAndSpentAtBetween(any(), any(), any())).thenReturn(List.of(lunch));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(judgments.findByUserIdAndJudgmentDate(1L, date)).thenReturn(Optional.empty());
        ArgumentCaptor<DailyJudgment> stored = ArgumentCaptor.forClass(DailyJudgment.class);
        when(judgments.save(stored.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        DailyJudgmentResponse fresh = service.judgeDate(user.getId(), user.getRole(), date);
        when(judgments.findByUserIdAndJudgmentDate(1L, date)).thenReturn(Optional.of(stored.getValue()));
        DailyJudgmentResponse reopened = service.getByDate(1L, date).orElseThrow();

        // 예전엔 다시 열면 스탯별 결과가 비고 절약액이 코인 × 100 으로 깎였다
        assertEquals(fresh.groupJudgments(), reopened.groupJudgments());
        assertEquals(Signal.GREEN, reopened.groupJudgments().get(StatType.ENERGY).signal());
        assertEquals(fresh.savedAmount(), reopened.savedAmount());
    }

    @Test
    void V49_이전에_저장된_판정은_스탯별_결과_없이_코인으로_절약액을_어림한다() {
        LocalDate date = LocalDate.now();
        DailyJudgment legacy = DailyJudgment.builder().userId(1L).judgmentDate(date)
                .signal(Signal.RED).expenseCount(4).coinReward(95).statRewardPerType(6)
                .rewardDetails("ENERGY:2,IQ:6").judgedAt(LocalDateTime.now()).build();
        when(judgments.findByUserIdAndJudgmentDate(1L, date)).thenReturn(Optional.of(legacy));

        DailyJudgmentResponse reopened = service.getByDate(1L, date).orElseThrow();

        assertTrue(reopened.groupJudgments().isEmpty(), "없는 결과를 지어내지 않는다 — 화면은 「기록 없음」");
        assertEquals(9_500, reopened.savedAmount());
        assertEquals(6, reopened.statRewards().get(StatType.IQ));
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
                .judgedAt(LocalDateTime.now()).rewardedAt(LocalDateTime.now()).build();
        when(users.findByIdForUpdate(3L)).thenReturn(Optional.of(admin));
        when(judgments.findByUserIdAndJudgmentDate(3L, date)).thenReturn(Optional.of(existing));

        DailyJudgmentResponse result = service.judgeDate(admin.getId(), admin.getRole(), date);

        assertFalse(result.alreadyJudged());
        assertEquals(Signal.GREEN, existing.getSignal(), "지출이 없으니 다시 계산하면 초록으로 갱신");
        // 재판정은 스탯별 결과를 갱신해, 다시 열었을 때 방금 계산과 같아야 한다
        assertEquals(result.groupJudgments(), DailyJudgmentResponse.groupsFromJson(existing.getGroupDetails()));
        // 보상 칸(절약액·코인)은 실제로 지급한 값 그대로 — 재판정은 보상을 다시 주지 않는다(docs/judgment-flow.md 9절)
        assertNull(existing.getSavedAmount());
        assertEquals(0, existing.getCoinReward());
    }

    // ── 1차 판정 → 예외 지출 사유 → 확정 (docs/judgment-flow.md) ──

    /** 그날 ENERGY 봉투(3만원)를 넘긴 빨강 지출 하나. 답변 안 된 AI 질문이 달려 있다. */
    private Expense overspendWithPendingQuestion(LocalDate date) {
        Expense big = Expense.builder().id(50L).userId(1L).statType(StatType.ENERGY).amount(100_000)
                .signalInitial(Signal.RED).signalFinal(Signal.RED).spentAt(date.atStartOfDay()).build();
        when(expenses.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(1L, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(big));
        when(expenses.findByUserIdAndSpentAtBetween(any(), any(), any())).thenReturn(List.of(big));
        when(inquiries.findPendingByDate(eq(1L), any(), any()))
                .thenReturn(List.of(AiInquiry.pending(50L, 1L, "회식", 100_000)));
        return big;
    }

    private static final long SAVED_ID = 99L;

    /** save 한 판정 행을 다음 조회에서 돌려주게 한다 — 1차 판정 뒤 확정 요청이 같은 행을 읽는다. */
    private void rememberSavedJudgment(LocalDate date) {
        when(judgments.findByUserIdAndJudgmentDate(1L, date)).thenReturn(Optional.empty());
        when(judgments.save(any())).thenAnswer(invocation -> {
            DailyJudgment row = invocation.getArgument(0);
            ReflectionTestUtils.setField(row, "id", SAVED_ID);
            when(judgments.findByUserIdAndJudgmentDate(1L, date)).thenReturn(Optional.of(row));
            when(judgments.findById(SAVED_ID)).thenReturn(Optional.of(row));
            return row;
        });
    }

    @Test
    void 답할_예외_지출이_있으면_1차_판정만_하고_보상은_주지_않는다() {
        LocalDate date = LocalDate.now();
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        Pet pet = activePet();
        energyBudget(date);
        overspendWithPendingQuestion(date);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        rememberSavedJudgment(date);

        DailyJudgmentResponse first = service.judgeDate(1L, Role.USER, date);

        assertEquals(JudgmentStatus.PENDING, first.status());
        assertEquals(Signal.RED, first.initialSignal());
        assertEquals(0, user.getGameMoney(), "보상은 그날 자정에 준다");
        assertEquals(0, pet.statTotal());
        verifyNoInteractions(growthLogs);
    }

    @Test
    void 사유가_인정되면_최종_판정에서_그_그룹을_완화하고_보상은_자정에_한_번_준다() {
        LocalDate date = LocalDate.now();
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        activePet();
        energyBudget(date);
        Expense big = overspendWithPendingQuestion(date);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        rememberSavedJudgment(date);
        service.judgeDate(1L, Role.USER, date);

        // 사유 답변 — AiInquiryService 가 하는 일: 지출 신호를 낮추고 질문에 인정 기록을 남긴다
        AiInquiry answered = AiInquiry.pending(50L, 1L, "회식", 100_000);
        answered.recordAnswer("팀 회식", ReasonCategory.SOCIAL, true);
        big.updateSignalFinal(Signal.GRAY);
        when(inquiries.findByExpenseIdIn(List.of(50L))).thenReturn(List.of(answered));

        DailyJudgmentResponse finalized = service.finalizeDate(1L, Role.USER, date);

        assertEquals(JudgmentStatus.FINALIZED, finalized.status());
        assertEquals(Signal.RED, finalized.initialSignal(), "1차 결과는 그대로 남아 달라진 점을 보여 준다");
        assertEquals(Signal.GRAY, finalized.groupJudgments().get(StatType.ENERGY).signal());
        assertEquals(Signal.GRAY, finalized.signal());

        assertEquals(0, user.getGameMoney(), "최종 판정이 나도 보상은 자정에");
        DailyJudgmentResponse again = service.finalizeDate(1L, Role.USER, date);
        assertTrue(again.alreadyJudged());

        assertTrue(service.payReward(SAVED_ID));
        assertEquals(finalized.coinReward(), user.getGameMoney());
        assertFalse(service.payReward(SAVED_ID));
        assertEquals(finalized.coinReward(), user.getGameMoney(), "보상은 한 번");
    }

    @Test
    void 건너뛰면_1차_판정_그대로_확정한다() {
        LocalDate date = LocalDate.now();
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        activePet();
        energyBudget(date);
        overspendWithPendingQuestion(date);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        rememberSavedJudgment(date);
        DailyJudgmentResponse first = service.judgeDate(1L, Role.USER, date);

        DailyJudgmentResponse finalized = service.finalizeDate(1L, Role.USER, date);

        assertEquals(JudgmentStatus.FINALIZED, finalized.status());
        assertEquals(first.signal(), finalized.signal());
        assertEquals(first.groupJudgments(), finalized.groupJudgments());
        assertEquals(0, finalized.statRewards().get(StatType.ENERGY));
    }

    @Test
    void 평범한_초록_지출은_예산을_넘긴_그룹을_풀지_않는다() {
        // 예전엔 같은 그룹에 처음부터 초록이던 지출이 하나만 있어도 빨강이 풀렸다(roadmap 7절 1번)
        LocalDate date = LocalDate.now();
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        activePet();
        energyBudget(date);
        Expense big = Expense.builder().id(60L).userId(1L).statType(StatType.ENERGY).amount(100_000)
                .signalInitial(Signal.RED).signalFinal(Signal.RED).spentAt(date.atStartOfDay()).build();
        Expense coffee = Expense.builder().id(61L).userId(1L).statType(StatType.ENERGY).amount(3_000)
                .signalInitial(Signal.GREEN).signalFinal(Signal.GREEN).spentAt(date.atStartOfDay()).build();
        when(expenses.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(1L, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(big, coffee));
        when(expenses.findByUserIdAndSpentAtBetween(any(), any(), any())).thenReturn(List.of(big, coffee));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        rememberSavedJudgment(date);

        DailyJudgmentResponse result = service.judgeDate(1L, Role.USER, date);

        assertEquals(Signal.RED, result.groupJudgments().get(StatType.ENERGY).signal());
        assertEquals(Signal.RED, result.signal());
    }

    @Test
    void 관리자가_확정된_날을_다시_판정해도_보상을_다시_주지_않는다() {
        LocalDate date = LocalDate.now().minusDays(3);
        User admin = User.builder().id(3L).role(Role.ADMIN).gameMoney(0).build();
        Pet pet = Pet.builder().id(9L).userId(3L).build();
        when(pets.findByUserIdAndReleasedAtIsNull(3L)).thenReturn(List.of(pet));
        DailyJudgment existing = DailyJudgment.builder().userId(3L).judgmentDate(date)
                .signal(Signal.GREEN).expenseCount(0).coinReward(300).statRewardPerType(15)
                .rewardDetails("ENERGY:15").judgedAt(LocalDateTime.now()).rewardedAt(LocalDateTime.now()).build();
        when(users.findByIdForUpdate(3L)).thenReturn(Optional.of(admin));
        when(judgments.findByUserIdAndJudgmentDate(3L, date)).thenReturn(Optional.of(existing));

        DailyJudgmentResponse result = service.judgeDate(3L, Role.ADMIN, date);

        assertEquals(0, admin.getGameMoney());
        // 보상 칸은 실제로 지급한 값 그대로 — 새 계산으로 덮으면 받지 않은 보상이 받은 것처럼 보인다
        assertEquals(300, existing.getCoinReward());
        assertEquals(300, result.coinReward());
        assertEquals(15, result.statRewards().get(StatType.ENERGY));
        assertEquals(0, pet.statTotal());
        verifyNoInteractions(growthLogs);
    }

    @Test
    void 일반_사용자는_지난_날짜를_확정할_수_없다() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.finalizeDate(1L, Role.USER, LocalDate.now().minusDays(1)));
        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(judgments, users, pets, growthLogs);
    }

    @Test
    void 자정이_지난_미확정_판정은_1차_판정_내용으로_확정하고_그_보상을_지급한다() {
        // D7 — 예외 지출 사유는 반영하지 않고, 1차 판정 때 저장한 결과·보상을 그대로 지급한다
        LocalDate yesterday = LocalDate.now().minusDays(1);
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        Pet pet = activePet();
        DailyJudgment pending = DailyJudgment.builder().id(70L).userId(1L).judgmentDate(yesterday)
                .signal(Signal.RED).initialSignal(Signal.RED).status(JudgmentStatus.PENDING)
                .expenseCount(2).coinReward(300).statRewardPerType(15).rewardDetails("CHARM:15").savedAmount(30_000)
                .judgedAt(LocalDateTime.now().minusDays(1)).build();
        when(judgments.findById(70L)).thenReturn(Optional.of(pending));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(judgments.findByUserIdAndJudgmentDate(1L, yesterday)).thenReturn(Optional.of(pending));

        assertTrue(service.finalizeExpiredAsInitial(70L));

        assertEquals(JudgmentStatus.FINALIZED, pending.getStatus());
        assertEquals(Signal.RED, pending.getSignal(), "1차 판정 그대로");
        assertFalse(service.finalizeExpiredAsInitial(70L), "이미 확정됐으면 아무것도 하지 않는다");

        assertTrue(service.payReward(70L));
        assertEquals(300, user.getGameMoney());
        assertEquals(15, pet.getStatCharm());
        verifyNoInteractions(expenses, inquiries); // 다시 계산하지 않는다
    }

    @Test
    void 판정_받기를_하지_않은_날은_자정에_신호등_판정을_대신_내린다() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        User user = User.builder().id(1L).role(Role.USER).gameMoney(0).build();
        energyBudget(yesterday);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(judgments.findByUserIdAndJudgmentDate(1L, yesterday)).thenReturn(Optional.empty());
        ArgumentCaptor<DailyJudgment> saved = ArgumentCaptor.forClass(DailyJudgment.class);
        when(judgments.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.judgeUnjudgedDay(1L, yesterday));

        assertEquals(JudgmentStatus.FINALIZED, saved.getValue().getStatus());
        assertFalse(saved.getValue().isRewarded(), "지급은 이어서 payReward 가 한다");
        assertEquals(0, user.getGameMoney());
    }

    @Test
    void 관리자가_지난_날짜를_새로_판정하면_그_자정이_지났으므로_바로_지급한다() {
        LocalDate date = LocalDate.now().minusDays(2);
        User admin = User.builder().id(1L).role(Role.ADMIN).gameMoney(0).build();
        activePet();
        energyBudget(date);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));
        rememberSavedJudgment(date);

        DailyJudgmentResponse result = service.judgeDate(1L, Role.ADMIN, date);

        assertTrue(result.rewarded());
        assertEquals(result.coinReward(), admin.getGameMoney());
    }
}
