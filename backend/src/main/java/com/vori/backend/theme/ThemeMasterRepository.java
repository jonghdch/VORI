package com.vori.backend.theme;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThemeMasterRepository extends JpaRepository<ThemeMaster, Long> {

    Optional<ThemeMaster> findByName(String name);
}
