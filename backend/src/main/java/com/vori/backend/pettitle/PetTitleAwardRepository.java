package com.vori.backend.pettitle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PetTitleAwardRepository extends JpaRepository<PetTitleAward, Long> {

    /** 펫들의 칭호 + 마스터를 한 번에 읽는다 — 펫 목록 응답에서 펫마다 따로 조회하지 않게. */
    @Query("SELECT a FROM PetTitleAward a JOIN FETCH a.title WHERE a.petId IN :petIds ORDER BY a.acquiredAt, a.id")
    List<PetTitleAward> findByPetIdIn(@Param("petIds") Collection<Long> petIds);

    // ───── 업적(유저) 지표 — TitleService.collect() 가 쓴다 ─────

    /** 유저의 모든 펫이 딴 칭호 합계. */
    @Query("SELECT COUNT(a) FROM PetTitleAward a, Pet p WHERE p.id = a.petId AND p.userId = :userId")
    long countByUserId(@Param("userId") Long userId);

    /** 한 번이라도 딴 서로 다른 공개 펫 칭호 수. 히든 칭호는 "칭호 도감" 업적에 넣지 않는다. */
    @Query("""
        SELECT COUNT(DISTINCT a.title.id) FROM PetTitleAward a, Pet p
        WHERE p.id = a.petId AND p.userId = :userId AND a.title.hidden = false
        """)
    long countPublicKindsByUserId(@Param("userId") Long userId);

    /** 딴 히든 펫 칭호 수. */
    @Query("""
        SELECT COUNT(a) FROM PetTitleAward a, Pet p
        WHERE p.id = a.petId AND p.userId = :userId AND a.title.hidden = true
        """)
    long countHiddenByUserId(@Param("userId") Long userId);

    /** 펫별 칭호 수, 많은 순. 첫 값이 "한 펫이 딴 칭호 최대 개수". */
    @Query("""
        SELECT COUNT(a) FROM PetTitleAward a, Pet p
        WHERE p.id = a.petId AND p.userId = :userId
        GROUP BY a.petId ORDER BY COUNT(a) DESC
        """)
    List<Long> countPerPetByUserId(@Param("userId") Long userId);
}
