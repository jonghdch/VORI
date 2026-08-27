package com.vori.backend.expense;

import com.vori.backend.category.Category;
import com.vori.backend.category.CategoryRepository;
import com.vori.backend.expense.dto.ExpenseCreateRequest;
import com.vori.backend.expense.dto.ExpenseResponse;
import com.vori.backend.goal.Goal;
import com.vori.backend.goal.GoalRepository;
import com.vori.backend.goal.GoalStatus;
import com.vori.backend.pet.GrowthReason;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLog;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.stats.UserStatStats;
import com.vori.backend.stats.UserStatStatsRepository;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final PetRepository petRepository;
    private final PetGrowthLogRepository petGrowthLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SignalConfigService signalConfigService;

    private static final int N_MIN = 5;
    // Z_GREEN / Z_RED 임계값은 signal_config 테이블(관리자 조정) 에서 읽는다. SignalConfigService 참조.
    private static final BigDecimal STDDEV_MIN = new BigDecimal("0.01");
    private static final double EMA_ALPHA = 0.2;

    // 절약액 → 게임머니 전환 비율 (N 원 절약당 1 코인).
    // 기본 알이 2,500 코인이므로 25,000 원 절약 = 알 1 개. 게임 루프가 며칠 단위로 돌게 잡은 값 —
    // 밸런싱은 이 상수만 바꾸면 된다. 분양 보상(스탯총합×10)은 여기에 얹히는 보너스 성격.
    private static final int SAVED_PER_GAME_MONEY = 10;

    /** 가계부 작성 화면 mount 시 그 날짜 기존 expense 들 불러오기. */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> listByDate(Long userId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return expenseRepository
                .findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(userId, start, end)
                .stream()
                .map(ExpenseResponse::from)
                .toList();
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

        UserStatStats stats = userStatStatsRepository
                .findByUserIdAndStatType(userId, category.getStatType())
                .orElseThrow(() -> new IllegalStateException("user_stat_stats 초기화가 누락되었습니다."));

        BigDecimal zScore = null;
        Signal signal;

        if (stats.getSampleCount() < N_MIN || stats.getStddevEma().compareTo(STDDEV_MIN) < 0) {
            signal = Signal.GREEN;
        } else {
            zScore = BigDecimal.valueOf(req.amount())
                    .subtract(stats.getMeanEma())
                    .divide(stats.getStddevEma(), 3, RoundingMode.HALF_UP);
            double z = zScore.doubleValue();
            SignalConfig cfg = signalConfigService.getConfig();
            double zGreen = cfg.getZGreen().doubleValue();
            double zRed = cfg.getZRed().doubleValue();
            signal = z <= zGreen ? Signal.GREEN : (z <= zRed ? Signal.GRAY : Signal.RED);
        }

        // docs/domain.md §3 — 반복 결제(통신비·구독 등)는 사용자의 의식적 결정이 아님 → RED 자동 제외
        if (Boolean.TRUE.equals(req.isRecurring()) && signal == Signal.RED) {
            signal = Signal.GRAY;
        }

        int savedAmount = stats.getMeanEma().subtract(BigDecimal.valueOf(req.amount())).intValue();
        int statDelta = Math.max(savedAmount, 0) / 1000;

        expense.updateCalculations(zScore, signal, savedAmount, statDelta);

        updateEma(stats, req.amount());

        if (savedAmount > 0) {
            User user = userRepository.findById(userId).orElseThrow();
            user.addTotalSaved(savedAmount);
            // 절약분의 일부를 게임머니로 전환 — 알 구매(상점)의 유일한 수입원.
            user.addGameMoney(savedAmount / SAVED_PER_GAME_MONEY);
            updateActiveGoals(userId, req.categoryId(), req.amount(), req.spentAt());
        }

        if (statDelta > 0) {
            updateActivePet(userId, category.getStatType(), statDelta, expense.getId(), savedAmount);
        }

        // AI 질문 트리거 조건 (docs/domain.md §6):
        //   signal != GREEN AND is_recurring == FALSE
        // 반복 결제는 사용자의 의식적 결정이 아니므로 AI 질문 스킵 → signal_final = signal_initial (이미 set 됨)
        boolean skipAiQuestion = Boolean.TRUE.equals(req.isRecurring());
        if (signal != Signal.GREEN && !skipAiQuestion) {
            eventPublisher.publishEvent(new ExpenseAnomalyEvent(
                    expense.getId(), userId, req.item(), req.amount(),
                    category.getStatType(), stats.getMeanEma(), signal
            ));
        }

        return ExpenseResponse.from(expense);
    }

    private void updateEma(UserStatStats stats, int amount) {
        int newCount = stats.getSampleCount() + 1;

        if (stats.getSampleCount() == 0) {
            stats.updateEma(BigDecimal.valueOf(amount), BigDecimal.ZERO, newCount);
            return;
        }

        double oldMean = stats.getMeanEma().doubleValue();
        double oldVar = Math.pow(stats.getStddevEma().doubleValue(), 2);

        double newMean = EMA_ALPHA * amount + (1 - EMA_ALPHA) * oldMean;
        double deviation = amount - oldMean;
        double newVar = EMA_ALPHA * deviation * deviation + (1 - EMA_ALPHA) * oldVar;

        stats.updateEma(
                BigDecimal.valueOf(newMean).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(Math.sqrt(newVar)).setScale(2, RoundingMode.HALF_UP),
                newCount
        );
    }

    private void updateActiveGoals(Long userId, Long categoryId, int amount, LocalDateTime spentAt) {
        String yearMonth = spentAt.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<Goal> goals = goalRepository.findByUserIdAndYearMonthAndStatus(userId, yearMonth, GoalStatus.ACTIVE);
        for (Goal goal : goals) {
            if (goal.getCategoryId() == null || goal.getCategoryId().equals(categoryId)) {
                goal.addCurrentAmount(amount);
            }
        }
    }

    private void updateActivePet(Long userId, com.vori.backend.common.StatType statType,
                                 int statDelta, Long expenseId, int savedAmount) {
        List<Pet> pets = petRepository.findByUserIdAndReleasedAtIsNull(userId);
        if (pets.isEmpty()) return;

        Pet pet = pets.get(0);
        pet.addStat(statType, statDelta);
        pet.evaluateStage(); // 스탯 합이 임계값을 넘었으면 INFANT→JUVENILE→ADULT 로 승급

        petGrowthLogRepository.save(PetGrowthLog.builder()
                .petId(pet.getId())
                .userId(userId)
                .expenseId(expenseId)
                .statType(statType)
                .delta(statDelta)
                .savedAmount(savedAmount)
                .reason(GrowthReason.EXPENSE_SAVING)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
