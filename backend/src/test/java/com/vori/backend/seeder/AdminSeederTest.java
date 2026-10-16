package com.vori.backend.seeder;

import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 관리자 시더 — 코드에 박힌 기본 비밀번호로 관리자가 생기지 않는지. */
class AdminSeederTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private AdminSeeder seeder(String password) {
        AdminSeeder seeder = new AdminSeeder(users, encoder, jdbc);
        ReflectionTestUtils.setField(seeder, "adminPassword", password);
        return seeder;
    }

    @Test
    @DisplayName("관리자가 이미 있으면 ADMIN_PASSWORD 와 상관없이 새로 만들거나 비밀번호를 바꾸지 않는다")
    void keepsExistingAdmin() {
        when(users.findByEmail("admin@vori.com")).thenReturn(Optional.of(
                User.builder().id(1L).email("admin@vori.com").role(Role.ADMIN).build()));

        seeder("another-long-password").run();

        verify(users, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    @DisplayName("관리자가 없고 ADMIN_PASSWORD 도 없으면 만들지 않는다 — 배포 서버에 기본 비밀번호 관리자가 생기지 않게")
    void skipsWithoutPassword() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        seeder("").run();
        seeder("   ").run();
        seeder(null).run();

        verify(users, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    @DisplayName("관리자가 없고 ADMIN_PASSWORD 가 있으면 그 비밀번호로 관리자를 만든다")
    void createsWithConfiguredPassword() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(encoder.encode("configured-password")).thenReturn("hashed");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        seeder("configured-password").run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@vori.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
    }
}
