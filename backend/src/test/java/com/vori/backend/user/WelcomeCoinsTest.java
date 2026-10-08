package com.vori.backend.user;

import com.vori.backend.auth.dto.SignupRequest;
import com.vori.backend.budget.SpendingPlanService;
import com.vori.backend.furniture.FurnitureCatalog;
import com.vori.backend.onboarding.BaselineSeeder;
import com.vori.backend.pet.EggGrade;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetSpeciesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 가입 축하 코인 — 이메일·구글 두 가입 경로 모두 같은 코인으로 시작하고, 그 코인으로 상점을 써 볼 수 있는지. */
class WelcomeCoinsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserService service = new UserService(users, mock(PasswordEncoder.class), mock(JdbcTemplate.class),
            mock(PetRepository.class), mock(PetSpeciesRepository.class), mock(ApplicationEventPublisher.class),
            mock(BaselineSeeder.class), mock(SpendingPlanService.class));

    @Test
    void 이메일로_가입하면_가입_축하_코인으로_시작한다() {
        when(users.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        User user = service.signup(new SignupRequest("new@vori.com", "Vori!2026", "새싹", "홍길동", true, true, false));
        assertEquals(UserService.WELCOME_COINS, user.getGameMoney());
    }

    @Test
    void 구글로_처음_들어와도_같은_코인으로_시작한다() {
        when(users.findByGoogleSub(any())).thenReturn(Optional.empty());
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        User user = service.findOrCreateGoogleUser("sub-new", "g@gmail.com", "홍길동");
        assertEquals(UserService.WELCOME_COINS, user.getGameMoney());
    }

    @Test
    void 이미_있는_구글_계정은_코인을_다시_받지_않는다() {
        // 로그인할 때마다 찾기·만들기를 지나므로, 기존 계정에 코인을 얹으면 로그인 반복으로 코인이 쌓인다
        User linked = User.builder().id(2L).email("old@gmail.com").googleSub("sub-a").gameMoney(30).build();
        when(users.findByGoogleSub("sub-a")).thenReturn(Optional.of(linked));
        assertEquals(30, service.findOrCreateGoogleUser("sub-a", "old@gmail.com", "A").getGameMoney());
    }

    @Test
    void 가입_축하_코인으로_가장_싼_가구와_기본_알을_살_수_있다() {
        // 팀에 약속한 기준: "기본 알 하나와 가구 하나". 가격을 다시 올리면 이 테스트가 먼저 알려 준다.
        int cheapestFurniture = Arrays.stream(FurnitureCatalog.values()).mapToInt(FurnitureCatalog::price).min().orElseThrow();
        assertTrue(UserService.WELCOME_COINS >= cheapestFurniture + EggGrade.BASIC.price(),
                "가입 축하 코인 " + UserService.WELCOME_COINS + " < 가구 " + cheapestFurniture + " + 기본 알 " + EggGrade.BASIC.price());
    }
}
