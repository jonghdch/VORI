package com.vori.backend.judgment;

import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.Signal;
import com.vori.backend.common.StatType;
import com.vori.backend.furniture.UserFurniture;
import com.vori.backend.furniture.UserFurnitureRepository;
import com.vori.backend.pet.GrowthReason;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLog;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DailyJudgmentService {
    /**
     * 판정이 열리는 시각(0~23). 기본 20시 — 하루가 끝난 뒤 돌아보게 하려는 규칙이다.
     *
     * 프런트의 {@code REACT_APP_AI_ACTIVE_FROM_HOUR} 와 짝이다. 화면만 열어 두면 이 검사에서
     * 403 이 나므로, 낮에 하는 발표·시연에서는 **양쪽을 함께** 0 으로 둬야 한다.
     *
     * <pre>
     * .env                        vori.ai-judge.open-hour=0
     * frontend/.env.local         REACT_APP_AI_ACTIVE_FROM_HOUR=0
     * </pre>
     *
     * 운영 기본값은 20 이고, 시연이 끝나면 되돌린다.
     * 관리자 계정은 이 값과 상관없이 언제든 판정할 수 있다(프런트 {@code canUseAiJudge} 와 같다).
     */
    @Value("${vori.ai-judge.open-hour:20}")
    private int openHour;

    private final DailyJudgmentRepository dailyJudgmentRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final PetGrowthLogRepository petGrowthLogRepository;
    private final UserFurnitureRepository userFurnitureRepository;

    private record Reward(int coins, int statPerType) {}

    @Transactional(readOnly = true)
    public Optional<DailyJudgmentResponse> getToday(Long userId) {
        return getByDate(userId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Optional<DailyJudgmentResponse> getByDate(Long userId, LocalDate date) {
        return dailyJudgmentRepository.findByUserIdAndJudgmentDate(userId, date)
                .map(j -> DailyJudgmentResponse.from(j, true));
    }

    @Transactional(readOnly = true)
    public List<DailyJudgmentResponse> getByMonth(Long userId, YearMonth month) {
        return dailyJudgmentRepository.findByUserIdAndJudgmentDateBetweenOrderByJudgmentDate(
                        userId, month.atDay(1), month.plusMonths(1).atDay(1))
                .stream()
                .map(j -> DailyJudgmentResponse.from(j, true))
                .toList();
    }

    @Transactional
    public DailyJudgmentResponse judgeToday(Long userId, Role role) {
        return judgeDate(userId, role, LocalDate.now());
    }

    @Transactional
    public DailyJudgmentResponse judgeDate(Long userId, Role role, LocalDate requestedDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        if (role != Role.ADMIN && !requestedDate.equals(today)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 날짜의 판정은 관리자만 사용할 수 있어요.");
        }
        // 관리자는 시각 제한을 받지 않는다 — 화면(config.canUseAiJudge)과 같은 기준.
        if (role != Role.ADMIN && now.getHour() < openHour) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "소비 판정은 매일 " + openHour + "시부터 자정까지 가능해요.");
        }

        // 같은 사용자의 동시 클릭을 직렬화해 UNIQUE 충돌과 중복 보상을 함께 막는다.
        User locked = userRepository.findByIdForUpdate(userId).orElseThrow();
        Optional<DailyJudgment> existing = dailyJudgmentRepository
                .findByUserIdAndJudgmentDate(locked.getId(), requestedDate);
        if (existing.isPresent()) return DailyJudgmentResponse.from(existing.get(), true);

        DailyJudgmentResponse evaluated = evaluate(locked.getId(), requestedDate, now, false);
        Reward reward = rewardFor(evaluated.signal());
        grantReward(locked, reward, now);
        DailyJudgment saved = dailyJudgmentRepository.save(DailyJudgment.builder()
                .userId(locked.getId())
                .judgmentDate(requestedDate)
                .signal(evaluated.signal())
                .expenseCount(evaluated.expenseCount())
                .coinReward(reward.coins())
                .statRewardPerType(reward.statPerType())
                .judgedAt(now)
                .build());
        return DailyJudgmentResponse.from(saved, false);
    }

    private DailyJudgmentResponse evaluate(Long userId, LocalDate date, LocalDateTime judgedAt,
                                           boolean alreadyJudged) {
        List<Expense> expenses = expenseRepository.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(
                userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        Signal signal = expenses.stream()
                .map(e -> e.getSignalFinal() != null ? e.getSignalFinal() : e.getSignalInitial())
                .filter(s -> s != null)
                .reduce(Signal.GREEN, DailyJudgmentService::stronger);
        return new DailyJudgmentResponse(date, signal, expenses.size(), 0, 0, judgedAt, alreadyJudged);
    }

    private Reward rewardFor(Signal signal) {
        return switch (signal) {
            case GREEN -> new Reward(500, 11);
            case GRAY -> new Reward(250, 6);
            case RED -> new Reward(100, 2);
        };
    }

    private void grantReward(User user, Reward reward, LocalDateTime now) {
        user.addGameMoney(reward.coins());
        List<Pet> pets = petRepository.findByUserIdAndReleasedAtIsNull(user.getId());
        if (pets.isEmpty()) return;
        Pet pet = pets.get(0);
        Map<StatType, BigDecimal> furnitureBonusPct = new EnumMap<>(StatType.class);
        for (UserFurniture furniture : userFurnitureRepository.findByUserIdAndPositionXIsNotNullAndPositionYIsNotNull(user.getId())) {
            BigDecimal pct = furniture.getReleaseBonusPct() == null ? BigDecimal.ZERO : furniture.getReleaseBonusPct();
            if (pct.signum() > 0) furnitureBonusPct.merge(furniture.getStatTarget(), pct, BigDecimal::add);
        }
        for (StatType stat : StatType.values()) {
            pet.addStat(stat, reward.statPerType());
            petGrowthLogRepository.save(PetGrowthLog.builder()
                    .petId(pet.getId()).userId(user.getId()).statType(stat)
                    .delta(reward.statPerType()).savedAmount(0)
                    .reason(GrowthReason.DAILY_JUDGMENT).createdAt(now).build());
            BigDecimal pct = furnitureBonusPct.getOrDefault(stat, BigDecimal.ZERO);
            int bonus = pct.signum() == 0 ? 0 : BigDecimal.valueOf(reward.statPerType())
                    .multiply(pct).movePointLeft(2).setScale(0, java.math.RoundingMode.CEILING).intValue();
            if (bonus > 0) {
                pet.addStat(stat, bonus);
                petGrowthLogRepository.save(PetGrowthLog.builder()
                        .petId(pet.getId()).userId(user.getId()).statType(stat)
                        .delta(bonus).savedAmount(0)
                        .reason(GrowthReason.FURNITURE_BONUS).createdAt(now).build());
            }
        }
        pet.evaluateStage();
    }

    private static Signal stronger(Signal a, Signal b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(Signal signal) {
        return switch (signal) {
            case GREEN -> 1;
            case GRAY -> 2;
            case RED -> 3;
        };
    }
}
