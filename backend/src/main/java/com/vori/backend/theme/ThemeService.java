package com.vori.backend.theme;

import com.vori.backend.furniture.UserFurniture;
import com.vori.backend.furniture.UserFurnitureRepository;
import com.vori.backend.theme.dto.ThemeResponse;
import com.vori.backend.title.UserTitle;
import com.vori.backend.title.UserTitleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 마이룸 테마 — 해금 판정과 세트 발동 현황.
 *
 * 해금의 단일 진실은 theme_master.unlock_title_name 이다. "그 이름의 칭호를 가졌는가" 하나만 본다.
 * user_titles.unlocks_theme_id 는 칭호 획득 시점에 남기는 기록일 뿐 판정에 쓰지 않는다 —
 * 판정을 두 군데서 하면 둘이 어긋났을 때 어느 쪽이 맞는지 알 수 없다.
 *
 * 세트 발동 기준은 PetService.calculateReleaseValue 와 같아야 한다: 마이룸에 **배치된** 가구만 센다.
 * 화면에 "발동 중"이라고 표시했는데 분양가에는 안 얹히면 그게 제일 나쁜 버그다.
 */
@Service
@RequiredArgsConstructor
public class ThemeService {

    private final ThemeMasterRepository themeMasterRepository;
    private final UserFurnitureRepository userFurnitureRepository;
    private final UserTitleRepository userTitleRepository;

    /** 전체 테마 현황. 발동 중인 것 먼저, 그다음 달성이 가까운 순. */
    @Transactional(readOnly = true)
    public List<ThemeResponse> list(Long userId) {
        Set<String> unlocked = unlockedNames(userId);
        Map<Long, Integer> placedByTheme = placedCountByTheme(userId);

        return themeMasterRepository.findAll().stream()
                .map(t -> ThemeResponse.of(
                        t,
                        placedByTheme.getOrDefault(t.getId(), 0),
                        unlocked.contains(t.getName())))
                .sorted(Comparator.comparing(ThemeResponse::active, Comparator.reverseOrder())
                        .thenComparing(ThemeResponse::progressPct, Comparator.reverseOrder()))
                .toList();
    }

    /** 테마 이름 → 마스터. 카탈로그가 이름으로 테마를 가리키므로 한 번에 읽어 맵으로 준다. */
    @Transactional(readOnly = true)
    public Map<String, ThemeMaster> loadByName() {
        return themeMasterRepository.findAll().stream()
                .collect(Collectors.toMap(ThemeMaster::getName, Function.identity(), (a, b) -> a));
    }

    /**
     * 사용자가 쓸 수 있는 테마 이름.
     * unlock_title_name 이 null 인 테마는 조건이 없다는 뜻이므로 누구나 해금 상태다.
     */
    @Transactional(readOnly = true)
    public Set<String> unlockedNames(Long userId) {
        // 칭호 이름은 titles 마스터에 있다(V11). findByUserId 가 title 을 join fetch 하므로 N+1 은 없다.
        Set<String> ownedTitles = userTitleRepository.findByUserId(userId).stream()
                .map(ut -> ut.getTitle().getName())
                .collect(Collectors.toSet());

        return themeMasterRepository.findAll().stream()
                .filter(t -> t.getUnlockTitleName() == null || ownedTitles.contains(t.getUnlockTitleName()))
                .map(ThemeMaster::getName)
                .collect(Collectors.toSet());
    }

    /** 마이룸에 배치된 가구를 테마별로 센다. 테마 없는 가구(벽지·바닥 등)는 제외. */
    private Map<Long, Integer> placedCountByTheme(Long userId) {
        Map<Long, Integer> counts = new HashMap<>();
        for (UserFurniture f : userFurnitureRepository
                .findByUserIdAndPositionXIsNotNullAndPositionYIsNotNull(userId)) {
            if (f.getThemeId() == null) continue;
            counts.merge(f.getThemeId(), 1, Integer::sum);
        }
        return counts;
    }
}
