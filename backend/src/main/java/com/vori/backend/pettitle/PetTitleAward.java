package com.vori.backend.pettitle;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 펫이 딴 칭호 1건. 한 번 딴 칭호는 회수하지 않고, 배웅한 펫의 기록으로도 그대로 남는다.
 */
@Entity
@Table(name = "pet_title_awards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PetTitleAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pet_id", nullable = false)
    private Long petId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_title_id", nullable = false)
    private PetTitle title;

    // 획득 시점의 근거 {code, threshold, value} — 나중에 "왜 이때 땄지" 를 설명할 수 있게
    @Column(name = "unlock_condition", columnDefinition = "JSON")
    private String unlockCondition;

    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;
}
