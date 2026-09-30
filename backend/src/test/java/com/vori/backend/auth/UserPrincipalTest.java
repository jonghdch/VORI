package com.vori.backend.auth;

import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class UserPrincipalTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private User user(String nickname) {
        return User.builder()
                .id(7L).email("a@vori.com").nickname(nickname)
                .passwordHash(encoder.encode("pw123!!!")).role(Role.USER)
                .build();
    }

    @Test
    void holdsIdentityOnlyNotEntitySnapshot() {
        User entity = user("처음닉");
        UserPrincipal principal = new UserPrincipal(entity, false);

        entity.updateProfile("바뀐닉", null, null, null, null);

        assertEquals(7L, principal.getId());
        assertEquals("a@vori.com", principal.getUsername());
        assertEquals(Role.USER, principal.getRole());
        assertEquals("ROLE_USER", principal.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void passwordHashIsErasedAfterLogin() {
        UserPrincipal loaded = new UserPrincipal(user("닉"), false);
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(email -> loaded);
        provider.setPasswordEncoder(encoder);

        Authentication auth = new ProviderManager(provider)
                .authenticate(new UsernamePasswordAuthenticationToken("a@vori.com", "pw123!!!"));

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertEquals(7L, principal.getId());
        assertNull(principal.getPassword(), "세션에 저장될 principal 에 비밀번호 해시가 남으면 안 된다");
    }
}
