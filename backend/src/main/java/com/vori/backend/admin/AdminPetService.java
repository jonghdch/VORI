package com.vori.backend.admin;

import com.vori.backend.common.StatType;
import com.vori.backend.pet.GrowthReason;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLog;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetSpeciesRepository;
import com.vori.backend.pet.PetStage;
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
