package com.vori.backend.pet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EggRepository extends JpaRepository<Egg, Long> {

    List<Egg> findByUserIdAndOpenedAtIsNull(Long userId);

    List<Egg> findByUserIdOrderByPurchasedAtDesc(Long userId);
}
