package com.vori.backend.judgment;

import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.Signal;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DailyJudgmentService {
    private static final int OPEN_HOUR = 20;

    private final DailyJudgmentRepository dailyJudgmentRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<DailyJudgmentResponse> getToday(Long userId) {
        return dailyJudgmentRepository.findByUserIdAndJudgmentDate(userId, LocalDate.now())
                .map(j -> DailyJudgmentResponse.from(j, true));
    }

    @Transactional
    public DailyJudgmentResponse judgeToday(User principalUser) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        if (principalUser.getRole() == Role.ADMIN) {
            return evaluate(principalUser.getId(), today, now, false);
        }
        if (now.getHour() < OPEN_HOUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "소비 판정은 매일 20시부터 자정까지 가능해요.");
        }

        // 같은 사용자의 동시 클릭을 직렬화해 UNIQUE 충돌과 중복 판정을 함께 막는다.
        User locked = userRepository.findByIdForUpdate(principalUser.getId()).orElseThrow();
        Optional<DailyJudgment> existing = dailyJudgmentRepository
                .findByUserIdAndJudgmentDate(locked.getId(), today);
        if (existing.isPresent()) return DailyJudgmentResponse.from(existing.get(), true);

        DailyJudgmentResponse evaluated = evaluate(locked.getId(), today, now, false);
        DailyJudgment saved = dailyJudgmentRepository.save(DailyJudgment.builder()
                .userId(locked.getId())
                .judgmentDate(today)
                .signal(evaluated.signal())
                .expenseCount(evaluated.expenseCount())
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
        return new DailyJudgmentResponse(date, signal, expenses.size(), judgedAt, alreadyJudged);
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
