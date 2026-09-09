package com.vori.backend.title;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TitleRepository extends JpaRepository<Title, Long> {

    List<Title> findByEnabledTrueOrderBySortOrderAscIdAsc();
}
