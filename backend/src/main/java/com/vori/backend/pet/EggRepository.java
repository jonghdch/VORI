package com.vori.backend.pet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EggRepository extends JpaRepository<Egg, Long> {

    List<Egg> findByUserIdAndOpenedAtIsNull(Long userId);

    List<Egg> findByUserIdOrderByPurchasedAtDesc(Long userId);

    /**
     * 개봉 전용 조회 (SELECT ... FOR UPDATE). 같은 알에 개봉 요청이 동시에 오면
     * 뒤 요청이 잠금 해제를 기다렸다가 opened_at 이 채워진 걸 보고 409 로 떨어진다.
     * (gacha_pulls.egg_id UNIQUE 가 최종 방어선이지만, 락으로 예외 대신 정상 응답을 만든다)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Egg e WHERE e.id = :id")
    Optional<Egg> findByIdForUpdate(@Param("id") Long id);
}
