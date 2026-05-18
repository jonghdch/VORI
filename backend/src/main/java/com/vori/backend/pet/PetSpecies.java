package com.vori.backend.pet;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pet_species")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PetSpecies {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,
        columnDefinition = "ENUM('STARTER','S','A','B','C')")
    private PetTier tier;

    @Column(name = "is_starter")
    @Builder.Default
    private Boolean isStarter = false;

    @Column(name = "appearance_key", nullable = false, length = 50)
    private String appearanceKey;
}
