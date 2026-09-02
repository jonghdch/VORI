package com.vori.backend.sanction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SanctionRepository extends JpaRepository<Sanction, Long> {

    Page<Sanction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 해제(lift) 안 된 제재 — 로그인 차단 판정용. 만료 여부는 서비스에서 계산. */
    List<Sanction> findByUserIdAndLiftedAtIsNull(Long userId);
}
