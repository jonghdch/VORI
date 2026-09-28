package com.vori.backend.sanction;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 제재로 계정 사용이 막혔는지 판정한다. 로그인(CustomUserDetailsService)과
 * 요청마다의 확인(AccountStatusFilter)이 같은 기준을 쓰도록 한 곳에 둔다.
 *
 * BAN: 해제 전까지 차단. SUSPENSION: expiresAt 전까지 차단. WARNING: 차단 X (기록만).
 */
@Component
@RequiredArgsConstructor
public class SanctionPolicy {

    private final SanctionRepository sanctionRepository;

    public boolean isBlocked(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return sanctionRepository.findByUserIdAndLiftedAtIsNull(userId).stream()
            .anyMatch(s -> s.getType() == SanctionType.BAN
                || (s.getType() == SanctionType.SUSPENSION
                    && s.getExpiresAt() != null
                    && s.getExpiresAt().isAfter(now)));
    }
}
