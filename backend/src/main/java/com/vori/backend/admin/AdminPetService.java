package com.vori.backend.admin;

import com.vori.backend.common.StatType;
import com.vori.backend.pet.GrowthReason;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLog;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetSpeciesRepository;
import com.vori.backend.pet.PetStage;
import com.vori.backend.pet.PetVariant;
import com.vori.backend.pet.PetSpecies;
import com.vori.backend.pet.dto.PetResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 전용 펫 조작 — 시연·QA 목적.
 *
 * 성체(스탯 합 300)까지 정상적으로 키우려면 누적 30만원어치 절약이 필요해 발표 자리에서
 * 분양을 보여줄 수 없다. 그렇다고 진화 임계값 자체를 낮추면 운영 규칙이 시연 때문에 왜곡되므로,
 * 어드민에만 열린 경로로 특정 펫의 스탯을 끌어올린다.
 *
 * 올린 스탯은 pet_growth_logs 에 reason=BONUS 로 남긴다. 흔적 없이 값만 바꾸면 나중에
 * "이 펫은 왜 이렇게 컸지" 를 설명할 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPetService {

    private final PetRepository petRepository;
    private final PetSpeciesRepository petSpeciesRepository;
    private final PetGrowthLogRepository petGrowthLogRepository;

    /**
     * 대상 사용자의 활성 펫을 지정한 단계까지 성장시킨다.
     * 이미 그 단계 이상이면 아무것도 하지 않는다(반복 호출해도 스탯이 계속 불어나지 않게).
     */
    @Transactional
    public PetResponse growActivePet(Long userId, PetStage targetStage) {
        List<Pet> pets = petRepository.findByUserIdAndReleasedAtIsNull(userId);
        if (pets.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "해당 사용자에게 활성 펫이 없습니다");
        }
        Pet pet = pets.get(0);

        int current = pet.statTotal();
        int target = Pet.minStatTotalFor(targetStage);
        if (current >= target) {
            log.info("[ADMIN] 펫 성장 스킵 — 이미 조건 충족. userId={}, petId={}, statTotal={}",
                    userId, pet.getId(), current);
            return PetResponse.of(pet, findSpecies(pet));
        }

        distribute(pet, userId, target - current);
        pet.evaluateStage();

        log.warn("[ADMIN] 펫 스탯 강제 성장(시연용) — userId={}, petId={}, {} -> {}, stage={}",
                userId, pet.getId(), current, pet.statTotal(), pet.getStage());

        return PetResponse.of(pet, findSpecies(pet));
    }

    // ───── 관리자 본인 계정 도구 — 사용자 화면(상점·마이룸·도감)에서 모든 종족·단계를 확인하기 위한 것 ─────

    /** 종족 목록(도감 순서와 무관하게 id 순). 프론트 관리자 도구의 종족 선택용. */
    @Transactional(readOnly = true)
    public List<PetSpeciesSummary> listSpecies() {
        return petSpeciesRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(PetSpecies::getId))
                .map(s -> new PetSpeciesSummary(s.getId(), s.getName(), s.getTier().name(), s.getAppearanceKey()))
                .toList();
    }

    public record PetSpeciesSummary(Long id, String name, String tier, String appearanceKey) {}

    /**
     * 활성 펫의 종족·변종을 바꾼다. 활성 펫이 없으면 그 종족으로 새 펫(아기)을 만든다.
     * 도감·마이룸·홈이 같은 펫을 보므로 화면 셋이 한 번에 바뀐다.
     */
    @Transactional
    public PetResponse setActivePetAppearance(Long userId, Long speciesId, PetVariant variant) {
        PetSpecies species = petSpeciesRepository.findById(speciesId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종족을 찾을 수 없습니다"));
        List<Pet> pets = petRepository.findByUserIdAndReleasedAtIsNull(userId);
        Pet pet;
        if (pets.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            pet = petRepository.save(Pet.builder()
                    .userId(userId)
                    .speciesId(species.getId())
                    .variant(variant == null ? PetVariant.NORMAL : variant)
                    .hatchedAt(now)
                    .createdAt(now)
                    .build());
        } else {
            pet = pets.get(0);
            pet.changeAppearance(species.getId(), variant);
        }
        log.warn("[ADMIN] 펫 외형 변경(시연용) — userId={}, petId={}, species={}, variant={}",
                userId, pet.getId(), species.getName(), pet.getVariant());
        return PetResponse.of(pet, species);
    }

    /** 활성 펫의 레벨을 강제로 맞춘다(시연용, 내려가기 허용). 30 이면 졸업(분양) 버튼이 열린다. */
    @Transactional
    public PetResponse setActivePetLevel(Long userId, int level) {
        List<Pet> pets = petRepository.findByUserIdAndReleasedAtIsNull(userId);
        if (pets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "활성 펫이 없습니다. 먼저 종족을 골라 펫을 만드세요");
        }
        Pet pet = pets.get(0);
        pet.forceLevel(level);
        log.warn("[ADMIN] 펫 레벨 강제 설정(시연용) — userId={}, petId={}, level={}, statTotal={}",
                userId, pet.getId(), pet.level(), pet.statTotal());
        return PetResponse.of(pet, findSpecies(pet));
    }

    /**
     * 활성 펫의 단계를 강제로 맞춘다(내려가는 것도 허용). 스탯은 그 단계의 최소값으로 재설정.
     * 정상 성장(growActivePet)과 달리 성장 로그를 남기지 않는다 — 실제 절약이 아니기 때문.
     */
    @Transactional
    public PetResponse setActivePetStage(Long userId, PetStage stage) {
        List<Pet> pets = petRepository.findByUserIdAndReleasedAtIsNull(userId);
        if (pets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "활성 펫이 없습니다. 먼저 종족을 골라 펫을 만드세요");
        }
        Pet pet = pets.get(0);
        pet.forceStage(stage);
        log.warn("[ADMIN] 펫 단계 강제 설정(시연용) — userId={}, petId={}, stage={}, statTotal={}",
                userId, pet.getId(), pet.getStage(), pet.statTotal());
        return PetResponse.of(pet, findSpecies(pet));
    }

    /**
     * 활성 펫을 비운다 — 보상 0 으로 분양 처리. 성체 조건을 건너뛰므로 관리자 전용.
     * 알 개봉은 펫이 없어야 되므로(409) 개봉 흐름을 다시 보려면 이걸로 자리를 비운다.
     */
    @Transactional
    public void clearActivePet(Long userId) {
        List<Pet> pets = petRepository.findByUserIdAndReleasedAtIsNull(userId);
        if (pets.isEmpty()) return;
        Pet pet = pets.get(0);
        pet.release(0, LocalDateTime.now());
        log.warn("[ADMIN] 펫 비우기(시연용) — userId={}, petId={}", userId, pet.getId());
    }

    /** 부족분을 4대 스탯에 고르게 나눠 넣는다. 나머지는 앞쪽 스탯이 흡수. */
    private void distribute(Pet pet, Long userId, int deficit) {
        StatType[] types = StatType.values();
        int base = deficit / types.length;
        int remainder = deficit % types.length;
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < types.length; i++) {
            int delta = base + (i < remainder ? 1 : 0);
            if (delta <= 0) continue;

            pet.addStat(types[i], delta);
            petGrowthLogRepository.save(PetGrowthLog.builder()
                    .petId(pet.getId())
                    .userId(userId)
                    .statType(types[i])
                    .delta(delta)
                    .savedAmount(0)
                    .reason(GrowthReason.BONUS)
                    .createdAt(now)
                    .build());
        }
    }

    private com.vori.backend.pet.PetSpecies findSpecies(Pet pet) {
        return petSpeciesRepository.findById(pet.getSpeciesId()).orElse(null);
    }
}
