package com.vori.backend.user;

import com.vori.backend.budget.SpendingPlanService;
import com.vori.backend.common.StatType;
import com.vori.backend.onboarding.BaselineSeeder;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetSpeciesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 체험 계정 — 설문 답(식비·카페 중심)에 맞춰 식비 예산을 더 받는지. */
class GuestUserCreationTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserService service = new UserService(users, mock(PasswordEncoder.class), mock(JdbcTemplate.class),
            mock(PetRepository.class), mock(PetSpeciesRepository.class), mock(ApplicationEventPublisher.class),
            mock(BaselineSeeder.class), mock(SpendingPlanService.class));

    @Test
    void 체험_계정은_식비_예산_비중을_높여_만든다() {
        // #142 뒤 예산은 네 그룹 균등이라, 비중을 안 주면 체험자가 한 끼만 적어도 식비가 「초과」가 된다
        when(users.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        User guest = service.createGuest("guest-test@guest.vori.local", "체험", "체험");
        assertEquals(EnumSet.of(StatType.ENERGY), guest.budgetFocusStatTypes());
    }
}
