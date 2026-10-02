package com.vori.backend.pet;

import com.vori.backend.furniture.UserFurnitureRepository;
import com.vori.backend.pettitle.PetTitleService;
import com.vori.backend.theme.ThemeMasterRepository;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 분양 직전에 펫 칭호를 마지막으로 판정하는지 검증. 분양한 펫은 다시 판정하지 않으므로,
 * 여기서 빠지면 마지막 행동으로 채운 칭호가 그 펫 기록에 영영 남지 않는다.
 */
class PetReleaseTitleTest {

    private static final long USER_ID = 7L;

    private final PetRepository petRepository = mock(PetRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PetTitleService petTitleService = mock(PetTitleService.class);
    private final PetService service = new PetService(
            petRepository,
            mock(PetSpeciesRepository.class),
            userRepository,
            mock(UserFurnitureRepository.class),
            mock(ThemeMasterRepository.class),
            mock(PetGrowthLogRepository.class),
            mock(ApplicationEventPublisher.class),
            mock(com.vori.backend.notification.NotificationService.class),
            petTitleService);

    @Test
    @DisplayName("분양하기 전에, 아직 키우는 상태인 펫으로 칭호를 판정한다")
    void evaluatesTitlesBeforeRelease() {
        int stat = PetLevel.minExpFor(PetLevel.MAX_LEVEL) / PetLevel.EXP_PER_STAT;
        Pet pet = Pet.builder().id(3L).userId(USER_ID).speciesId(1L).statEnergy(stat)
                .createdAt(LocalDateTime.now()).build();
        when(petRepository.findById(3L)).thenReturn(Optional.of(pet));
        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(User.builder().id(USER_ID).gameMoney(0).build()));
        AtomicBoolean evaluatedWhileRaising = new AtomicBoolean(false);
        doAnswer(inv -> {
            evaluatedWhileRaising.set(!((Pet) inv.getArgument(0)).isReleased());
            return java.util.List.of();
        }).when(petTitleService).evaluate(any());

        service.release(USER_ID, 3L);

        assertThat(evaluatedWhileRaising).isTrue();
        assertThat(pet.isReleased()).isTrue();
    }
}
