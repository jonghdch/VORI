package com.vori.backend.user;

import com.vori.backend.user.dto.WithdrawalRequest;
import com.vori.backend.user.dto.WithdrawalResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 접수 규칙 — 관리자 차단, 가입 방식별 본인 확인, 중복 신청, 유예 기간.
 * 영구 삭제(purge)는 실제 FK 를 거쳐야 의미가 있어 AccountDeletionPurgeTest 에서 DB 로 본다.
 */
class AccountDeletionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AccountDeletionService service = new AccountDeletionService(
            userRepository, encoder, mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), 30);

    private User given(Role role, String rawPassword) {
        User u = User.builder().id(5L).email("u@vori.com").nickname("닉").role(role)
                .passwordHash(rawPassword == null ? null : encoder.encode(rawPassword))
                .build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));
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
    @DisplayName("로그인 복구 — 대기 계정만 취소되고 true")
    void cancelDeletionRestoresOnlyPending() {
        User u = given(Role.USER, "pw1234!!");
        assertThat(u.cancelDeletion()).isFalse();

        u.requestDeletion(NOW);
        assertThat(u.cancelDeletion()).isTrue();
        assertThat(u.isPendingDeletion()).isFalse();
    }
}
