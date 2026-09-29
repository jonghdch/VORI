package com.vori.backend.pet;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 펫 종족 도감 (마스터 데이터). 시드로 16개 INSERT (PetSpeciesSeeder).
 * 등급: S·레전드(용·늑대·뱀) / A·에픽(판다·너구리·펭귄·사자)
 *       / B·희귀(사슴·여우·양·원숭이·다람쥐) / C·일반(고양이·강아지·토끼·거북이).
 * is_starter = TRUE 인 행(강아지)은 회원가입 시 자동 부여.
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
