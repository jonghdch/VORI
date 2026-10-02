package com.vori.backend.pet;

import com.vori.backend.common.StatType;
import com.vori.backend.furniture.UserFurniture;
import com.vori.backend.furniture.UserFurnitureRepository;
import com.vori.backend.pet.dto.PetInteractionResponse;
import com.vori.backend.notification.NotificationService;
import com.vori.backend.pet.dto.PetResponse;
import com.vori.backend.pettitle.PetTitleService;
import com.vori.backend.pettitle.dto.PetTitleSummary;
import com.vori.backend.theme.ThemeMaster;
import com.vori.backend.theme.ThemeMasterRepository;
import com.vori.backend.title.TitleCheckEvent;
import com.vori.backend.title.dto.GrantedTitle;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 펫 조회·분양·상호작용. 스탯 성장은 주로 지출 등록 흐름(ExpenseService)에서 일어나고,
 * 여기서는 상호작용의 낮은 확률 매력 보너스만 다룬다.
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
    private final PetGrowthLogRepository petGrowthLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final PetTitleService petTitleService;

    // 분양가 = EXP(스탯 합 × 10) × (1 + (개별 가구 보너스합 + 테마 세트 보너스합)/100)

    // 상호작용 1회당 매력이 오를 확률(%)과 오르는 양
    private static final int INTERACT_CHARM_CHANCE_PCT = 1;
    private static final int INTERACT_CHARM_DELTA = 1;
    // 상호작용으로 매력이 오를 수 있는 하루 횟수. 호출 자체엔 제한이 없어, 자동으로 수만 번 불러
    // 매력(→ 성장 단계·분양가)을 모으지 못하게 당첨 횟수를 막는다. 1% 라 정상 사용에선 거의 닿지 않는다.
    static final int INTERACT_CHARM_DAILY_CAP = 3;

    /** 현재 키우는 펫. 없으면 null (신규 가입자·직전에 분양한 경우). */
    @Transactional(readOnly = true)
    public PetResponse getActive(Long userId) {
        List<Pet> pets = petRepository.findByUserIdAndReleasedAtIsNull(userId);
        if (pets.isEmpty()) return null;
        Pet pet = pets.get(0);
        return toResponse(pet);
    }

    /** 보유·분양 이력 전체 (최신순). */
    @Transactional(readOnly = true)
    public List<PetResponse> listAll(Long userId) {
        List<Pet> pets = petRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, PetSpecies> speciesById = loadSpecies(pets);
        Map<Long, List<PetTitleSummary>> titlesByPet =
                petTitleService.summariesByPet(pets.stream().map(Pet::getId).toList());
        return pets.stream()
                .map(p -> PetResponse.of(p, speciesById.get(p.getSpeciesId()),
                        titlesByPet.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    /** 키우는 펫의 이름을 짓는다(다시 지어도 된다). 분양한 펫은 기록이라 바꾸지 않는다. */
    @Transactional
    public PetResponse rename(Long userId, Long petId, String name) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "펫을 찾을 수 없습니다"));
        if (!pet.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 펫에만 이름을 지을 수 있습니다");
        }
        if (pet.isReleased()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 분양한 펫입니다");
        }
        pet.rename(name);
        return toResponse(pet);
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
        if (!pet.isGraduated()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, PetLevel.MAX_LEVEL + "레벨을 달성한 펫만 분양할 수 있습니다");
        }

        // 분양하면 판정하지 않으므로, 마지막으로 칭호를 본 뒤 그 기록을 펫에 고정한다
        petTitleService.evaluate(pet);

        int value = calculateReleaseValue(userId, pet);

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        user.addGameMoney(value);

        pet.release(value, LocalDateTime.now());
        log.info("펫 분양 — userId={}, petId={}, statTotal={}, value={}",
                userId, petId, pet.statTotal(), value);

        // 분양 횟수가 바뀌었으므로 칭호 조건을 다시 본다
        eventPublisher.publishEvent(new TitleCheckEvent(userId, "PET_RELEASED"));

        return toResponse(pet);
    }

    /**
     * 펫 상호작용(쓰다듬기·칭찬하기 등) 1회. 1% 확률로 매력이 1 오른다.
     * 추첨은 서버에서 한다 — 클라이언트가 당첨 여부를 정하면 요청만 조작해 스탯을 올릴 수 있다.
     * 상호작용 횟수·매력 보너스로 펫 칭호가 채워지면 그 자리에서 지급하고 응답에 싣는다 — 히든 칭호는
     * 목록에 없던 것이라 획득 순간을 알려주지 않으면 사용자가 알 길이 없다.
     */
    @Transactional
    public PetInteractionResponse interact(Long userId) {
        return interact(userId, ThreadLocalRandom.current().nextInt(100));
    }

    /** roll 은 0~99 추첨값. 테스트에서 당첨·꽝을 고정하려고 분리했다. */
    PetInteractionResponse interact(Long userId, int roll) {
        Pet pet = petRepository.findByUserIdAndReleasedAtIsNull(userId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "키우는 펫이 있어야 상호작용할 수 있습니다"));

        pet.recordInteraction();

        boolean charmUp = false;
        if (roll < INTERACT_CHARM_CHANCE_PCT) {
            // 당첨일 때만 사용자 행을 잠가, 동시 당첨이 "아직 상한 미만" 을 함께 읽고 넘치지 않게 한다
            userRepository.findByIdForUpdate(userId);
            charmUp = petGrowthLogRepository.countByPetIdAndReasonAndCreatedAtGreaterThanEqual(
                    pet.getId(), GrowthReason.PET_INTERACTION, LocalDate.now().atStartOfDay())
                    < INTERACT_CHARM_DAILY_CAP;
        }
        if (charmUp) {
            int levelBefore = pet.level();
            pet.addStat(StatType.CHARM, INTERACT_CHARM_DELTA);
            pet.evaluateStage();
            notificationService.petGrew(userId, pet, levelBefore);
            petGrowthLogRepository.save(PetGrowthLog.builder()
                    .petId(pet.getId())
                    .userId(userId)
                    .statType(StatType.CHARM)
                    .delta(INTERACT_CHARM_DELTA)
                    .savedAmount(0)
                    .reason(GrowthReason.PET_INTERACTION)
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("펫 상호작용 매력 보너스 — userId={}, petId={}, charm={}",
                    userId, pet.getId(), pet.getStatCharm());
        }
        List<GrantedTitle> newTitles = petTitleService.evaluate(pet);
        return new PetInteractionResponse(charmUp, toResponse(pet), newTitles);
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

        // 기본값 = EXP (= 스탯 합 × 10)
        BigDecimal base = BigDecimal.valueOf((long) pet.exp());
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

    /** 칭호를 실은 펫 응답. */
    private PetResponse toResponse(Pet pet) {
        return PetResponse.of(pet, findSpecies(pet.getSpeciesId()),
                petTitleService.summariesByPet(List.of(pet.getId())).getOrDefault(pet.getId(), List.of()));
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
