package com.vori.backend.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserIdAndYearMonth(Long userId, String yearMonth);

    List<Goal> findByUserIdAndCategoryId(Long userId, Long categoryId);

    List<Goal> findByUserIdAndYearMonthAndStatus(Long userId, String yearMonth, GoalStatus status);

    /**
     * 같은 달·같은 카테고리 목표가 이미 있는지.
     *
     * category_id 가 NULL 인 "그 달 전체 목표" 는 DB UNIQUE 로 못 막는다 —
     * MySQL 은 UNIQUE 컬럼의 NULL 중복을 허용하기 때문. 그래서 IsNull 전용 메서드를 따로 두고
     * 서비스에서 검사한다. (파생 쿼리로 categoryId=null 을 넘기면 `= NULL` 이 되어 아무것도 안 걸린다)
     */
    boolean existsByUserIdAndYearMonthAndCategoryId(Long userId, String yearMonth, Long categoryId);

    boolean existsByUserIdAndYearMonthAndCategoryIdIsNull(Long userId, String yearMonth);
}
