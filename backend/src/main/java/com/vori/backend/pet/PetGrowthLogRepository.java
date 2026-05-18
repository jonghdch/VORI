package com.vori.backend.pet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PetGrowthLogRepository extends JpaRepository<PetGrowthLog, Long> {

    List<PetGrowthLog> findByPetIdOrderByCreatedAtDesc(Long petId);

    List<PetGrowthLog> findByUserIdOrderByCreatedAtDesc(Long userId);
}
