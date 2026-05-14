package com.vori.backend.seeder;

import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
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

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) return;

        LocalDateTime now = LocalDateTime.now();
        User admin = User.builder()
            .email(ADMIN_EMAIL)
            .passwordHash(passwordEncoder.encode(ADMIN_PLAIN_PASSWORD))
            .nickname(ADMIN_NICKNAME)
            .role(Role.ADMIN)
            .termsAgreedAt(now)
            .privacyAgreedAt(now)
            .createdAt(now)
            .build();
        userRepository.save(admin);
    }
}
