package com.vori.backend.pet;

import com.vori.backend.common.StatType;
import com.vori.backend.furniture.UserFurnitureRepository;
import com.vori.backend.pet.dto.PetInteractionResponse;
import com.vori.backend.theme.ThemeMasterRepository;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 펫 상호작용(쓰다듬기 등)의 매력 보너스 검증. 스탯은 진화·배웅 선물로 이어지므로
 * 확률이 선언한 값(1%)과 어긋나면 바로 잡아야 한다.
 * Spring 컨텍스트·DB 없이 도는 순수 단위 테스트.
 */
class PetInteractionTest {

    private static final long USER_ID = 7L;

    private final PetRepository petRepository = mock(PetRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PetGrowthLogRepository growthLogRepository = mock(PetGrowthLogRepository.class);
    private final com.vori.backend.pettitle.PetTitleService petTitleService =
            mock(com.vori.backend.pettitle.PetTitleService.class);
    private final PetService service = new PetService(
            petRepository,
            mock(PetSpeciesRepository.class),
            userRepository,
            mock(UserFurnitureRepository.class),
            mock(ThemeMasterRepository.class),
            growthLogRepository,
            mock(ApplicationEventPublisher.class),
            mock(com.vori.backend.notification.NotificationService.class),
            petTitleService,
            new com.vori.backend.pet.PetStatRewardService(org.mockito.Mockito.mock(com.vori.backend.attendance.UserStatItemRepository.class)));

    private Pet activePet(int charm, int energy) {
        Pet pet = Pet.builder()
                .id(3L)
                .userId(USER_ID)
                .speciesId(1L)
                .statCharm(charm)
                .statEnergy(energy)
                .createdAt(LocalDateTime.now())
                .build();
        when(petRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).thenReturn(List.of(pet));
        return pet;
    }

    @Test
    @DisplayName("오늘 상호작용 보너스가 상한에 닿으면 당첨이어도 오르지 않는다 — 자동 호출로 매력을 모으지 못하게")
    void dailyCapBlocksFarming() {
        Pet pet = activePet(10, 0);
        when(growthLogRepository.countByPetIdAndReasonAndCreatedAtGreaterThanEqual(
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(GrowthReason.PET_INTERACTION),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn((long) PetService.INTERACT_CHARM_DAILY_CAP);

        PetInteractionResponse response = service.interact(USER_ID, 0);

        assertThat(response.charmUp()).isFalse();
        assertThat(pet.getStatCharm()).isEqualTo(10);
        verify(growthLogRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("당첨이면 상한을 세기 전에 사용자 행을 잠근다 — 동시 당첨이 상한을 넘지 못하게")
    void winningRollLocksBeforeCounting() {
        activePet(10, 0);

        service.interact(USER_ID, 0);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userRepository, growthLogRepository);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(growthLogRepository).countByPetIdAndReasonAndCreatedAtGreaterThanEqual(
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(GrowthReason.PET_INTERACTION),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("꽝이면 잠그지 않는다 — 매 호출마다 잠금 비용을 치르지 않게")
    void losingRollDoesNotLock() {
        activePet(10, 0);
        service.interact(USER_ID, 99);
        verify(userRepository, org.mockito.Mockito.never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("당첨이면 매력이 1 오르고 PET_INTERACTION 성장 이력이 남는다")
    void winningRollAddsOneCharm() {
        Pet pet = activePet(10, 0);

        PetInteractionResponse response = service.interact(USER_ID, 0);

        assertThat(response.charmUp()).isTrue();
        assertThat(response.pet().statCharm()).isEqualTo(11);
        assertThat(pet.getStatCharm()).isEqualTo(11);

        ArgumentCaptor<PetGrowthLog> saved = ArgumentCaptor.forClass(PetGrowthLog.class);
        verify(growthLogRepository).save(saved.capture());
        assertThat(saved.getValue().getPetId()).isEqualTo(3L);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getStatType()).isEqualTo(StatType.CHARM);
        assertThat(saved.getValue().getDelta()).isEqualTo(1);
        assertThat(saved.getValue().getSavedAmount()).isZero();
        assertThat(saved.getValue().getReason()).isEqualTo(GrowthReason.PET_INTERACTION);
    }

    @Test
    @DisplayName("꽝이면 스탯도 이력도 그대로다")
    void losingRollChangesNothing() {
        Pet pet = activePet(10, 0);

        for (int roll = 1; roll < 100; roll++) {
            PetInteractionResponse response = service.interact(USER_ID, roll);
            assertThat(response.charmUp()).as("roll=%d", roll).isFalse();
        }

        assertThat(pet.getStatCharm()).isEqualTo(10);
        verify(growthLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("당첨이든 꽝이든 상호작용 횟수는 1씩 쌓인다")
    void everyInteractionIsCounted() {
        Pet pet = activePet(10, 0);

        service.interact(USER_ID, 50);
        service.interact(USER_ID, 0);
        PetInteractionResponse third = service.interact(USER_ID, 99);

        assertThat(pet.getInteractionCount()).isEqualTo(3);
        assertThat(third.pet().interactionCount()).isEqualTo(3);
        assertThat(third.newTitles()).isEmpty();
    }

    @Test
    @DisplayName("매력 1로 진화 임계값을 넘으면 단계가 오른다")
    void winningRollCanEvolve() {
        Pet pet = activePet(0, Pet.minStatTotalFor(PetStage.JUVENILE) - 1);

        PetInteractionResponse response = service.interact(USER_ID, 0);

        assertThat(pet.getStage()).isEqualTo(PetStage.JUVENILE);
        assertThat(response.pet().stage()).isEqualTo("JUVENILE");
    }

    @Test
    @DisplayName("키우는 펫이 없으면 400")
    void noActivePet() {
        when(petRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.interact(USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("실제 추첨의 당첨 비율이 1% 다")
    void chanceIsOnePercent() {
        activePet(0, 0);
        int trials = 200_000;
        int wins = 0;
        for (int i = 0; i < trials; i++) {
            if (service.interact(USER_ID).charmUp()) wins++;
        }
        // 20만 회에서 표준편차는 약 0.022%p — 0.2%p 는 9σ 라 정상 변동으로는 넘지 않는다
        assertThat(wins * 100.0 / trials).isCloseTo(1.0, offset(0.2));
    }
}
