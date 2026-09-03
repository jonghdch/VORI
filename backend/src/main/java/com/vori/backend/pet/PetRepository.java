package com.vori.backend.pet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findByUserIdAndReleasedAtIsNull(Long userId);

    List<Pet> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 분양한 펫 수 — 펫 관련 칭호 조건. */
    long countByUserIdAndReleasedAtIsNotNull(Long userId);
}
