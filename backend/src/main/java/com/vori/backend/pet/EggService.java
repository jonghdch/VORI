package com.vori.backend.pet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vori.backend.pet.dto.EggProductResponse;
import com.vori.backend.pet.dto.EggResponse;
import com.vori.backend.pet.dto.GachaResultResponse;
import com.vori.backend.pet.dto.PetResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 알 상점·인벤토리·개봉(가챠). 추첨 확률 계산 자체는 GachaService 가 맡는다.
 *
 * 게임 루프: 절약 → 게임머니 적립(ExpenseService) → 알 구매 → 개봉 → 펫 획득
 *            → 절약으로 스탯 성장 → 성체 분양(PetService) → 게임머니 회수
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EggService {

    private final EggRepository eggRepository;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final PetSpeciesRepository petSpeciesRepository;
    private final GachaPullRepository gachaPullRepository;
    private final GachaService gachaService;
    private final ObjectMapper objectMapper;

    /** 상점 상품 목록. 가격·확률은 EggGrade 가 단일 출처. */
    public List<EggProductResponse> listProducts() {
        return Arrays.stream(EggGrade.values())
                .map(EggProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EggResponse> listMyEggs(Long userId, boolean unopenedOnly) {
        List<Egg> eggs = unopenedOnly
                ? eggRepository.findByUserIdAndOpenedAtIsNull(userId)
                : eggRepository.findByUserIdOrderByPurchasedAtDesc(userId);
        return eggs.stream().map(EggResponse::from).toList();
    }

    /**
     * 알 구매 — 게임머니 차감 후 eggs INSERT.
     * 잔액은 행 잠금으로 읽어 더블클릭 이중 차감을 막는다.
     */
    @Transactional
    public EggResponse buy(Long userId, EggGrade grade) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        try {
            user.spendGameMoney(grade.price());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "코인이 부족합니다");
        }

        Egg egg = eggRepository.save(Egg.builder()
                .userId(userId)
                .gradeName(grade.displayName())
                // 구매 시점 정책을 박제 — 이후 EggGrade 를 바꿔도 이 알의 확률은 고정
                .priceGameMoney(grade.price())
                .probabilityDistribution(writeDistribution(grade))
                .purchasedAt(LocalDateTime.now())
                .build());

        return EggResponse.from(egg);
    }

    /**
     * 알 개봉 — 추첨 → gacha_pulls + pets INSERT → eggs.opened_at 갱신.
     * 알을 행 잠금으로 읽으므로 동시 개봉 요청은 직렬화되어 뒤엣것이 409 를 받는다.
     */
    @Transactional
    public GachaResultResponse open(Long userId, Long eggId) {
        Egg egg = eggRepository.findByIdForUpdate(eggId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알을 찾을 수 없습니다"));
        if (!egg.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 알만 개봉할 수 있습니다");
        }
        if (egg.isOpened()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 개봉한 알입니다");
        }

        GachaService.Draw draw = gachaService.draw(readDistribution(egg));
        LocalDateTime now = LocalDateTime.now();

        gachaPullRepository.save(GachaPull.builder()
                .eggId(egg.getId())
                .drawnSpeciesId(draw.species().getId())
                .variant(draw.variant())
                .drawnAt(now)
                .build());

        Pet pet = petRepository.save(Pet.builder()
                .userId(userId)
                .speciesId(draw.species().getId())
                .eggId(egg.getId())
                .variant(draw.variant())
                .hatchedAt(now)
                .createdAt(now)
                .build());

        egg.markOpened(now);

        int remain = userRepository.findById(userId)
                .map(u -> u.getGameMoney() == null ? 0 : u.getGameMoney())
                .orElse(0);

        return new GachaResultResponse(egg.getId(), PetResponse.of(pet, draw.species()), remain);
    }

    // ───── probability_distribution JSON 직렬화 ─────

    private String writeDistribution(EggGrade grade) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        grade.distribution().forEach((tier, pct) -> raw.put(tier.name(), pct));
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            // EggGrade 는 코드 상수라 실패할 수 없다. 나면 배포가 깨진 것.
            throw new IllegalStateException("확률 분포 직렬화 실패: " + grade, e);
        }
    }

    /** 박제된 JSON → 등급 가중치. 모르는 등급 키는 무시한다(정책이 바뀐 옛 알 대비). */
    private Map<PetTier, Integer> readDistribution(Egg egg) {
        Map<String, Integer> raw;
        try {
            raw = objectMapper.readValue(
                    egg.getProbabilityDistribution(), new TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            log.error("알 확률 분포 파싱 실패 — eggId={}, raw={}",
                    egg.getId(), egg.getProbabilityDistribution(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "알 정보가 손상되어 개봉할 수 없습니다");
        }

        Map<PetTier, Integer> weights = new LinkedHashMap<>();
        raw.forEach((key, weight) -> {
            if (weight == null || weight <= 0) return;
            try {
                weights.put(PetTier.valueOf(key), weight);
            } catch (IllegalArgumentException ignored) {
                log.warn("알 확률 분포에 알 수 없는 등급 — eggId={}, tier={}", egg.getId(), key);
            }
        });

        if (weights.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "알 정보가 손상되어 개봉할 수 없습니다");
        }
        return weights;
    }
}
