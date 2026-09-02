package com.vori.backend.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.gemini.GeminiClient;
import com.vori.backend.income.Income;
import com.vori.backend.income.IncomeRepository;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetSpecies;
import com.vori.backend.pet.PetSpeciesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 일일 리포트 생성 배치.
 *
 * 설계상 지켜야 하는 두 가지:
 *
 * 1) 트랜잭션 밖에서 AI 를 호출한다. 메서드 전체에 @Transactional 을 걸면 사용자 N 명을 도는 동안
 *    커넥션을 계속 물고 있어 풀이 마른다 (Gemini read timeout 15s × N). 집계(읽기) → Gemini →
 *    짧은 쓰기 순으로 쪼갠다.
 *
 * 2) 실패를 사용자 단위로 가둔다. 한 명에서 예외가 나도 나머지는 계속 돌아야 하고,
 *    AI 코멘트만 실패한 경우엔 통계라도 저장한다 (코멘트 null).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportService {

    private final DailyReportRepository dailyReportRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final PetRepository petRepository;
    private final PetSpeciesRepository petSpeciesRepository;
    private final PetGrowthLogRepository petGrowthLogRepository;
    private final GeminiClient geminiClient;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /** 하루치 집계 결과 — AI 호출 전에 트랜잭션 밖으로 들고 나올 값들. */
    private record DailySummary(
            int incomeTotal, int expenseTotal, int savedAmount,
            int statDeltaTotal, String petName, String petSnapshot) {}

    public record BatchResult(LocalDate date, int targeted, int generated, int failed) {}

    /**
     * 해당 날짜 리포트를 대상자 전원에게 생성한다. 스케줄러와 수동 트리거가 함께 쓴다.
     * 이미 있는 리포트는 덮어쓰므로 여러 번 돌려도 중복이 생기지 않는다.
     */
    public BatchResult generateFor(LocalDate date) {
        List<Long> userIds = findTargetUsers(date);
        log.info("일일 리포트 배치 시작 — date={}, 대상 {}명", date, userIds.size());

        int generated = 0;
        int failed = 0;
        for (Long userId : userIds) {
            try {
                generateForUser(userId, date);
                generated++;
            } catch (Exception e) {
                // 한 명의 실패가 배치 전체를 멈추지 않게 여기서 가둔다
                failed++;
                log.error("일일 리포트 생성 실패 — userId={}, date={}", userId, date, e);
            }
        }

        log.info("일일 리포트 배치 종료 — date={}, 생성 {}건, 실패 {}건", date, generated, failed);
        return new BatchResult(date, userIds.size(), generated, failed);
    }

    /**
     * 리포트 대상 = 그 날 지출이나 수입을 남긴 사용자.
     * 두 쿼리를 각각 던지고 Java 에서 합친다 — 이 규모(수십~수백 명)에서 UNION 과 성능 차이가 없고,
     * JPQL 로 남겨두는 편이 읽고 고치기 쉽다. LinkedHashSet 이라 중복 제거 + 순서 안정.
     */
    public List<Long> findTargetUsers(LocalDate date) {
        Set<Long> ids = new LinkedHashSet<>(
                expenseRepository.findUserIdsWithExpenseInRange(
                        date.atStartOfDay(), date.plusDays(1).atStartOfDay()));
        ids.addAll(incomeRepository.findUserIdsWithIncomeOn(date));
        return List.copyOf(ids);
    }

    // ───── 조회 ─────

    /** 가장 최근 리포트. 없으면 null. */
    public DailyReportResponse getLatest(Long userId) {
        return dailyReportRepository.findFirstByUserIdOrderByReportDateDesc(userId)
                .map(DailyReportResponse::from)
                .orElse(null);
    }

    /** 특정 날짜 리포트. 없으면 null. */
    public DailyReportResponse getByDate(Long userId, LocalDate date) {
        return dailyReportRepository.findByUserIdAndReportDate(userId, date)
                .map(DailyReportResponse::from)
                .orElse(null);
    }

    public List<DailyReportResponse> listRange(Long userId, LocalDate from, LocalDate to) {
        return dailyReportRepository
                .findByUserIdAndReportDateBetweenOrderByReportDateDesc(userId, from, to)
                .stream()
                .map(DailyReportResponse::from)
                .toList();
    }

    /** 읽음 처리. 남의 리포트는 조용히 무시한다(존재 여부를 알려줄 이유가 없다). */
    @Transactional
    public void markRead(Long userId, Long reportId) {
        dailyReportRepository.findById(reportId)
                .filter(r -> r.getUserId().equals(userId))
                .ifPresent(r -> r.markRead(LocalDateTime.now()));
    }

    /**
     * 본인 리포트만 즉시 생성 — 시연·디버깅용.
     * 스케줄러를 기다리지 않고 그 자리에서 결과를 볼 수 있게 한다. 전체 배치가 아니라
     * 호출자 1명만 처리하므로 아무나 눌러도 남에게 영향이 없다.
     */
    public DailyReportResponse generateNow(Long userId, LocalDate date) {
        generateForUser(userId, date);
        return getByDate(userId, date);
    }

    // ───── 생성 ─────

    /** 사용자 1명의 리포트 생성. 집계 → (트랜잭션 밖) AI → 짧은 쓰기. */
    private void generateForUser(Long userId, LocalDate date) {
        DailySummary summary = aggregate(userId, date);

        // AI 실패로 리포트 자체가 사라지면 안 된다 — 코멘트만 비우고 통계는 남긴다
        String comment = null;
        try {
            comment = geminiClient.generateDailyComment(
                    summary.petName(), summary.expenseTotal(),
                    summary.incomeTotal(), summary.savedAmount(), summary.statDeltaTotal());
        } catch (Exception e) {
            log.warn("AI 코멘트 생성 실패 — 통계만 저장. userId={}, date={}", userId, date);
        }

        save(userId, date, summary, comment);
    }

    /**
     * 여기와 findTargetUsers 에 @Transactional 을 붙이지 않는 이유:
     * 둘 다 같은 클래스 안에서 호출되는데, 자기호출은 AOP 프록시를 거치지 않아 어노테이션이
     * 조용히 무시된다. 붙여두면 "읽기 트랜잭션으로 묶여 있다"는 착각만 남는다.
     *
     * 대신 Spring Data 리포지토리 메서드가 각자 읽기 트랜잭션을 연다. 리포트가 다루는 건
     * 이미 지나간 날짜의 데이터라 호출 사이에 값이 바뀌지 않으므로 이걸로 충분하다.
     */
    private DailySummary aggregate(Long userId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        List<Expense> expenses = expenseRepository.findByUserIdAndSpentAtBetween(userId, start, end);
        int expenseTotal = expenses.stream().mapToInt(Expense::getAmount).sum();
        int savedAmount = expenses.stream()
                .mapToInt(e -> e.getSavedAmount() == null ? 0 : e.getSavedAmount())
                .sum();

        int incomeTotal = incomeRepository
                .findByUserIdAndReceivedAtBetween(userId, date, date)
                .stream()
                .mapToInt(Income::getAmount)
                .sum();

        int statDeltaTotal = petGrowthLogRepository.sumDeltaInRange(userId, start, end);

        List<Pet> pets = petRepository.findByUserIdAndReleasedAtIsNull(userId);
        Pet pet = pets.isEmpty() ? null : pets.get(0);
        PetSpecies species = pet == null ? null
                : petSpeciesRepository.findById(pet.getSpeciesId()).orElse(null);

        return new DailySummary(
                incomeTotal, expenseTotal, savedAmount, statDeltaTotal,
                species == null ? null : species.getName(),
                writeSnapshot(pet, species));
    }

    /** 짧은 쓰기 트랜잭션. 같은 날 리포트가 있으면 덮어쓴다(멱등). */
    private void save(Long userId, LocalDate date, DailySummary s, String comment) {
        transactionTemplate.executeWithoutResult(tx -> {
            LocalDateTime now = LocalDateTime.now();
            dailyReportRepository.findByUserIdAndReportDate(userId, date)
                    .ifPresentOrElse(
                            existing -> existing.refresh(
                                    s.incomeTotal(), s.expenseTotal(), s.savedAmount(),
                                    s.statDeltaTotal(), s.petSnapshot(), comment, now),
                            () -> dailyReportRepository.save(DailyReport.builder()
                                    .userId(userId)
                                    .reportDate(date)
                                    .incomeTotal(s.incomeTotal())
                                    .expenseTotal(s.expenseTotal())
                                    .savedAmount(s.savedAmount())
                                    .statDeltaTotal(s.statDeltaTotal())
                                    .petSnapshot(s.petSnapshot())
                                    .aiComment(comment)
                                    .generatedAt(now)
                                    .build()));
        });
    }

    /**
     * 리포트 시점의 펫 상태를 JSON 으로 박제. 나중에 그 펫을 분양해도 리포트엔 그날의 모습이 남는다.
     * 직렬화가 실패해도 리포트 생성을 막지는 않는다.
     */
    private String writeSnapshot(Pet pet, PetSpecies species) {
        if (pet == null) return null;
        try {
            return objectMapper.writeValueAsString(new PetSnapshot(
                    pet.getId(),
                    species == null ? null : species.getName(),
                    species == null ? null : species.getAppearanceKey(),
                    pet.getStage().name(),
                    pet.getStatEnergy(), pet.getStatCharm(),
                    pet.getStatIq(), pet.getStatEndurance(),
                    pet.statTotal()));
        } catch (Exception e) {
            log.warn("펫 스냅샷 직렬화 실패 — petId={}", pet.getId(), e);
            return null;
        }
    }
}
