package com.vori.backend.title;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TitleRepository extends JpaRepository<Title, Long> {

    List<Title> findByEnabledTrueOrderBySortOrderAscIdAsc();

    /** 어드민 목록 — 비활성 포함 전체. */
    List<Title> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByCode(String code);

    /** 이 지표값이 정확히 목표치인 활성 칭호가 있는지 — 값이 1씩 오르는 지표의 "방금 닿았나" 판정용. */
    boolean existsByEnabledTrueAndMetricTypeAndThreshold(TitleMetricType metricType, Long threshold);
}
