package com.vori.backend.expense;

import com.vori.backend.auth.dto.SignupRequest;
import com.vori.backend.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지출 기간 조회의 끝 시각 — 실제 쿼리로 확인한다(목으로는 Between 의 끝 포함 여부를 볼 수 없다).
 * 시간을 받지 않은 지출은 그날 00:00:00 으로 저장되므로, 끝을 포함하면 다음 날 지출이 전날에 들어간다.
 * 테스트마다 롤백된다.
 */
@SpringBootTest
@Transactional
class ExpenseRangeQueryTest {

    @Autowired UserService userService;
    @Autowired ExpenseRepository expenses;
    @Autowired JdbcTemplate jdbc;

    private Expense save(Long userId, Long categoryId, LocalDateTime spentAt, String item) {
        return expenses.save(Expense.builder()
                .userId(userId).categoryId(categoryId).statType(com.vori.backend.common.StatType.ENERGY)
                .spentAt(spentAt).amount(1000).item(item).build());
    }

    @Test
    @DisplayName("기간 조회는 끝 시각을 포함하지 않는다 — 다음 날 0시 지출은 전날 조회에 들어가지 않는다")
    void endIsExclusive() {
        Long userId = userService.signup(new SignupRequest(
                "range-query@vori.test", "test1234!", "기간조회", "기간조회", true, true, false)).getId();
        Long categoryId = jdbc.queryForObject("SELECT MIN(id) FROM categories WHERE parent_id IS NOT NULL", Long.class);
        LocalDate day = LocalDate.of(2026, 10, 13);
        save(userId, categoryId, day.atStartOfDay(), "그날 지출");
        save(userId, categoryId, day.plusDays(1).atStartOfDay(), "다음 날 지출");

        List<Expense> found = expenses.findByUserIdAndSpentAtBetween(
                userId, day.atStartOfDay(), day.plusDays(1).atStartOfDay());

        assertThat(found).extracting(Expense::getItem).containsExactly("그날 지출");
    }
}
