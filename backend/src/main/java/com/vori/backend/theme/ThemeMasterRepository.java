package com.vori.backend.theme;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ThemeMasterRepository extends JpaRepository<ThemeMaster, Long> {

    Optional<ThemeMaster> findByName(String name);

    /**
     * 전체 테마 + 해금 칭호를 한 번에. unlockTitle 이 LAZY 라 그냥 두면 응답에 칭호 이름을
     * 실을 때 테마 수만큼 SELECT 가 더 나간다.
     */
    @Override
    @Query("SELECT t FROM ThemeMaster t LEFT JOIN FETCH t.unlockTitle")
    List<ThemeMaster> findAll();

    /**
     * 이 칭호가 해제하는 테마. 현재 시드는 칭호 1개당 테마 1개지만 List 로 받는다 —
     * Optional 이면 나중에 같은 칭호에 테마를 하나 더 붙이는 순간 칭호 지급·조회가
     * IncorrectResultSizeDataAccessException 으로 터진다.
     */
    // 파생 쿼리로 두면 Spring Data 가 엔티티의 편의 getter(getUnlockTitleId)를 실제 속성으로
    // 오해해 unlockTitle.id 로 풀지 못한다. 경로를 JPQL 로 명시한다.
    @Query("SELECT t FROM ThemeMaster t WHERE t.unlockTitle.id = :titleId")
    List<ThemeMaster> findByUnlockTitleId(@Param("titleId") Long titleId);
}
