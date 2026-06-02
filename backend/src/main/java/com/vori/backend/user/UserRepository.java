package com.vori.backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByRole(Role role, Pageable pageable);

    // ───── 어드민 대시보드 집계 ─────

    long countByRole(Role role);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    @Query("select coalesce(sum(u.totalSaved), 0) from User u")
    long sumTotalSaved();

    /**
     * since 이후 가입자를 일별로 집계 (MySQL 전용). 결과는 [yyyy-MM-dd, count] 행.
     * 가입 0건인 날은 행 자체가 빠지므로, 서비스에서 빈 날을 0으로 채운다.
     */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS d, COUNT(*) AS c " +
            "FROM users WHERE created_at >= :since GROUP BY d ORDER BY d",
            nativeQuery = true)
    List<Object[]> countSignupsByDaySince(@Param("since") LocalDateTime since);
}
