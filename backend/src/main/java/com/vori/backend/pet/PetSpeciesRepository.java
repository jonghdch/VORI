package com.vori.backend.pet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PetSpeciesRepository extends JpaRepository<PetSpecies, Long> {

    Optional<PetSpecies> findByName(String name);

    List<PetSpecies> findByTier(PetTier tier);

    List<PetSpecies> findByIsStarterTrue();
}
