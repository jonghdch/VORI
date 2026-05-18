package com.vori.backend.pet;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "gacha_pulls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GachaPull {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "egg_id", nullable = false, unique = true)
    private Long eggId;

    @Column(name = "drawn_species_id", nullable = false)
    private Long drawnSpeciesId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,
        columnDefinition = "ENUM('NORMAL','IRO','ALIEN')")
    private PetVariant variant;

    @Column(name = "drawn_at", nullable = false)
    private LocalDateTime drawnAt;
}
