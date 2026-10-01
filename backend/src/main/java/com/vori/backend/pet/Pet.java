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

    public void addStat(com.vori.backend.common.StatType statType, int delta) {
        switch (statType) {
            case ENERGY     -> this.statEnergy     += delta;
            case CHARM      -> this.statCharm      += delta;
            case IQ         -> this.statIq         += delta;
            case ENDURANCE  -> this.statEndurance  += delta;
        }
    }

    /** 해당 단계가 되기 위한 최소 스탯 합. 기준은 PetLevel 한 곳에 있다(EXP 를 스탯 단위로 환산). */
    public static int minStatTotalFor(PetStage stage) {
        return PetLevel.minExpFor(PetLevel.levelOf(stage)) / PetLevel.EXP_PER_STAT;
    }

    /** 4대 스탯 합. */
    public int statTotal() {
        return nz(statEnergy) + nz(statCharm) + nz(statIq) + nz(statEndurance);
    }

    /** EXP = 스탯 합 × 10. 레벨 판정과 분양가 산출의 기준값. */
    public int exp() {
        return statTotal() * PetLevel.EXP_PER_STAT;
    }

    /** EXP 에서 계산한 레벨(1~30). */
    public int level() {
        return PetLevel.levelFor(exp());
    }

    /**
     * 화면·리포트에 보일 단계 — 저장된 단계와 레벨이 정하는 단계 중 높은 쪽.
     * 단계는 스탯이 오를 때만 다시 계산되므로, 진화 기준을 바꾼 직후의 기존 펫은 저장값이 낮을 수 있다.
     * 다음 성장 때 evaluateStage 가 저장값도 맞춘다.
     */
    public PetStage displayStage() {
        PetStage byLevel = PetLevel.stageFor(level());
        return byLevel.ordinal() > stage.ordinal() ? byLevel : stage;
    }

    /** 만렙(30)을 달성해 졸업(분양)할 수 있는가. */
    public boolean isGraduated() {
        return level() >= PetLevel.MAX_LEVEL;
    }

    /**
     * 레벨에 따라 성장 단계를 갱신한다(5레벨 2차, 15레벨 3차). addStat 직후 호출.
     * 단계는 되돌아가지 않는다 — 지출 삭제로 스탯이 줄어도 이미 큰 펫이 도로 작아지면
     * 사용자 경험이 무너지므로 상향 전이만 허용.
     */
    public void evaluateStage() {
        PetStage next = PetLevel.stageFor(level());
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
        setStatTotal(minStatTotalFor(target));
        this.stage = target;
    }

    /** 레벨을 강제로 맞춘다(관리자 시연용, 내려가기 허용). 스탯 합을 그 레벨의 최소값으로, 단계도 그 레벨에 맞춘다. */
    public void forceLevel(int level) {
        int target = Math.max(1, Math.min(level, PetLevel.MAX_LEVEL));
        setStatTotal(PetLevel.minExpFor(target) / PetLevel.EXP_PER_STAT);
        this.stage = PetLevel.stageFor(target);
    }

    /** 스탯 합을 total 로 맞추며 4대 스탯에 고르게 나눈다. */
    private void setStatTotal(int total) {
        int base = total / 4;
        int rem = total % 4;
        this.statEnergy = base + (rem > 0 ? 1 : 0);
        this.statCharm = base + (rem > 1 ? 1 : 0);
        this.statIq = base + (rem > 2 ? 1 : 0);
        this.statEndurance = base;
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
