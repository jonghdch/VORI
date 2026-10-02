package com.vori.backend.pet;

import com.vori.backend.furniture.UserFurnitureRepository;
import com.vori.backend.pet.dto.PetNameRequest;
import com.vori.backend.pet.dto.PetResponse;
import com.vori.backend.theme.ThemeMasterRepository;
import com.vori.backend.user.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 펫 이름 짓기. 이름은 1~10자(앞뒤 공백 제외)이고 본인이 키우는 펫에만 붙일 수 있다.
 * Spring 컨텍스트·DB 없이 도는 순수 단위 테스트.
 */
class PetNameTest {

    private static final long USER_ID = 7L;
    private static final long PET_ID = 3L;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final PetRepository petRepository = mock(PetRepository.class);
    private final com.vori.backend.pettitle.PetTitleService petTitleService =
            mock(com.vori.backend.pettitle.PetTitleService.class);
    private final PetService service = new PetService(
            petRepository,
            mock(PetSpeciesRepository.class),
            mock(UserRepository.class),
            mock(UserFurnitureRepository.class),
            mock(ThemeMasterRepository.class),
            mock(PetGrowthLogRepository.class),
            mock(ApplicationEventPublisher.class),
            mock(com.vori.backend.notification.NotificationService.class),
            petTitleService,
            new com.vori.backend.pet.PetStatRewardService(org.mockito.Mockito.mock(com.vori.backend.attendance.UserStatItemRepository.class)));

    private boolean valid(String name) {
        return validator.validate(new PetNameRequest(name)).isEmpty();
    }

    private Pet pet(long ownerId, LocalDateTime releasedAt) {
        Pet pet = Pet.builder()
                .id(PET_ID)
                .userId(ownerId)
                .speciesId(1L)
                .createdAt(LocalDateTime.now())
                .releasedAt(releasedAt)
                .build();
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(pet));
        return pet;
    }

    @Test
    @DisplayName("이름은 1~10자")
    void nameLength() {
        assertThat(valid("콩")).isTrue();
        assertThat(valid("가".repeat(10))).isTrue();
        assertThat(valid("가".repeat(11))).isFalse();
        assertThat(valid("")).isFalse();
        assertThat(valid(null)).isFalse();
    }

    @Test
    @DisplayName("길이는 앞뒤 공백을 뺀 값으로 본다")
    void nameIsTrimmedBeforeValidation() {
        assertThat(valid("   ")).isFalse();
        assertThat(new PetNameRequest("  보리 ").name()).isEqualTo("보리");
        assertThat(valid(" " + "가".repeat(10) + " ")).isTrue();
    }

    @Test
    @DisplayName("본인이 키우는 펫에 이름을 붙이면 응답에 실린다")
    void ownerNamesActivePet() {
        Pet pet = pet(USER_ID, null);

        PetResponse response = service.rename(USER_ID, PET_ID, "보리");

        assertThat(pet.getName()).isEqualTo("보리");
        assertThat(response.name()).isEqualTo("보리");
    }

    @Test
    @DisplayName("이름을 짓지 않은 펫의 응답 name 은 null")
    void unnamedPetHasNullName() {
        Pet pet = pet(USER_ID, null);

        assertThat(PetResponse.of(pet, null).name()).isNull();
    }

    @Test
    @DisplayName("남의 펫에는 이름을 붙일 수 없다")
    void othersPetIsForbidden() {
        Pet pet = pet(USER_ID + 1, null);

        assertThatThrownBy(() -> service.rename(USER_ID, PET_ID, "보리"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(pet.getName()).isNull();
    }

    @Test
    @DisplayName("분양한 펫에는 이름을 붙일 수 없다")
    void releasedPetIsRejected() {
        Pet pet = pet(USER_ID, LocalDateTime.now());

        assertThatThrownBy(() -> service.rename(USER_ID, PET_ID, "보리"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(pet.getName()).isNull();
    }

    @Test
    @DisplayName("없는 펫이면 404")
    void missingPetIsNotFound() {
        when(petRepository.findById(PET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rename(USER_ID, PET_ID, "보리"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
