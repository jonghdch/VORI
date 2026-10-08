package com.vori.backend.admin;

import com.vori.backend.admin.dto.AdminCoinResponse;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminCoinServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminCoinService service = new AdminCoinService(userRepository);

    @Test
    @DisplayName("관리자 지급은 잔액을 늘리고 지급 결과를 돌려준다")
    void grantsCoinsAndReturnsUpdatedBalance() {
        User user = User.builder().id(42L).gameMoney(376).build();
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));

        AdminCoinResponse response = service.grant(7L, 42L, 25_000);

        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.gameMoney()).isEqualTo(25_376);
        assertThat(response.granted()).isEqualTo(25_000);
        verify(userRepository).findByIdForUpdate(42L);
    }

    @Test
    @DisplayName("허용 범위 양 끝(1·100,000)은 지급된다")
    void grantsBoundaryAmounts() {
        User user = User.builder().id(42L).gameMoney(0).build();
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));

        assertThat(service.grant(7L, 42L, 1).gameMoney()).isEqualTo(1);
        assertThat(service.grant(7L, 42L, 100_000).gameMoney()).isEqualTo(100_001);
    }

    @Test
    @DisplayName("0·음수·100,001 코인은 거절한다")
    void rejectsAmountsOutsideAllowedRange() {
        for (int amount : new int[]{0, -1, 100_001}) {
            assertThatThrownBy(() -> service.grant(7L, 42L, amount))
                    .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("없는 사용자에게 지급하면 404로 거절한다")
    void rejectsMissingUser() {
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(7L, 42L, 1))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
