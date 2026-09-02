package com.vori.backend.pet;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 가챠 추첨만 담당. 알 구매·펫 생성 같은 상태 변경은 EggService 가 한다 —
 * 확률 로직을 순수하게 떼어놔야 분포 검증 테스트를 돌릴 수 있다.
 */
@Service
@RequiredArgsConstructor
public class GachaService {

    private final PetSpeciesRepository petSpeciesRepository;

    /** 변종 등장 확률(정수 %). 합 100. */
    private static final Map<PetVariant, Integer> VARIANT_WEIGHTS = Map.of(
            PetVariant.NORMAL, 90,
            PetVariant.IRO, 8,
            PetVariant.ALIEN, 2
    );

    public record Draw(PetSpecies species, PetVariant variant) {}

    /**
     * 등급 분포 → 종족·변종 추첨.
     *
     * @param tierWeights 등급별 가중치. eggs.probability_distribution 에서 파싱한 값.
     */
    @Transactional(readOnly = true)
    public Draw draw(Map<PetTier, Integer> tierWeights) {
        PetTier tier = pick(tierWeights);

        List<PetSpecies> pool = petSpeciesRepository.findByTier(tier);
        if (pool.isEmpty()) {
            // 시드 누락 — 사용자 잘못이 아니라 마스터 데이터 문제라 그대로 터뜨린다
            throw new IllegalStateException("펫 종족 시드 누락: tier=" + tier);
        }
        PetSpecies species = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));

        return new Draw(species, pick(VARIANT_WEIGHTS));
    }

    /**
     * 가중치 누적합 추첨. 합이 100 이 아니어도 동작하도록 총합 기준으로 굴린다
     * (분포가 박제된 과거 알에 다른 스케일이 들어 있어도 안전).
     */
    private static <T> T pick(Map<T, Integer> weights) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            throw new IllegalStateException("확률 분포의 가중치 합이 0 이하입니다: " + weights);
        }

        int roll = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        for (Map.Entry<T, Integer> e : weights.entrySet()) {
            cumulative += e.getValue();
            if (roll < cumulative) return e.getKey();
        }
        // 누적합이 total 과 같으므로 도달 불가. 방어적으로 마지막 항목 반환.
        throw new IllegalStateException("추첨 실패 — 도달 불가 경로: " + weights);
    }
}
