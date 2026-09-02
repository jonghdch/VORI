package com.vori.backend.auth;

import com.vori.backend.sanction.SanctionRepository;
import com.vori.backend.sanction.SanctionType;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SanctionRepository sanctionRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("이메일을 찾을 수 없습니다: " + email));
        return new UserPrincipal(user, hasActiveSanction(user.getId()));
    }

    /**
     * 활성 제재 판정 — 로그인 차단 기준.
     * BAN: 해제 전까지 차단. SUSPENSION: expiresAt 전까지 차단. WARNING: 차단 X (기록만).
     */
    private boolean hasActiveSanction(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return sanctionRepository.findByUserIdAndLiftedAtIsNull(userId).stream()
            .anyMatch(s -> s.getType() == SanctionType.BAN
                || (s.getType() == SanctionType.SUSPENSION
                    && s.getExpiresAt() != null
                    && s.getExpiresAt().isAfter(now)));
    }
}
