package com.vori.backend.seeder;

import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Order(1)
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private static final String ADMIN_EMAIL = "admin@vori.com";
    private static final String ADMIN_PLAIN_PASSWORD = "1234";
    private static final String ADMIN_NICKNAME = "VORI Admin";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            return userRepository.save(User.builder()
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PLAIN_PASSWORD))
                .nickname(ADMIN_NICKNAME)
                .role(Role.ADMIN)
                .termsAgreedAt(now)
                .privacyAgreedAt(now)
                .createdAt(now)
                .build());
        });

        initializeStatStatsIfMissing(admin.getId());
    }

    private void initializeStatStatsIfMissing(Long userId) {
        String sql = "INSERT IGNORE INTO user_stat_stats " +
            "(user_id, stat_type, mean_ema, stddev_ema, sample_count) " +
            "VALUES (?, ?, 0, 0, 0)";
        for (String type : new String[]{"ENERGY", "CHARM", "IQ", "ENDURANCE"}) {
            jdbc.update(sql, userId, type);
        }
    }
}
