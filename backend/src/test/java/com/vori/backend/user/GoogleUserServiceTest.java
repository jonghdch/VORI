package com.vori.backend.user;

import com.vori.backend.onboarding.BaselineSeeder;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetSpeciesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 구글 로그인 계정 찾기·만들기 — 이메일 선점 탈취를 막고, 만든 값이 가입 규칙을 지키는지. */
class GoogleUserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserService service = new UserService(users, mock(PasswordEncoder.class), mock(JdbcTemplate.class),
            mock(PetRepository.class), mock(PetSpeciesRepository.class), mock(ApplicationEventPublisher.class),
            mock(BaselineSeeder.class), mock(com.vori.backend.budget.SpendingPlanService.class));

    @Test
    void 같은_이메일의_기존_계정에는_자동으로_연결하지_않는다() {
        // 이메일 가입은 소유 확인이 없어, 남이 먼저 가입해 둔 계정에 진짜 주인의 구글이 붙으면 탈취가 된다
        User squatter = User.builder().id(1L).email("victim@gmail.com").passwordHash("attacker-hash").build();
        when(users.findByGoogleSub("sub-v")).thenReturn(Optional.empty());
        when(users.findByEmail("victim@gmail.com")).thenReturn(Optional.of(squatter));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.findOrCreateGoogleUser("sub-v", "victim@gmail.com", "Victim"));
        assertEquals(409, e.getStatusCode().value());
        assertNull(squatter.getGoogleSub());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void 구글_식별자가_같으면_그_계정으로_들어간다() {
        User linked = User.builder().id(2L).email("old@gmail.com").googleSub("sub-a").build();
        when(users.findByGoogleSub("sub-a")).thenReturn(Optional.of(linked));
        assertSame(linked, service.findOrCreateGoogleUser("sub-a", "new@gmail.com", "A"));
    }

    private User created(String displayName, String email) {
        when(users.findByGoogleSub(any())).thenReturn(Optional.empty());
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        return service.findOrCreateGoogleUser("sub-new", email, displayName);
    }

    @Test
    void 새_계정의_닉네임은_가입_규칙_2에서_12자를_지킨다() {
        String longName = created("Christopher Alexander Smith", "c@gmail.com").getNickname();
        assertTrue(longName.length() >= 2 && longName.length() <= 12, longName);
        String shortName = created(null, "a@x.com").getNickname();
        assertTrue(shortName.length() >= 2 && shortName.length() <= 12, shortName);
    }

    @Test
    void 이름이_2자_미만이면_비워_두어_프로필에서_채우게_한다() {
        assertNull(created("김", "k@gmail.com").getName());
        assertEquals("홍길동", created("홍길동", "h@gmail.com").getName());
    }
}
