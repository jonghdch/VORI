package com.vori.backend.user;

import com.vori.backend.auth.dto.SignupRequest;
import com.vori.backend.notification.NotificationService;
import com.vori.backend.notification.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴 계정 영구 삭제를 실제 DB 의 FK 로 확인한다. 목으로는 "NO ACTION FK 에 막혀 users 삭제가 실패" 를
 * 잡을 수 없다. 테스트마다 롤백된다.
 *
 * <p>실행은 임시 스키마로 — 새 마이그레이션이 공유 vori DB 에 먼저 적용되지 않게
 * SPRING_DATASOURCE_URL 을 vori_wt_* 로 넘긴다.
 */
@SpringBootTest
@Transactional
class AccountDeletionPurgeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 4, 0);

    @Autowired UserService userService;
    @Autowired AccountDeletionService deletionService;
    @Autowired NotificationService notificationService;
    @Autowired JdbcTemplate jdbc;

    /** 가입(시작 펫·스탯 행 생성) + NO ACTION 테이블인 알림 1건. */
    private Long signupWithRows(String email) {
        Long id = userService.signup(new SignupRequest(
                email, "test1234!", "탈퇴테스트", "탈퇴테스트", true, true, false)).getId();
        notificationService.notify(id, NotificationType.MONTHLY_REPORT, "제목", "본문", "/report", "purge-test:" + id);
        return id;
    }

    private void requestedAt(Long id, LocalDateTime at) {
        jdbc.update("UPDATE users SET deletion_requested_at = ? WHERE id = ?", at, id);
    }

    private int count(String table, String column, Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
    }

    @Test
    @DisplayName("유예 기간이 지난 계정은 계정·펫·스탯·알림까지 모두 지워진다")
    void purgesExpiredAccountWithItsRows() {
        Long id = signupWithRows("purge-expired@vori.test");
        assertThat(count("pets", "user_id", id)).isEqualTo(1);
        assertThat(count("notifications", "user_id", id)).isEqualTo(1);
        requestedAt(id, NOW.minusDays(31));

        int purged = deletionService.purgeExpired(NOW);

        assertThat(purged).isEqualTo(1);
        assertThat(count("users", "id", id)).isZero();
        assertThat(count("pets", "user_id", id)).isZero();
        assertThat(count("user_stat_stats", "user_id", id)).isZero();
        assertThat(count("notifications", "user_id", id)).isZero();
    }

    @Test
    @DisplayName("유예 기간 안이거나 탈퇴하지 않은 계정은 지우지 않는다")
    void keepsAccountsInGraceOrActive() {
        Long inGrace = signupWithRows("purge-grace@vori.test");
        Long active = signupWithRows("purge-active@vori.test");
        requestedAt(inGrace, NOW.minusDays(10));

        deletionService.purgeExpired(NOW);

        assertThat(count("users", "id", inGrace)).isEqualTo(1);
        assertThat(count("users", "id", active)).isEqualTo(1);
    }

    @Test
    @DisplayName("스케줄러가 대상을 고른 뒤 로그인으로 복구됐으면 지우지 않는다")
    void restoredBeforePurgeIsKept() {
        Long id = signupWithRows("purge-restored@vori.test");
        // 대상 선정 이후 복구된 상황 — purge 가 잠근 뒤 다시 확인한다
        requestedAt(id, null);

        assertThat(deletionService.purge(id)).isFalse();
        assertThat(count("users", "id", id)).isEqualTo(1);
    }
}
