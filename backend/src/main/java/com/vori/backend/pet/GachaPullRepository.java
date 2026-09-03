package com.vori.backend.pet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GachaPullRepository extends JpaRepository<GachaPull, Long> {

    Optional<GachaPull> findByEggId(Long eggId);

    /**
     * 그 사용자가 특정 등급 펫을 뽑은 횟수 — 가챠 칭호 조건.
     * gacha_pulls 에는 user_id 가 없어 eggs 를 거쳐야 한다.
     */
    @Query("""
        SELECT COUNT(g) FROM GachaPull g, Egg e, PetSpecies s
        WHERE g.eggId = e.id AND g.drawnSpeciesId = s.id
          AND e.userId = :userId AND s.tier = :tier
        """)
    long countByUserIdAndTier(@Param("userId") Long userId, @Param("tier") PetTier tier);
}
