package com.vori.backend.pet;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "release_value", columnDefinition = "INT UNSIGNED")
    private Integer releaseValue;
}
