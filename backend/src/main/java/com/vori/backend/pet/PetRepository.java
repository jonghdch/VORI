package com.vori.backend.pet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findByUserIdAndReleasedAtIsNull(Long userId);

    List<Pet> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 분양한 펫 수 — 펫 관련 칭호 조건. */
    long countByUserIdAndReleasedAtIsNotNull(Long userId);

    /** 가장 많이 상호작용한 펫의 횟수(분양한 펫 포함). 펫이 없으면 0 — 상호작용 칭호 조건. */
    @Query("SELECT COALESCE(MAX(p.interactionCount), 0) FROM Pet p WHERE p.userId = :userId")
    long maxInteractionCountByUserId(@Param("userId") Long userId);
}
