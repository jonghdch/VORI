package com.vori.backend.expense;

import com.vori.backend.category.Category;
import com.vori.backend.category.CategoryRepository;
import com.vori.backend.expense.dto.ExpenseCreateRequest;
import com.vori.backend.expense.dto.ExpenseResponse;
import com.vori.backend.expense.dto.ExpenseUpdateRequest;
import com.vori.backend.goal.Goal;
import com.vori.backend.goal.GoalRepository;
import com.vori.backend.goal.GoalStatus;
import com.vori.backend.inquiry.AiInquiry;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.stats.UserStatStats;
import com.vori.backend.title.TitleCheckEvent;
import com.vori.backend.stats.UserStatStatsRepository;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final UserStatStatsRepository userStatStatsRepository;
    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ExpenseCalculationService calculationService;
    private final AiInquiryRepository aiInquiryRepository;

    /**
     * 판정에 필요한 최소 표본 수. 이보다 적으면 z 를 계산하지 않고 GREEN.
     * 온보딩 씨딩(BaselineSeeder)이 초기값을 넣을 때 표본 수를 이 값으로 두어 첫 지출부터 판정이 돌게 한다.
     */
    public static final int N_MIN = ExpenseCalculationService.N_MIN;
    /** 하루 첫 지출 기록에 주는 코인. */
    private static final int RECORD_REWARD_COINS = 100;

    /** 가계부 작성 화면 mount 시 그 날짜 기존 expense 들 불러오기. */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> listByDate(Long userId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<Expense> expenses = expenseRepository.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(userId, start, end);
        if (expenses.isEmpty()) return List.of();
        java.util.Map<Long, com.vori.backend.inquiry.ReasonCategory> reasons = new java.util.HashMap<>();
        aiInquiryRepository.findByExpenseIdIn(expenses.stream().map(Expense::getId).toList())
                .forEach(i -> reasons.put(i.getExpenseId(), i.getReasonCategory()));
        return expenses.stream().map(e -> ExpenseResponse.from(e, reasons.get(e.getId()))).toList();
    }

    @Transactional
    public ExpenseResponse createExpense(Long userId, ExpenseCreateRequest req) {
        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다: " + req.categoryId()));

        Expense expense = expenseRepository.save(Expense.builder()
                .userId(userId)
                .spentAt(req.spentAt())
                .timeProvided(req.timeProvided())
                .amount(req.amount())
                .item(req.item())
                .categoryId(req.categoryId())
                .statType(category.getStatType())
                .paymentMethod(req.paymentMethod())
                .memo(req.memo())
                .isRecurring(req.isRecurring())
                .build());

        // 기록 습관 보상 — 하루 첫 기록에만 준다. 지급일을 사용자에 남겨 삭제 후 재등록으로 다시 받지 못한다.
        // 같은 날 동시 등록이 둘 다 받지 않게 사용자 행을 잠근다.
        userRepository.findByIdForUpdate(userId).orElseThrow()
                .grantDailyRecordReward(java.time.LocalDate.now(), RECORD_REWARD_COINS);

        UserStatStats stats = userStatStatsRepository
                .findByUserIdAndStatType(userId, category.getStatType())
                .orElseThrow(() -> new IllegalStateException("user_stat_stats 초기화가 누락되었습니다."));

        ExpenseCalculationService.Result calculation = calculationService.calculate(
                stats, req.amount(), Boolean.TRUE.equals(req.isRecurring()));
        BigDecimal zScore = calculation.zScore();
        Signal signal = calculation.signal();
        int savedAmount = calculation.savedAmount();
        // 코인·스탯은 지출 금액이 아니라 하루 최종 판정에서만 지급한다.
        int statDelta = 0;

        expense.updateCalculations(zScore, signal, savedAmount, statDelta);

        calculationService.updateEma(stats, req.amount());

        if (savedAmount > 0) {
            User user = userRepository.findById(userId).orElseThrow();
            user.addTotalSaved(savedAmount);
            // 목표에 누적되는 건 지출액이 아니라 절약액이다 (db-spec.md: "saved_amount 양수 값 누적").
            // 지출액을 넣으면 5만원 목표가 3만원짜리 지출 한 번에 60% 로 찍힌다.
            updateActiveGoals(userId, req.categoryId(), savedAmount, req.spentAt());
        }

        // AI 질문 트리거 — RED 일 때만 묻는다.
        //
        // docs/domain.md 는 {RED, GRAY} 를 트리거로 적어 두었지만, 같은 문서의 용어집이
        // "이례(Anomaly) = z-score 가 임계치를 **초과**한 지출" 이라고 정의한다. 신호등
        // 임계값을 스펙값(z_green=-0.5)으로 되돌리자 GRAY 가 평균 이하 구간까지 품게 되어,
        // 평소보다 적게 쓴 지출에도 "평균보다 높습니다" 라고 묻는 상황이 생겼다.
        // (종전 z_green=1.00 에서는 GRAY 가 z>1.0 에서만 떠서 그 전제가 우연히 참이었다.)
        //
        // RED 로 좁히면 용어집 정의와 맞고, 질문 빈도도 69% → 7% 수준으로 내려간다.
        // 두 건 중 한 번씩 이유를 캐묻는 앱은 쓰이지 않는다.
        //
        // 반복 결제는 사용자의 의식적 결정이 아니므로 여전히 스킵한다.
        //
        // 질문 행은 여기서(같은 트랜잭션) 템플릿 문구로 먼저 만든다. AI 문구는 커밋 뒤
        // AiInquiryService.handleAnomalyEvent 가 비동기로 덮어쓴다. Gemini 가 실패해도
        // 질문이 남고, 저장 직후 열리는 분석 화면이 빈 목록을 보지 않는다.
        boolean skipAiQuestion = Boolean.TRUE.equals(req.isRecurring());
        if (signal == Signal.RED && !skipAiQuestion) {
            AiInquiry pending = aiInquiryRepository.save(
                    AiInquiry.pending(expense.getId(), userId, req.item(), req.amount()));
            eventPublisher.publishEvent(new ExpenseAnomalyEvent(
                    pending.getId(), expense.getId(), userId, req.item(), req.amount(),
                    category.getStatType(), stats.getMeanEma(), signal
            ));
        }

        // 지출 건수·누적 절약액·목표 달성이 모두 바뀌었으므로 칭호 조건을 다시 본다.
        // 커밋 이후에 평가되므로 방금 저장한 지출까지 반영된다.
        eventPublisher.publishEvent(new TitleCheckEvent(userId, "EXPENSE_CREATED"));

        return ExpenseResponse.from(expense);
    }

    /**
     * 본인 지출의 내역명·금액 수정 후 현재 통계 기준으로 판정과 절약액을 다시 계산한다.
     *
     * 파생 상태 처리는 삭제(LedgerService.deleteExpense)와 같은 규칙을 따른다.
     * - user.totalSaved — 이 지출이 더해 둔 절약액과 새 절약액의 차이만큼 맞춘다.
     * - EMA(user_stat_stats)·goal 누적 — 보존. EMA 는 중간 항을 바꿀 수 없고, goal 은 그 시점의 이력이다.
     */
    @Transactional
    public ExpenseResponse updateExpense(Long userId, Long expenseId, ExpenseUpdateRequest req) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("지출을 찾을 수 없습니다."));
        if (!expense.getUserId().equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "본인 지출만 수정할 수 있습니다.");
        }

        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다: " + req.categoryId()));
        UserStatStats stats = userStatStatsRepository
                .findByUserIdAndStatType(userId, category.getStatType())
                .orElseThrow(() -> new IllegalStateException("user_stat_stats 초기화가 누락되었습니다."));

        expense.updateDetails(req.item().trim(), req.amount(), req.categoryId(),
                category.getStatType(), req.paymentMethod());
        ExpenseCalculationService.Result calculation = calculationService.calculate(
                stats, req.amount(), Boolean.TRUE.equals(expense.getIsRecurring()));
        BigDecimal zScore = calculation.zScore();
        Signal signal = calculation.signal();
        int previousSaved = expense.getSavedAmount() == null ? 0 : expense.getSavedAmount();
        int savedAmount = calculation.savedAmount();
        int statDelta = 0;
        expense.updateCalculations(zScore, signal, savedAmount, statDelta);
        // 등록 때 양수 절약액만 누적했으므로 비교도 양수 부분끼리 한다.
        adjustTotalSaved(userId, Math.max(savedAmount, 0) - Math.max(previousSaved, 0));

        // 수정 전 금액으로 만든 질문은 더 이상 유효하지 않다. 새 판정이 RED면 템플릿 질문을
        // 바로 다시 만들고, AI 문구는 커밋 후 비동기로 덮어쓴다.
        // flush: expense_id 가 UNIQUE 라, Hibernate 가 INSERT 를 DELETE 보다 먼저 내보내면
        // 같은 expense_id 로 두 행이 겹쳐 제약 위반이 난다.
        aiInquiryRepository.findByExpenseId(expenseId).ifPresent(old -> {
            aiInquiryRepository.delete(old);
            aiInquiryRepository.flush();
        });
        if (signal == Signal.RED && !Boolean.TRUE.equals(expense.getIsRecurring())) {
            AiInquiry pending = aiInquiryRepository.save(AiInquiry.pending(
                    expense.getId(), userId, expense.getItem(), expense.getAmount()));
            eventPublisher.publishEvent(new ExpenseAnomalyEvent(
                    pending.getId(), expense.getId(), userId, expense.getItem(), expense.getAmount(),
                    expense.getStatType(), stats.getMeanEma(), signal));
        }
        return ExpenseResponse.from(expense);
    }

    /** 누적 절약액을 차이만큼 옮긴다. 줄일 때는 삭제와 같이 0 아래로 내려가지 않게 자른다. */
    private void adjustTotalSaved(Long userId, int delta) {
        if (delta == 0) return;
        User user = userRepository.findById(userId).orElseThrow();
        int current = user.getTotalSaved() == null ? 0 : user.getTotalSaved();
        user.addTotalSaved(Math.max(delta, -current));
        // 누적 절약액이 늘었으면 그 값을 조건으로 하는 칭호를 다시 본다.
        if (delta > 0) {
            eventPublisher.publishEvent(new TitleCheckEvent(userId, "EXPENSE_UPDATED"));
        }
    }

    /**
     * 이번 지출의 절약액을 해당 월의 ACTIVE 목표에 누적한다.
     * category_id 가 NULL 인 목표는 그 달 전체가 대상이므로 카테고리와 무관하게 쌓인다.
     * 목표치를 넘으면 Goal 이 스스로 DONE 으로 전이하고, 그 뒤로는 조회 대상에서 빠진다.
     */
    private void updateActiveGoals(Long userId, Long categoryId, int savedAmount, LocalDateTime spentAt) {
        String yearMonth = spentAt.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<Goal> goals = goalRepository.findByUserIdAndYearMonthAndStatus(userId, yearMonth, GoalStatus.ACTIVE);
        for (Goal goal : goals) {
            if (goal.getCategoryId() == null || goal.getCategoryId().equals(categoryId)) {
                goal.addCurrentAmount(savedAmount);
            }
        }
    }

}
