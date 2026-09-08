package com.vori.backend.furniture;

import com.vori.backend.furniture.dto.FurniturePlaceRequest;
import com.vori.backend.furniture.dto.FurnitureProductResponse;
import com.vori.backend.furniture.dto.FurnitureResponse;
import com.vori.backend.theme.ThemeMaster;
import com.vori.backend.theme.ThemeService;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 마이룸 가구 상점·보유·배치.
 *
 * 배치한 가구의 release_bonus_pct 합이 펫 분양가에 가산된다(PetService.calculateReleaseValue).
 * 인벤토리에 쌓아둔 가구는 계산에서 빠진다 — 꾸며야 이득이라는 게 보상 설계 의도.
 *
 * 같은 테마 가구를 required_count 이상 **배치**하면 세트 보너스가 추가된다. 테마 해금은
 * 칭호로 하며(theme_master.unlock_title_name), 판정은 ThemeService 한 곳에서만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FurnitureService {

    private final UserFurnitureRepository userFurnitureRepository;
    private final UserRepository userRepository;
    private final ThemeService themeService;

    /**
     * 상점 목록. 가격 오름차순 — 화면에서 다시 정렬하지 않아도 되게.
     * 잠긴 가구도 빼지 않고 locked=true 로 내려준다 — 해금 조건이 보여야 목표가 된다.
     */
    @Transactional(readOnly = true)
    public List<FurnitureProductResponse> listProducts(Long userId) {
        Map<String, ThemeMaster> themes = themeService.loadByName();
        Set<String> unlocked = themeService.unlockedNames(userId);

        return Arrays.stream(FurnitureCatalog.values())
                .sorted(Comparator.comparingInt(FurnitureCatalog::price))
                .map(c -> {
                    ThemeMaster theme = resolveTheme(c, themes);
                    return FurnitureProductResponse.from(c, theme, isLocked(theme, unlocked));
                })
                .toList();
    }

    /** 보유 가구 전체. 배치된 것부터, 그다음 인벤토리. */
    @Transactional(readOnly = true)
    public List<FurnitureResponse> listMine(Long userId) {
        return userFurnitureRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(UserFurniture::isPlaced).reversed()
                        .thenComparing(UserFurniture::getId))
                .map(FurnitureResponse::from)
                .toList();
    }

    /**
     * 가구 구매 — 게임머니 차감 후 user_furniture INSERT.
     * 잔액은 행 잠금으로 읽어 더블클릭 이중 차감을 막는다(알 구매와 동일).
     * 구매 직후에는 인벤토리 상태(좌표 NULL)다. 배치는 별도 호출.
     *
     * 잠긴 테마 가구는 403. 상점 목록에는 보이지만 사는 건 서버가 막는다 —
     * 화면이 잠금을 그려주더라도 API 를 직접 호출하면 그만이므로 여기서 다시 본다.
     */
    @Transactional
    public FurnitureResponse buy(Long userId, FurnitureCatalog item) {
        ThemeMaster theme = resolveTheme(item, themeService.loadByName());
        if (isLocked(theme, themeService.unlockedNames(userId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "'" + theme.getUnlockTitleName() + "' 칭호를 획득해야 살 수 있습니다");
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        try {
            user.spendGameMoney(item.price());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "코인이 부족합니다");
        }

        UserFurniture saved = userFurnitureRepository.save(UserFurniture.builder()
                .userId(userId)
                // 구매 시점 정보를 복사해 박제 — 이후 카탈로그를 고쳐도 이 가구는 그대로
                .name(item.displayName())
                .category(item.category())
                .statTarget(item.statTarget())
                .releaseBonusPct(item.releaseBonusPct())
                .themeId(theme == null ? null : theme.getId())
                .priceGameMoney(item.price())
                .acquiredAt(LocalDateTime.now())
                .build());

        return FurnitureResponse.from(saved);
    }

    /** 마이룸에 배치. 같은 좌표에 다른 가구가 있으면 409. */
    @Transactional
    public FurnitureResponse place(Long userId, Long furnitureId, FurniturePlaceRequest req) {
        UserFurniture furniture = findOwned(userId, furnitureId);

        boolean occupied = userFurnitureRepository.existsByUserIdAndPositionXAndPositionY(
                userId, req.positionX(), req.positionY());
        // 이미 그 자리에 있는 가구를 같은 좌표로 다시 배치하는 건 허용(멱등)
        boolean sameSpot = furniture.isPlaced()
                && req.positionX().equals(furniture.getPositionX())
                && req.positionY().equals(furniture.getPositionY());
        if (occupied && !sameSpot) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 다른 가구가 놓인 자리입니다");
        }

        furniture.placeAt(req.positionX(), req.positionY());
        return FurnitureResponse.from(furniture);
    }

    /** 인벤토리로 회수. 이미 인벤토리에 있으면 그대로 둔다(멱등). */
    @Transactional
    public FurnitureResponse unplace(Long userId, Long furnitureId) {
        UserFurniture furniture = findOwned(userId, furnitureId);
        furniture.removeFromRoom();
        return FurnitureResponse.from(furniture);
    }

    /**
     * 카탈로그가 가리키는 테마. 시드가 없거나 이름이 어긋나면 null 이고, 그 가구는 테마 없는 가구처럼
     * 동작한다 — 상점 전체가 잠기는 것보다 세트 보너스만 못 받는 쪽이 낫다.
     */
    private ThemeMaster resolveTheme(FurnitureCatalog item, Map<String, ThemeMaster> themes) {
        return item.themeName() == null ? null : themes.get(item.themeName());
    }

    /** 해금 조건이 걸린 테마인데 그 칭호가 없으면 잠김. 테마가 없거나 조건이 없으면 항상 열림. */
    private boolean isLocked(ThemeMaster theme, Set<String> unlockedNames) {
        return theme != null
                && theme.getUnlockTitleName() != null
                && !unlockedNames.contains(theme.getName());
    }

    private UserFurniture findOwned(Long userId, Long furnitureId) {
        UserFurniture f = userFurnitureRepository.findById(furnitureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "가구를 찾을 수 없습니다"));
        if (!f.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 가구만 옮길 수 있습니다");
        }
        return f;
    }
}
