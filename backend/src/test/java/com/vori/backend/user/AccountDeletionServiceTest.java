package com.vori.backend.user;

import com.vori.backend.notification.NotificationService;
import com.vori.backend.notification.NotificationType;
import com.vori.backend.user.dto.WithdrawalRequest;
import com.vori.backend.user.dto.WithdrawalResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 접수 규칙 — 관리자 차단, 가입 방식별 본인 확인, 중복 신청, 유예 기간.
 * 영구 삭제(purge)는 실제 FK 를 거쳐야 의미가 있어 AccountDeletionPurgeTest 에서 DB 로 본다.
 */
class AccountDeletionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AccountDeletionService service = new AccountDeletionService(
            userRepository, encoder, jdbc, mock(PlatformTransactionManager.class), notifications, 30);

    private User given(Role role, String rawPassword) {
        User u = User.builder().id(5L).email("u@vori.com").nickname("닉").role(role)
                .passwordHash(rawPassword == null ? null : encoder.encode(rawPassword))
                .build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(u));
        return u;
    }

    private static HttpStatus statusOf(Throwable e) {
        return HttpStatus.valueOf(((ResponseStatusException) e).getStatusCode().value());
    }

    @Test
    @DisplayName("비밀번호가 맞으면 탈퇴 대기가 되고, 30일 뒤 삭제 예정 시각을 돌려준다")
    void emailUserWithdrawsWithPassword() {
        User u = given(Role.USER, "pw1234!!");

        WithdrawalResponse res = service.request(5L, new WithdrawalRequest("pw1234!!", null), NOW);

        assertThat(u.isPendingDeletion()).isTrue();
        assertThat(u.getDeletionRequestedAt()).isEqualTo(NOW);
        assertThat(res.deleteAt()).isEqualTo(NOW.plusDays(30));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 400, 탈퇴되지 않는다")
    void wrongPasswordIsRejected() {
        User u = given(Role.USER, "pw1234!!");

        assertThatThrownBy(() -> service.request(5L, new WithdrawalRequest("wrong!!", null), NOW))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(u.isPendingDeletion()).isFalse();
    }

    @Test
    @DisplayName("구글 가입자는 확인 문구 '탈퇴' 로 본인 확인")
    void googleUserConfirmsWithText() {
        User u = given(Role.USER, null);

        assertThatThrownBy(() -> service.request(5L, new WithdrawalRequest(null, "탈퇴할래"), NOW))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(u.isPendingDeletion()).isFalse();

        service.request(5L, new WithdrawalRequest(null, " 탈퇴 "), NOW);
        assertThat(u.isPendingDeletion()).isTrue();
    }

    @Test
    @DisplayName("관리자 계정은 비밀번호가 맞아도 탈퇴할 수 없다 (403)")
    void adminCannotWithdraw() {
        User u = given(Role.ADMIN, "pw1234!!");

        assertThatThrownBy(() -> service.request(5L, new WithdrawalRequest("pw1234!!", null), NOW))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(u.isPendingDeletion()).isFalse();
    }

    @Test
    @DisplayName("이미 탈퇴 대기면 409 — 유예 기간이 다시 늘어나지 않게")
    void alreadyPendingIsConflict() {
        User u = given(Role.USER, "pw1234!!");
        u.requestDeletion(NOW.minusDays(3));

        assertThatThrownBy(() -> service.request(5L, new WithdrawalRequest("pw1234!!", null), NOW))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThat(u.getDeletionRequestedAt()).isEqualTo(NOW.minusDays(3));
    }

    @Test
    @DisplayName("로그인 복구 — 유예 기간 안의 대기 계정만 복구하고 true, 정상 계정은 false")
    void restoreOnLoginWithinGrace() {
        User u = given(Role.USER, "pw1234!!");
        assertThat(service.restoreOnLogin(5L, NOW)).isFalse();

        u.requestDeletion(NOW.minusDays(29));
        assertThat(service.restoreOnLogin(5L, NOW)).isTrue();
        assertThat(u.isPendingDeletion()).isFalse();
    }

    @Test
    @DisplayName("유예 기간이 지났으면 새벽 삭제 전이라도 복구하지 않고 401 (Codex 지적)")
    void expiredAccountIsNotRestored() {
        User u = given(Role.USER, "pw1234!!");
        u.requestDeletion(NOW.minusDays(30));

        assertThatThrownBy(() -> service.restoreOnLogin(5L, NOW))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(u.isPendingDeletion()).isTrue();
    }

    @Test
    @DisplayName("이미 지워진 계정의 로그인은 401 — purge 가 먼저 행을 지운 경우")
    void purgedAccountLoginIsUnauthorized() {
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreOnLogin(9L, NOW))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("영구 삭제가 실패하면 관리자에게 하루 한 번 알림을 보낸다")
    void purgeFailureAlertsAdmins() {
        when(userRepository.findIdsDeletionRequestedBefore(NOW.minusDays(30))).thenReturn(List.of(5L));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenThrow(new DataIntegrityViolationException("FK"));
        User admin = User.builder().id(1L).email("admin@vori.com").nickname("관리자").role(Role.ADMIN).build();
        when(userRepository.findAllByRole(Role.ADMIN)).thenReturn(List.of(admin));

        assertThat(service.purgeExpired(NOW)).isZero();

        verify(notifications).notify(eq(1L), eq(NotificationType.ADMIN_ALERT), contains("1건"), anyString(),
                isNull(), eq("account-purge-failed:2026-10-08"));
    }
}
