package com.vori.backend.notification;

import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 알림 저장 규칙 — 같은 일로 두 번 알리지 않고, 펫 성장은 진화·졸업 지점을 넘을 때만 알린다. */
class NotificationServiceTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(repository);

    @Test
    @DisplayName("같은 dedupeKey 로는 한 번만 저장한다")
    void dedupe() {
        when(repository.existsByUserIdAndDedupeKey(1L, "k")).thenReturn(false, true);

        service.notify(1L, NotificationType.TITLE_ACQUIRED, "a", null, "/x", "k");
        service.notify(1L, NotificationType.TITLE_ACQUIRED, "a", null, "/x", "k");

        verify(repository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("레벨 4 → 6: 2차 진화 알림 하나")
    void evolvesOnce() {
        Pet pet = pet(PetLevel.minExpFor(6) / PetLevel.EXP_PER_STAT);
        service.petGrew(1L, pet, 4);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.PET_EVOLVED);
        assertThat(saved.getValue().getTitle()).contains("2차");
        assertThat(saved.getValue().getDedupeKey()).isEqualTo("pet:7:lv5");
    }

    @Test
    @DisplayName("레벨 14 → 30: 3차 진화와 졸업 알림 둘")
    void evolveAndGraduate() {
        Pet pet = pet(PetLevel.minExpFor(30) / PetLevel.EXP_PER_STAT);
        service.petGrew(1L, pet, 14);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(saved.capture());
        List<NotificationType> types = saved.getAllValues().stream().map(Notification::getType).toList();
        assertThat(types).containsExactly(NotificationType.PET_EVOLVED, NotificationType.PET_GRADUATE_READY);
    }

    @Test
    @DisplayName("진화 지점을 넘지 않으면 알리지 않는다")
    void noMilestone() {
        Pet pet = pet(PetLevel.minExpFor(8) / PetLevel.EXP_PER_STAT);
        service.petGrew(1L, pet, 6);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("다른 사람 알림은 읽음 처리하지 않는다")
    void markReadOwnOnly() {
        Notification n = Notification.builder().id(5L).userId(2L).type(NotificationType.TITLE_ACQUIRED)
                .title("t").createdAt(LocalDateTime.now()).build();
        when(repository.findById(5L)).thenReturn(java.util.Optional.of(n));

        service.markRead(1L, 5L);
        assertThat(n.getReadAt()).isNull();

        service.markRead(2L, 5L);
        assertThat(n.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("전체 삭제는 본인 알림만 지운다")
    void deleteAll() {
        service.deleteAll(1L);
        verify(repository).deleteByUserId(1L);
    }

    private static Pet pet(int statTotal) {
        return Pet.builder().id(7L).userId(1L).speciesId(1L).name("콩이")
                .statEnergy(statTotal).createdAt(LocalDateTime.now()).build();
    }
}
