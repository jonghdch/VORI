package com.vori.backend.pettitle;

import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.notification.NotificationService;
import com.vori.backend.notification.NotificationType;
import com.vori.backend.pet.GrowthReason;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetLevel;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetSpeciesRepository;
import com.vori.backend.pettitle.dto.PetTitleBoardResponse;
import com.vori.backend.pettitle.dto.PetTitleItem;
import com.vori.backend.title.TitleCheckEvent;
import com.vori.backend.title.dto.GrantedTitle;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 펫 칭호 판정 검증. 칭호는 "그 펫"이 해낸 것이라 펫이 바뀌면 0부터 다시 세고, 한 번 딴 칭호는
 * 분양한 뒤에도 그 펫 기록으로 남아야 한다.
 * Spring 컨텍스트·DB 없이 도는 순수 단위 테스트.
 */
class PetTitleServiceTest {

    private static final long USER_ID = 7L;

    private final PetTitleRepository titleRepository = mock(PetTitleRepository.class);
    private final PetTitleAwardRepository awardRepository = mock(PetTitleAwardRepository.class);
    private final PetRepository petRepository = mock(PetRepository.class);
    private final PetGrowthLogRepository growthLogRepository = mock(PetGrowthLogRepository.class);
    private final AiInquiryRepository aiInquiryRepository = mock(AiInquiryRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PetTitleService service = new PetTitleService(
            titleRepository, awardRepository, petRepository, mock(PetSpeciesRepository.class),
            growthLogRepository, aiInquiryRepository, mock(UserRepository.class),
            notificationService, eventPublisher);

    private final PetTitle evolve = title(10L, "PET_EVOLVE_2", "첫 진화", PetTitleMetricType.LEVEL, 5, false);
    private final PetTitle lovely = title(11L, "PET_LOVELY", "사랑둥이", PetTitleMetricType.INTERACTIONS, 100, false);
    private final PetTitle talkative = title(12L, "PET_TALKATIVE", "수다쟁이", PetTitleMetricType.AI_ANSWERS, 100, false);
    private final PetTitle lucky = title(13L, "PET_LUCKY_CHARM", "행운의 매력", PetTitleMetricType.CHARM_BONUS, 10, true);

    private static PetTitle title(Long id, String code, String name, PetTitleMetricType metric,
                                  long threshold, boolean hidden) {
        return new PetTitle(id, code, name, name + " 조건", metric, threshold, true, hidden, 0, null, null);
    }

    /** 레벨을 맞춘 펫. hatchedAt 은 수명 구간 시작점. */
    private static Pet pet(long id, int level, int interactions, LocalDateTime hatchedAt) {
        int stat = PetLevel.minExpFor(level) / PetLevel.EXP_PER_STAT;
        return Pet.builder().id(id).userId(USER_ID).speciesId(1L)
                .statEnergy(stat).interactionCount(interactions)
                .hatchedAt(hatchedAt).createdAt(hatchedAt).build();
    }

    private static PetTitleAward award(long id, long petId, PetTitle title) {
        return PetTitleAward.builder().id(id).petId(petId).title(title).acquiredAt(LocalDateTime.now()).build();
    }

    @BeforeEach
    void setUp() {
        when(titleRepository.findByEnabledTrueOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(evolve, lovely, talkative, lucky));
        when(awardRepository.findByPetIdIn(anyCollection())).thenReturn(List.of());
    }

    @Test
    @DisplayName("새 펫의 과제는 0부터 — AI 답변은 그 펫이 부화한 뒤의 것만 센다")
    void newPetStartsFromZero() {
        LocalDateTime hatched = LocalDateTime.now().minusMinutes(1);
        Pet fresh = pet(2L, 1, 0, hatched);
        when(petRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).thenReturn(List.of(fresh));
        when(aiInquiryRepository.countAnsweredForExpensesSince(USER_ID, hatched)).thenReturn(0L);
        when(growthLogRepository.countByPetIdAndReason(2L, GrowthReason.PET_INTERACTION)).thenReturn(0L);

        PetTitleBoardResponse board = service.board(USER_ID);

        assertThat(board.petId()).isEqualTo(2L);
        // 레벨은 새 펫도 1부터라 진화 과제는 1, 나머지 횟수 과제는 0
        assertThat(board.titles()).filteredOn(t -> !t.metricType().equals("LEVEL"))
                .extracting(PetTitleItem::current).containsOnly(0L);
        assertThat(board.titles()).extracting(PetTitleItem::acquired).containsOnly(false);
        // 이전 펫 시절의 답변을 세지 않도록, 구간 시작이 이 펫의 부화 시각이어야 한다
        verify(aiInquiryRepository, org.mockito.Mockito.atLeastOnce()).countAnsweredForExpensesSince(USER_ID, hatched);
        verify(awardRepository, never()).save(any());
    }

    @Test
    @DisplayName("진화 레벨에 닿으면 진화 칭호를 지급하고, 알리고, 업적을 다시 보게 한다")
    void grantsEvolutionTitleAtLevel() {
        Pet grown = pet(3L, PetLevel.JUVENILE_LEVEL, 0, LocalDateTime.now().minusDays(1));

        List<GrantedTitle> granted = service.evaluate(grown);

        assertThat(granted).containsExactly(new GrantedTitle("첫 진화", false));
        verify(awardRepository).save(any(PetTitleAward.class));
        verify(notificationService).notify(eq(USER_ID), eq(NotificationType.PET_TITLE_ACQUIRED),
                anyString(), anyString(), eq("/dex?tab=titles"), eq("pet-title:3:10"));
        verify(eventPublisher).publishEvent(new TitleCheckEvent(USER_ID, "PET_TITLE_ACQUIRED"));
    }

    @Test
    @DisplayName("같은 칭호라도 펫이 다르면 알림 키가 달라 두 번째 펫도 알림을 받는다")
    void notificationKeyIsPerPet() {
        service.evaluate(pet(4L, PetLevel.JUVENILE_LEVEL, 0, LocalDateTime.now()));
        service.evaluate(pet(5L, PetLevel.JUVENILE_LEVEL, 0, LocalDateTime.now()));

        verify(notificationService).notify(eq(USER_ID), any(), anyString(), anyString(), anyString(), eq("pet-title:4:10"));
        verify(notificationService).notify(eq(USER_ID), any(), anyString(), anyString(), anyString(), eq("pet-title:5:10"));
    }

    @Test
    @DisplayName("이미 딴 칭호는 다시 주지 않고, 지표가 내려가도 회수하지 않는다")
    void doesNotRegrantOrRevoke() {
        Pet lowered = pet(6L, 1, 0, LocalDateTime.now()); // 관리자 도구로 레벨이 내려간 경우
        when(awardRepository.findByPetIdIn(List.of(6L))).thenReturn(List.of(award(60L, 6L, evolve)));

        List<GrantedTitle> granted = service.evaluate(lowered);

        assertThat(granted).isEmpty();
        verify(awardRepository, never()).save(any());
        verify(awardRepository, never()).delete(any());
        verify(awardRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("분양한 펫은 판정하지 않는다 — 칭호는 분양 순간에 고정된다")
    void releasedPetIsNotEvaluated() {
        Pet released = pet(7L, PetLevel.MAX_LEVEL, 500, LocalDateTime.now().minusDays(10));
        released.release(100, LocalDateTime.now());

        assertThat(service.evaluate(released)).isEmpty();
        verifyNoInteractions(awardRepository, notificationService, eventPublisher);
    }

    @Test
    @DisplayName("히든 칭호는 따기 전에는 조건을 가리고 달성률만, 딴 뒤에는 설명까지 보인다")
    void hiddenTitleIsMaskedUntilAcquired() {
        Pet pet = pet(8L, 1, 0, LocalDateTime.now());
        when(petRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).thenReturn(List.of(pet));
        when(growthLogRepository.countByPetIdAndReason(8L, GrowthReason.PET_INTERACTION)).thenReturn(3L);

        PetTitleItem masked = service.board(USER_ID).titles().stream()
                .filter(t -> t.code().equals("PET_LUCKY_CHARM")).findFirst().orElseThrow();
        assertThat(masked.description()).isEqualTo("???");
        assertThat(masked.current()).isZero();
        assertThat(masked.threshold()).isZero();
        assertThat(masked.progressPct()).isEqualTo(30);

        when(awardRepository.findByPetIdIn(anyCollection())).thenReturn(List.of(award(80L, 8L, lucky)));
        PetTitleItem item = service.board(USER_ID).titles().stream()
                .filter(t -> t.code().equals("PET_LUCKY_CHARM")).findFirst().orElseThrow();
        assertThat(item.acquired()).isTrue();
        assertThat(item.hidden()).isTrue();
        assertThat(item.description()).isEqualTo("행운의 매력 조건");
        assertThat(item.awardId()).isEqualTo(80L);
    }

    @Test
    @DisplayName("키우는 펫이 없으면 과제를 0% 로 보여 주고 히든은 조건을 가린다")
    void boardWithoutPet() {
        when(petRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).thenReturn(List.of());

        PetTitleBoardResponse board = service.board(USER_ID);

        assertThat(board.petId()).isNull();
        assertThat(board.titles()).extracting(PetTitleItem::code)
                .containsExactly("PET_EVOLVE_2", "PET_LOVELY", "PET_TALKATIVE", "PET_LUCKY_CHARM");
        assertThat(board.titles().get(3).description()).isEqualTo("???");
        assertThat(board.titles()).extracting(PetTitleItem::progressPct).containsOnly(0);
    }

    @Test
    @DisplayName("그 펫이 딴 칭호만 장착할 수 있다")
    void equipMustBelongToPet() {
        Pet pet = pet(9L, 1, 0, LocalDateTime.now());
        when(petRepository.findById(9L)).thenReturn(Optional.of(pet));
        when(awardRepository.findById(99L)).thenReturn(Optional.of(award(99L, 1L, evolve))); // 다른 펫(1번)의 칭호

        assertThatThrownBy(() -> service.equip(USER_ID, 9L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이 펫이 얻은 칭호만");
        assertThat(pet.getEquippedTitleAwardId()).isNull();
    }

    @Test
    @DisplayName("분양한 펫의 장착 칭호는 바꿀 수 없다")
    void equipOfReleasedPetIsFixed() {
        Pet released = pet(10L, PetLevel.MAX_LEVEL, 0, LocalDateTime.now());
        released.release(100, LocalDateTime.now());
        when(petRepository.findById(10L)).thenReturn(Optional.of(released));

        assertThatThrownBy(() -> service.equip(USER_ID, 10L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("분양한 펫");
    }
}
