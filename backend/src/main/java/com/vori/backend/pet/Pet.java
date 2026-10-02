package com.vori.backend.pet;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자가 키우는 펫 1마리. 절약하면 stat_<type> 이 증가하고 stage 가 진전.
 * stage 전이 조건(INFANT→JUVENILE→ADULT), 분양(released_at·release_value) 룰은 TBD (docs/domain.md).
 * egg_id NULL = 시작 펫 (가챠 없이 받은 것), 값 있음 = 가챠로 부화한 펫.
 */
@Entity
@Table(name = "pets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "species_id", nullable = false)
    private Long speciesId;

    // 사용자가 지어 준 이름(1~10자). NULL = 아직 이름을 짓지 않음 — 화면이 이름 짓기 팝업을 띄운다.
    @Column(length = 10)
    private String name;

    @Column(name = "egg_id", unique = true)
    private Long eggId;

    @Column(name = "hatched_at")
    private LocalDateTime hatchedAt;

    @Column(name = "stat_energy")
    @Builder.Default
    private Integer statEnergy = 0;

    @Column(name = "stat_charm")
    @Builder.Default
    private Integer statCharm = 0;

    @Column(name = "stat_iq")
    @Builder.Default
    private Integer statIq = 0;

    @Column(name = "stat_endurance")
    @Builder.Default
    private Integer statEndurance = 0;

    // 쓰다듬기·칭찬하기 등 상호작용 누적 횟수. 칭호 조건(PET_INTERACTIONS)의 기준값.
    @Column(name = "interaction_count", nullable = false)
    @Builder.Default
    private Integer interactionCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('INFANT','JUVENILE','ADULT')")
    @Builder.Default
    private PetStage stage = PetStage.INFANT;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('NORMAL','IRO','ALIEN')")
    @Builder.Default
    private PetVariant variant = PetVariant.NORMAL;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 진화 임계값 — 4대 스탯 합 기준. 1학기 설계서 §Step 4 기준값.
    private static final int JUVENILE_THRESHOLD = 200;
    private static final int ADULT_THRESHOLD = 300;

    public void addStat(com.vori.backend.common.StatType statType, int delta) {
        switch (statType) {
            case ENERGY     -> this.statEnergy     += delta;
            case CHARM      -> this.statCharm      += delta;
            case IQ         -> this.statIq         += delta;
            case ENDURANCE  -> this.statEndurance  += delta;
        }
    }

    /** 스탯 상한 100까지만 올리고 실제 반영된 수치를 돌려준다. */
    public int addStatUpToMax(com.vori.backend.common.StatType statType, int delta) {
        int current = statValue(statType);
        int applied = Math.max(0, Math.min(delta, 100 - current));
        addStat(statType, applied);
        return applied;
    }

    public int statValue(com.vori.backend.common.StatType statType) {
        return switch (statType) {
            case ENERGY -> nz(statEnergy); case CHARM -> nz(statCharm);
            case IQ -> nz(statIq); case ENDURANCE -> nz(statEndurance);
        };
    }

    /** 해당 단계가 되기 위한 최소 스탯 합. 임계값이 여기 한 곳에만 있도록 밖에서도 이걸 쓴다. */
    public static int minStatTotalFor(PetStage stage) {
        return switch (stage) {
            case INFANT -> 0;
            case JUVENILE -> JUVENILE_THRESHOLD;
            case ADULT -> ADULT_THRESHOLD;
        };
    }

    /** 4대 스탯 합. 진화 판정·분양가 산출의 기준값. */
    public int statTotal() {
        return nz(statEnergy) + nz(statCharm) + nz(statIq) + nz(statEndurance);
    }

    /**
     * 스탯 합에 따라 성장 단계를 갱신한다. addStat 직후 호출.
     * 단계는 되돌아가지 않는다 — 지출 삭제로 스탯이 줄어도 이미 큰 펫이 도로 작아지면
     * 사용자 경험이 무너지므로 상향 전이만 허용.
     */
    public void evaluateStage() {
        int total = statTotal();
        PetStage next = total >= ADULT_THRESHOLD ? PetStage.ADULT
                : total >= JUVENILE_THRESHOLD ? PetStage.JUVENILE
                : PetStage.INFANT;
        if (next.ordinal() > this.stage.ordinal()) {
            this.stage = next;
        }
    }

    // ───── 관리자 도구 전용 (AdminPetService) ─────
    // 일반 흐름에서는 종족·변종은 부화 때 정해지고 단계는 상향만 한다. 아래 둘은 시연·QA 에서
    // 모든 종족·단계를 화면에서 확인하려고 관리자에게만 연 경로다. 호출부가 권한을 보장한다.

    /** 종족·변종을 바꾼다. 스탯·단계는 그대로. */
    public void changeAppearance(Long speciesId, PetVariant variant) {
        this.speciesId = speciesId;
        this.variant = variant == null ? PetVariant.NORMAL : variant;
    }

    /**
     * 단계를 강제로 맞춘다. 스탯 합을 그 단계의 최소값으로 4등분해 채우므로
     * evaluateStage() 를 다시 돌려도 같은 단계가 나온다(내려가는 전이도 허용).
     */
    public void forceStage(PetStage target) {
        int total = minStatTotalFor(target);
        int base = total / 4;
        int rem = total % 4;
        this.statEnergy = base + (rem > 0 ? 1 : 0);
        this.statCharm = base + (rem > 1 ? 1 : 0);
        this.statIq = base + (rem > 2 ? 1 : 0);
        this.statEndurance = base;
        this.stage = target;
    }

    /** 상호작용 1회를 센다. */
    public void recordInteraction() {
        this.interactionCount = nz(interactionCount) + 1;
    }

    /** 이름을 짓는다. 길이·공백 검사는 요청 DTO(PetNameRequest)가 맡는다. */
    public void rename(String name) {
        this.name = name;
    }

    public boolean isReleased() {
        return releasedAt != null;
    }

    /** 분양 처리. 보상 금액 산출은 PetService 가 맡는다(가구 보너스가 펫 밖의 정보라서). */
    public void release(int value, LocalDateTime at) {
        this.releasedAt = at;
        this.releaseValue = value;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    // NULL = 현재 키우는 활성 펫. 값 있음 = 분양됨 (다 키워서 처분)
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    // 분양 시 받은 게임머니 보상. 분양가 산출식은 TBD (스탯·가구 보너스 반영 예정)
    @Column(name = "release_value", columnDefinition = "INT UNSIGNED")
    private Integer releaseValue;
}
