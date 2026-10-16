package com.vori.backend.seeder;

import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 관리자 계정 시더.
 *
 * <p>비밀번호는 코드에 두지 않고 {@code vori.admin.password}(.env 의 ADMIN_PASSWORD)로 받는다.
 * 예전에는 「1234」를 코드에 박아 어느 서버에서나 만들었는데, 배포 서버에서 이 이메일·비밀번호로
 * 누구나 관리자(게스트 정보 조회·코인 지급·제재)가 될 수 있었다.
 * <ul>
 *   <li>관리자가 이미 있으면 건드리지 않는다 — 로컬 DB 의 기존 관리자(1234)는 그대로 쓸 수 있다.</li>
 *   <li>없고 ADMIN_PASSWORD 도 없으면 만들지 않는다 — 새 DB(배포 서버 포함)는 값을 넣어야만 생긴다.</li>
 * </ul>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private static final String ADMIN_EMAIL = "admin@vori.com";
    private static final String ADMIN_NICKNAME = "VORI Admin";
    /** 이보다 짧으면 경고만 남긴다 — 로컬은 시연 스크립트와 맞춘 1234 를 쓰지만 배포 서버에서는 길게. */
    private static final int RECOMMENDED_MIN_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    // 필드 주입 — @RequiredArgsConstructor 는 final 필드만 생성자로 받는다
    @Value("${vori.admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        Optional<User> existing = userRepository.findByEmail(ADMIN_EMAIL);
        if (existing.isPresent()) {
            initializeStatStatsIfMissing(existing.get().getId());
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("관리자 계정({})을 만들지 않았어요 — ADMIN_PASSWORD 가 없어요. 필요하면 .env 에 넣고 다시 켜 주세요.", ADMIN_EMAIL);
            return;
        }
        if (adminPassword.length() < RECOMMENDED_MIN_LENGTH) {
            log.warn("관리자 비밀번호가 {}자보다 짧아요. 로컬이 아니면(배포 서버) 더 길게 바꿔 주세요.", RECOMMENDED_MIN_LENGTH);
        }
        LocalDateTime now = LocalDateTime.now();
        User admin = userRepository.save(User.builder()
            .email(ADMIN_EMAIL)
            .passwordHash(passwordEncoder.encode(adminPassword))
            .nickname(ADMIN_NICKNAME)
            .role(Role.ADMIN)
            .termsAgreedAt(now)
            .privacyAgreedAt(now)
            .createdAt(now)
            .build());
        log.info("관리자 계정({})을 만들었어요.", ADMIN_EMAIL);
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
