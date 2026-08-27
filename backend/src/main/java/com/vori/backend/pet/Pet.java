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
