package com.vori.backend.user;

import com.vori.backend.auth.dto.SignupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    @Transactional
    public User signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
            .email(req.email())
            .passwordHash(passwordEncoder.encode(req.password()))
            .nickname(req.nickname())
            .name(req.name())
            .role(Role.USER)
            .termsAgreedAt(now)
            .privacyAgreedAt(now)
            .marketingAgreedAt(Boolean.TRUE.equals(req.marketingAgreed()) ? now : null)
            .createdAt(now)
            .build();

        User saved = userRepository.save(user);
        initializeStatStats(saved.getId());
        return saved;
    }

    private void initializeStatStats(Long userId) {
        String sql = "INSERT INTO user_stat_stats " +
            "(user_id, stat_type, mean_ema, stddev_ema, sample_count) " +
            "VALUES (?, ?, 0, 0, 0)";
        for (String type : new String[]{"ENERGY", "CHARM", "IQ", "ENDURANCE"}) {
            jdbc.update(sql, userId, type);
        }
    }
}
