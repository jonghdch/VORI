package com.vori.backend.theme;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThemeMasterRepository extends JpaRepository<ThemeMaster, Long> {

    Optional<ThemeMaster> findByName(String name);

    /**
     * 이 칭호가 해제하는 테마. 현재 시드는 칭호 1개당 테마 1개지만 List 로 받는다 —
     * Optional 이면 나중에 같은 칭호에 테마를 하나 더 붙이는 순간 칭호 지급·조회가
     * IncorrectResultSizeDataAccessException 으로 터진다.
     */
    List<ThemeMaster> findByUnlockTitleName(String unlockTitleName);
}
