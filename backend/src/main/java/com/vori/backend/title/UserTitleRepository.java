package com.vori.backend.title;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserTitleRepository extends JpaRepository<UserTitle, Long> {

    /**
     * 보유 칭호 + 마스터(titles)를 한 번에 읽는다. title 이 LAZY 라 그냥 두면 ThemeService 의
     * 해금 판정과 TitleService 목록이 칭호 수만큼 SELECT 를 더 날린다.
     */
    @Query("SELECT ut FROM UserTitle ut JOIN FETCH ut.title WHERE ut.userId = :userId")
    List<UserTitle> findByUserId(@Param("userId") Long userId);

    Optional<UserTitle> findByUserIdAndTitleId(Long userId, Long titleId);
}
