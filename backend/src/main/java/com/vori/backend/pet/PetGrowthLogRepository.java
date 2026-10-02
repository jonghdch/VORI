package com.vori.backend.pet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PetGrowthLogRepository extends JpaRepository<PetGrowthLog, Long> {

    List<PetGrowthLog> findByPetIdOrderByCreatedAtDesc(Long petId);

    List<PetGrowthLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 이 펫이 from 이후 받은 reason 성장 건수 — 상호작용 보너스 하루 상한용. */
    long countByPetIdAndReasonAndCreatedAtGreaterThanEqual(Long petId, GrowthReason reason, java.time.LocalDateTime from);

    /** 기간 내 스탯 변화량 합계 — 일일 리포트의 stat_delta_total. */
    @Query("""
        SELECT COALESCE(SUM(l.delta), 0)
        FROM PetGrowthLog l
        WHERE l.userId = :userId AND l.createdAt >= :start AND l.createdAt < :end
        """)
    int sumDeltaInRange(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
}
