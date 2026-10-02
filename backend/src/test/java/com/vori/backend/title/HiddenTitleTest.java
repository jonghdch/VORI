package com.vori.backend.title;

import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.goal.GoalRepository;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.pet.GachaPullRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.receipt.ReceiptOcrJobRepository;
import com.vori.backend.theme.ThemeMasterRepository;
import com.vori.backend.title.dto.GrantedTitle;
import com.vori.backend.title.dto.TitleResponse;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
 * 히든 칭호(사랑둥이)와 펫 상호작용 지표 검증. 히든 칭호는 따기 전에는 목록에 새어 나가면 안 되고,
 * 조건을 채운 순간에는 바로 지급돼 화면에 알릴 수 있어야 한다.
 * Spring 컨텍스트·DB 없이 도는 순수 단위 테스트.
 */
class HiddenTitleTest {

    private static final long USER_ID = 7L;

    private final UserTitleRepository userTitleRepository = mock(UserTitleRepository.class);
    private final TitleRepository titleRepository = mock(TitleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PetRepository petRepository = mock(PetRepository.class);
    private final ThemeMasterRepository themeMasterRepository = mock(ThemeMasterRepository.class);
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
            themeMasterRepository);

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
    @DisplayName("못 딴 히든 칭호는 목록에 나오지 않는다")
    void lockedHiddenTitleIsNotListed() {
        when(petRepository.maxInteractionCountByUserId(USER_ID)).thenReturn(99L);

        List<TitleResponse> titles = service.list(USER_ID);

        assertThat(titles).extracting(TitleResponse::code).containsExactly("RECORD_START");
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
    @DisplayName("상호작용이 목표치에 닿는 순간 칭호를 지급하고 그 이름을 돌려준다")
    void grantsWhenInteractionCountReachesThreshold() {
        when(titleRepository.existsByEnabledTrueAndMetricTypeAndThreshold(TitleMetricType.PET_INTERACTIONS, 100L))
                .thenReturn(true);
        when(petRepository.maxInteractionCountByUserId(USER_ID)).thenReturn(100L);

        List<GrantedTitle> granted = service.grantOnReach(USER_ID, TitleMetricType.PET_INTERACTIONS, 100);

        assertThat(granted).containsExactly(new GrantedTitle("사랑둥이", true));
        ArgumentCaptor<UserTitle> saved = ArgumentCaptor.forClass(UserTitle.class);
        verify(userTitleRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getTitle()).isSameAs(lovely);
    }

    @Test
    @DisplayName("목표치에 닿지 않은 횟수에서는 평가를 건너뛴다")
    void skipsEvaluationOffThreshold() {
        when(petRepository.maxInteractionCountByUserId(USER_ID)).thenReturn(57L);

        List<GrantedTitle> granted = service.grantOnReach(USER_ID, TitleMetricType.PET_INTERACTIONS, 57);

        assertThat(granted).isEmpty();
        verify(userRepository, never()).findByIdForUpdate(any());
        verify(userTitleRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 가진 칭호는 목표치에 다시 닿아도 또 주지 않는다")
    void doesNotGrantTwice() {
        when(titleRepository.existsByEnabledTrueAndMetricTypeAndThreshold(TitleMetricType.PET_INTERACTIONS, 100L))
                .thenReturn(true);
        when(petRepository.maxInteractionCountByUserId(USER_ID)).thenReturn(150L);
        when(userTitleRepository.findByUserIdAndTitleId(USER_ID, 1L))
                .thenReturn(Optional.of(UserTitle.builder().id(50L).userId(USER_ID).title(lovely).build()));

        // 다른 펫이 100회째를 채운 경우 — 사용자는 이미 칭호가 있다
        List<GrantedTitle> granted = service.grantOnReach(USER_ID, TitleMetricType.PET_INTERACTIONS, 100);

        assertThat(granted).isEmpty();
        verify(userTitleRepository, never()).save(any());
    }

    @Test
    @DisplayName("펫 상호작용 지표는 목표치 이상이면 달성이다")
    void petInteractionMetric() {
        assertThat(lovely.isAchieved(progressWithInteractions(99))).isFalse();
        assertThat(lovely.isAchieved(progressWithInteractions(100))).isTrue();
    }

    private static TitleProgress progressWithInteractions(long count) {
        return new TitleProgress(0, 0, 0, 0, 0, 0, 0, 0, count);
    }
}
