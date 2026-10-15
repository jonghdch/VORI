package com.vori.backend.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByEmail(String email);

    /**
     * 게임머니 증감처럼 "읽고 → 계산해서 → 쓰는" 경로 전용 조회 (SELECT ... FOR UPDATE).
     * 일반 findById 로 하면 알 구매를 더블클릭했을 때 두 트랜잭션이 같은 잔액을 읽어
     * 둘 다 통과 → 잔액이 실제보다 많이 빠진다. 행 잠금으로 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Page<User> findByRole(Role role, Pageable pageable);

    /** 역할별 전체 목록 — 관리자 알림 대상(AccountDeletionService) */
    List<User> findAllByRole(Role role);

    // ───── 탈퇴 유예 (AccountDeletionService) ─────

    /** 탈퇴 대기 중인 계정 id. 배치 작업(알림·AI 코멘트·월간 리포트)이 대상에서 뺀다. */
    @Query("select u.id from User u where u.deletionRequestedAt is not null")
    Set<Long> findIdsPendingDeletion();

    /** 유예 기간이 끝나 영구 삭제할 계정 id. */
    @Query("select u.id from User u where u.deletionRequestedAt is not null and u.deletionRequestedAt <= :cutoff")
    List<Long> findIdsDeletionRequestedBefore(@Param("cutoff") LocalDateTime cutoff);

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
