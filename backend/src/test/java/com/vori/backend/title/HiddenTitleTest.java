package com.vori.backend.title;

import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.goal.GoalRepository;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.pet.GachaPullRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pettitle.PetTitleAwardRepository;
import com.vori.backend.receipt.ReceiptOcrJobRepository;
import com.vori.backend.theme.ThemeMasterRepository;
import com.vori.backend.title.dto.TitleResponse;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 히든 업적 목록 노출과 업적 지표 검증. 히든 업적은 따기 전에는 목록에 새어 나가면 안 된다.
 * (사랑둥이를 그 자리에서 지급하던 grantOnReach 는 펫 칭호로 옮겼다 — PetTitleServiceTest.)
 * Spring 컨텍스트·DB 없이 도는 순수 단위 테스트.
 */
class HiddenTitleTest {

    private static final long USER_ID = 7L;

    private final UserTitleRepository userTitleRepository = mock(UserTitleRepository.class);
    private final TitleRepository titleRepository = mock(TitleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PetRepository petRepository = mock(PetRepository.class);
    private final ThemeMasterRepository themeMasterRepository = mock(ThemeMasterRepository.class);
    private final PetTitleAwardRepository petTitleAwardRepository = mock(PetTitleAwardRepository.class);
    private final TitleService service = new TitleService(
            userTitleRepository,
            titleRepository,
            userRepository,
            mock(ExpenseRepository.class),
            mock(GoalRepository.class),
            petRepository,
            mock(GachaPullRepository.class),
            mock(AiInquiryRepository.class),
            mock(ReceiptOcrJobRepository.class),
            themeMasterRepository,
            mock(com.vori.backend.notification.NotificationService.class),
            petTitleAwardRepository);

    private final Title lovely = title(1L, "PET_LOVELY", "사랑둥이", TitleMetricType.PET_INTERACTIONS, 100, true);
    private final Title recordStart = title(2L, "RECORD_START", "기록의 시작", TitleMetricType.EXPENSE_COUNT, 10, false);

    private static Title title(Long id, String code, String name, TitleMetricType metric,
                               long threshold, boolean hidden) {
        LocalDateTime now = LocalDateTime.now();
        return new Title(id, code, name, name + " 조건", metric, threshold, true, hidden, 0, now, now);
    }

    @BeforeEach
    void setUp() {
        User user = User.builder().id(USER_ID).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(titleRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(lovely, recordStart));
        when(userTitleRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(userTitleRepository.findByUserIdAndTitleId(any(), any())).thenReturn(Optional.empty());
        when(themeMasterRepository.findByUnlockTitleId(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("못 딴 히든 업적은 목록에 나오되 조건을 가리고 달성률만 보여 준다")
    void lockedHiddenTitleIsMasked() {
        when(petRepository.maxInteractionCountByUserId(USER_ID)).thenReturn(99L);

        List<TitleResponse> titles = service.list(USER_ID);

        TitleResponse hidden = titles.stream().filter(t -> t.code().equals("PET_LOVELY")).findFirst().orElseThrow();
        assertThat(hidden.description()).isEqualTo("???");
        assertThat(hidden.current()).isZero();
        assertThat(hidden.threshold()).isZero();
        assertThat(hidden.progressPct()).isEqualTo(99);
        assertThat(hidden.acquired()).isFalse();
        verify(userTitleRepository, never()).save(any());
    }

    @Test
    @DisplayName("딴 히든 칭호는 히든 표시와 함께 목록에 나온다")
    void acquiredHiddenTitleIsListed() {
        UserTitle owned = UserTitle.builder().id(50L).userId(USER_ID).title(lovely)
                .acquiredAt(LocalDateTime.now()).build();
        when(userTitleRepository.findByUserId(USER_ID)).thenReturn(List.of(owned));
        when(userTitleRepository.findByUserIdAndTitleId(USER_ID, 1L)).thenReturn(Optional.of(owned));

        List<TitleResponse> titles = service.list(USER_ID);

        TitleResponse first = titles.get(0);
        assertThat(first.code()).isEqualTo("PET_LOVELY");
        assertThat(first.acquired()).isTrue();
        assertThat(first.hidden()).isTrue();
        assertThat(titles.get(1).hidden()).isFalse();
    }

    @Test
    @DisplayName("펫이 칭호를 처음 얻으면 \"첫 칭호\" 업적을 받는다 — 업적이 펫 칭호 수를 센다")
    void petTitleCountAchievement() {
        Title firstPetTitle = title(3L, "PET_TITLE_FIRST", "첫 칭호", TitleMetricType.PET_TITLES_TOTAL, 1, false);
        when(titleRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(firstPetTitle));
        when(petTitleAwardRepository.countByUserId(USER_ID)).thenReturn(1L);

        service.list(USER_ID);

        verify(userTitleRepository).save(org.mockito.ArgumentMatchers.argThat(
                (UserTitle t) -> t.getTitle() == firstPetTitle && t.getUserId() == USER_ID));
    }

    @Test
    @DisplayName("딴 업적을 3개까지, 고른 순서대로 장착한다")
    void equipUpToThreeInOrder() {
        UserTitle a = UserTitle.builder().id(51L).userId(USER_ID).title(recordStart).acquiredAt(LocalDateTime.now()).build();
        UserTitle b = UserTitle.builder().id(52L).userId(USER_ID).title(lovely).acquiredAt(LocalDateTime.now()).equipOrder(1).build();
        when(userTitleRepository.findByUserId(USER_ID)).thenReturn(List.of(a, b));

        service.equip(USER_ID, List.of(51L));

        assertThat(a.getEquipOrder()).isEqualTo(1);
        assertThat(b.getEquipOrder()).isNull(); // 목록에서 빠진 업적은 장착 해제된다
        // 동시 요청이 순서를 엉키게 하지 않도록 사용자 행을 잠근다
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).findByIdForUpdate(USER_ID);
    }

    @Test
    @DisplayName("업적은 4개 이상 장착할 수 없고, 딴 적 없는 업적도 장착할 수 없다")
    void equipRejectsTooManyOrForeign() {
        UserTitle a = UserTitle.builder().id(51L).userId(USER_ID).title(recordStart).acquiredAt(LocalDateTime.now()).build();
        when(userTitleRepository.findByUserId(USER_ID)).thenReturn(List.of(a));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.equip(USER_ID, List.of(1L, 2L, 3L, 4L)))
                .hasMessageContaining("3개까지");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.equip(USER_ID, List.of(99L)))
                .hasMessageContaining("획득한 업적만");
        assertThat(a.getEquipOrder()).isNull();
    }

    @Test
    @DisplayName("펫 상호작용 지표는 목표치 이상이면 달성이다")
    void petInteractionMetric() {
        assertThat(lovely.isAchieved(progressWithInteractions(99))).isFalse();
        assertThat(lovely.isAchieved(progressWithInteractions(100))).isTrue();
    }

    private static TitleProgress progressWithInteractions(long count) {
        return new TitleProgress(0, 0, 0, 0, 0, 0, 0, 0, count, 0, 0, 0, 0, 0, 0);
    }
}
