package com.vori.backend.pet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GachaPullRepository extends JpaRepository<GachaPull, Long> {

    Optional<GachaPull> findByEggId(Long eggId);
}
