package com.vori.backend.pet;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 펫 종족 도감 (마스터 데이터). 시드로 16개 INSERT (PetSpeciesSeeder).
 * 등급: S(용·사자·뱀·여우) / A(사슴·펭귄·늑대·거북이) / B(강아지·고양이·토끼·양) / C(개구리·다람쥐·원숭이·팬더).
 * is_starter = TRUE 인 행은 회원가입 시 자동 부여 (TBD — 현재 전부 FALSE).
 */
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

    // 프론트 asset 매칭 키 (예: "dragon", "puppy"). 종족 이미지·애니메이션 파일명과 1:1
    @Column(name = "appearance_key", nullable = false, length = 50)
    private String appearanceKey;
}
