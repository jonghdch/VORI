package com.vori.backend.pettitle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PetTitleRepository extends JpaRepository<PetTitle, Long> {

    List<PetTitle> findByEnabledTrueOrderBySortOrderAscIdAsc();
}
