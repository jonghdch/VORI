package com.vori.backend.pet;

import com.vori.backend.furniture.UserFurniture;
import com.vori.backend.furniture.UserFurnitureRepository;
import com.vori.backend.pet.dto.PetResponse;
import com.vori.backend.theme.ThemeMaster;
import com.vori.backend.theme.ThemeMasterRepository;
import com.vori.backend.title.TitleCheckEvent;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 펫 조회·분양. 스탯 성장 자체는 지출 등록 흐름(ExpenseService)에서 일어난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;
    private final PetSpeciesRepository petSpeciesRepository;
    private final UserRepository userRepository;
    private final UserFurnitureRepository userFurnitureRepository;
    private final ThemeMasterRepository themeMasterRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 분양가 = 스탯총합 × 배수 × (1 + (개별 가구 보너스합 + 테마 세트 보너스합)/100)
    private static final int RELEASE_VALUE_PER_STAT = 10;

    /** 현재 키우는 펫. 없으면 null (신규 가입자·직전에 분양한 경우). */
    @Transactional(readOnly = true)
    public PetResponse getActive(Long userId) {
        List<Pet> pets = petRepository.findByUserIdAndReleasedAtIsNull(userId);
        if (pets.isEmpty()) return null;
        Pet pet = pets.get(0);
        return PetResponse.of(pet, findSpecies(pet.getSpeciesId()));
    }

    /** 보유·분양 이력 전체 (최신순). */
    @Transactional(readOnly = true)
    public List<PetResponse> listAll(Long userId) {
        List<Pet> pets = petRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, PetSpecies> speciesById = loadSpecies(pets);
        return pets.stream()
                .map(p -> PetResponse.of(p, speciesById.get(p.getSpeciesId())))
                .toList();
    }

    /**
     * 성체 펫 분양 — 게임머니 보상 지급 후 released_at 기록.
     * 잔액 갱신 경로라 사용자 행을 잠그고 읽는다.
     */
    @Transactional
    public PetResponse release(Long userId, Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "펫을 찾을 수 없습니다"));
        if (!pet.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 펫만 분양할 수 있습니다");
        }
        if (pet.isReleased()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 분양한 펫입니다");
        }
        if (pet.getStage() != PetStage.ADULT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "성체가 된 펫만 분양할 수 있습니다");
        }

        int value = calculateReleaseValue(userId, pet);

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        user.addGameMoney(value);

        pet.release(value, LocalDateTime.now());
        log.info("펫 분양 — userId={}, petId={}, statTotal={}, value={}",
                userId, petId, pet.statTotal(), value);

        // 분양 횟수가 바뀌었으므로 칭호 조건을 다시 본다
        eventPublisher.publishEvent(new TitleCheckEvent(userId, "PET_RELEASED"));

        return PetResponse.of(pet, findSpecies(pet.getSpeciesId()));
    }

    /**
     * 분양가 산출. 마이룸에 **배치된** 가구만 반영한다
     * (인벤토리에 쌓아둔 가구는 제외 — 꾸며야 이득이라는 게 보상 설계 의도).
     *
     * 보너스는 두 겹이다: 가구 개별 release_bonus_pct 합 + 같은 테마를 required_count 이상
     * 배치했을 때의 세트 보너스 합. 세트 판정 기준은 ThemeService.list 가 화면에 내려주는
     * 기준과 같아야 한다 — "발동 중"이라 표시됐는데 분양가에 안 얹히면 제일 나쁜 버그다.
     */
    private int calculateReleaseValue(Long userId, Pet pet) {
        List<UserFurniture> placed = userFurnitureRepository
                .findByUserIdAndPositionXIsNotNullAndPositionYIsNotNull(userId);

        BigDecimal bonusPct = placed.stream()
                .map(UserFurniture::getReleaseBonusPct)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(setBonusPct(placed));

        BigDecimal base = BigDecimal.valueOf((long) pet.statTotal() * RELEASE_VALUE_PER_STAT);
        BigDecimal multiplier = BigDecimal.ONE.add(bonusPct.movePointLeft(2));

        return base.multiply(multiplier).setScale(0, RoundingMode.DOWN).intValue();
    }

    /** 배치된 가구를 테마별로 세어, 기준 개수를 채운 테마의 세트 보너스를 합산한다. */
    private BigDecimal setBonusPct(List<UserFurniture> placed) {
        Map<Long, Integer> countByTheme = new HashMap<>();
        for (UserFurniture f : placed) {
            if (f.getThemeId() == null) continue; // 벽지·바닥 등 테마 없는 가구
            countByTheme.merge(f.getThemeId(), 1, Integer::sum);
        }
        if (countByTheme.isEmpty()) return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;
        for (ThemeMaster theme : themeMasterRepository.findAllById(countByTheme.keySet())) {
            // 둘 중 하나라도 비어 있으면 설정이 덜 된 테마 — 조용히 건너뛴다
            if (theme.getRequiredCount() == null || theme.getSetBonusPct() == null) continue;
            if (countByTheme.get(theme.getId()) >= theme.getRequiredCount()) {
                total = total.add(theme.getSetBonusPct());
            }
        }
        return total;
    }

    private PetSpecies findSpecies(Long speciesId) {
        return petSpeciesRepository.findById(speciesId).orElse(null);
    }

    /** N+1 방지 — 펫 목록의 종족을 한 번에 읽어 Map 으로 맵핑. */
    private Map<Long, PetSpecies> loadSpecies(List<Pet> pets) {
        if (pets.isEmpty()) return Map.of();
        List<Long> ids = pets.stream().map(Pet::getSpeciesId).distinct().toList();
        Map<Long, PetSpecies> byId = new HashMap<>();
        petSpeciesRepository.findAllById(ids).forEach(s -> byId.put(s.getId(), s));
        return byId;
    }
}
