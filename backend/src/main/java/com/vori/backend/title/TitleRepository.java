package com.vori.backend.title;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TitleRepository extends JpaRepository<Title, Long> {

    List<Title> findByEnabledTrueOrderBySortOrderAscIdAsc();

    /** 어드민 목록 — 비활성 포함 전체. */
    List<Title> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByCode(String code);
}
