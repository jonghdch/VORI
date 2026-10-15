package com.vori.backend.title;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자가 획득한 칭호. UNIQUE(user_id, title_id) — 같은 칭호 중복 획득 X.
 * 일부 칭호는 unlocks_theme_id 로 특정 테마를 해제. (지금은 "업적" — 칭호는 펫이 얻는다, pettitle 패키지)
 * equipOrder 1~3 이면 장착한 업적이다(내 정보 상자 3칸).
 * 획득 조건은 unlock_condition JSON 에 기록 (감사·표시용).
 */
@Entity
@Table(name = "user_titles",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_titles_user_title", columnNames = {"user_id", "title_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @Column(name = "unlock_condition", columnDefinition = "JSON")
    private String unlockCondition;

    @Column(name = "unlocks_theme_id")
    private Long unlocksThemeId;

    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;

    // 장착 순서(1~3) — 내 정보 상자 칸 순서. NULL = 장착 안 함.
    @Column(name = "equip_order", columnDefinition = "TINYINT")
    private Integer equipOrder;

    public void equip(Integer order) {
        this.equipOrder = order;
    }
}
