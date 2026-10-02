package com.vori.backend.pet;

import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.Signal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 펫 대화에 넘길 가계부 요약. 펫이 사용자의 소비를 알고 말할 수 있게 한다.
 *
 * <p><b>넘기는 것은 네 가지뿐이다</b> — 오늘 지출 합계·건수, 이번 달 카테고리별 합계,
 * 최근 지출 몇 건의 항목명·판정 신호, 이번 달 판정 분포.
 * 메모·사유 답변 원문·결제수단·가맹점 상세는 넘기지 않는다. 대화마다 외부 AI 로 나가는 데이터라
 * 대화에 필요한 만큼만 보낸다. 마이룸 대화방 (i) 안내에도 같은 범위를 적어 두었다.
 */
@Component
@RequiredArgsConstructor
public class PetLedgerSummary {

    /** 최근 지출로 넘길 건수 */
    static final int RECENT_COUNT = 5;
    /** 이번 달 카테고리로 넘길 개수 (금액 큰 순) */
    static final int CATEGORY_COUNT = 6;

    private final ExpenseRepository expenseRepository;

    /** 판정 화면(JudgmentResults)과 같은 이름 */
    private static final Map<Signal, String> SIGNAL_LABEL = Map.of(
            Signal.GREEN, "초록(합리적)",
            Signal.GRAY, "노랑(보통)",
            Signal.RED, "주황(주의)");

    public String describe(Long userId, LocalDate today) {
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        return format(
                expenseRepository.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(userId, todayStart, tomorrow),
                expenseRepository.categoryBreakdownInRange(userId, monthStart, tomorrow),
                expenseRepository.findTop5ByUserIdOrderBySpentAtDesc(userId),
                expenseRepository.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(userId, monthStart, tomorrow));
    }

    /**
     * 요약 문장. 조회와 분리해 테스트에서 넘기는 내용을 직접 확인한다.
     *
     * @param categories 행: [카테고리명, 합계] (금액 내림차순)
     */
    static String format(List<Expense> today, List<Object[]> categories, List<Expense> recent, List<Expense> month) {
        long todayTotal = today.stream().mapToLong(e -> e.getAmount() == null ? 0 : e.getAmount()).sum();

        String categoryText = categories.isEmpty() ? "없음" : categories.stream()
                .limit(CATEGORY_COUNT)
                .map(r -> "%s %,d원".formatted(r[0], ((Number) r[1]).longValue()))
                .collect(Collectors.joining(", "));

        String recentText = recent.isEmpty() ? "없음" : recent.stream()
                .limit(RECENT_COUNT)
                .map(e -> e.getItem() + " " + signalLabel(e))
                .collect(Collectors.joining(", "));

        Map<Signal, Long> counts = new EnumMap<>(Signal.class);
        long unjudged = 0;
        for (Expense e : month) {
            Signal s = signalOf(e);
            if (s == null) unjudged++;
            else counts.merge(s, 1L, Long::sum);
        }
        String distribution = month.isEmpty() ? "없음" : "초록 %d건, 노랑 %d건, 주황 %d건%s".formatted(
                counts.getOrDefault(Signal.GREEN, 0L),
                counts.getOrDefault(Signal.GRAY, 0L),
                counts.getOrDefault(Signal.RED, 0L),
                unjudged > 0 ? ", 판정 전 " + unjudged + "건" : "");

        return """
                - 오늘 지출: %s
                - 이번 달 카테고리별 합계: %s
                - 최근 지출(최신순): %s
                - 이번 달 판정 분포: %s""".formatted(
                today.isEmpty() ? "아직 없음" : "%d건, 합계 %,d원".formatted(today.size(), todayTotal),
                categoryText, recentText, distribution);
    }

    private static Signal signalOf(Expense e) {
        return e.getSignalFinal() != null ? e.getSignalFinal() : e.getSignalInitial();
    }

    private static String signalLabel(Expense e) {
        Signal s = signalOf(e);
        return s == null ? "(판정 전)" : "(" + SIGNAL_LABEL.get(s) + ")";
    }
}
