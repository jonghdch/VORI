package com.vori.backend.pet;

import com.vori.backend.furniture.UserFurniture;
import com.vori.backend.furniture.UserFurnitureRepository;
import com.vori.backend.pet.dto.PetResponse;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // 분양가 = 스탯총합 × 배수 × (1 + 배치가구 보너스합/100)
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

        return PetResponse.of(pet, findSpecies(pet.getSpeciesId()));
    }

    /**
     * 분양가 산출. 마이룸에 **배치된** 가구의 release_bonus_pct 합만 반영한다
     * (인벤토리에 쌓아둔 가구는 제외 — 꾸며야 이득이라는 게 보상 설계 의도).
     */
    private int calculateReleaseValue(Long userId, Pet pet) {
        BigDecimal bonusPct = userFurnitureRepository
                .findByUserIdAndPositionXIsNotNullAndPositionYIsNotNull(userId)
                .stream()
                .map(UserFurniture::getReleaseBonusPct)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal base = BigDecimal.valueOf((long) pet.statTotal() * RELEASE_VALUE_PER_STAT);
        BigDecimal multiplier = BigDecimal.ONE.add(bonusPct.movePointLeft(2));

        return base.multiply(multiplier).setScale(0, RoundingMode.DOWN).intValue();
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
