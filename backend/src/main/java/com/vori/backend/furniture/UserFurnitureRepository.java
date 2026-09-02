package com.vori.backend.furniture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserFurnitureRepository extends JpaRepository<UserFurniture, Long> {

    List<UserFurniture> findByUserId(Long userId);

    List<UserFurniture> findByUserIdAndPositionXIsNotNullAndPositionYIsNotNull(Long userId);

    /** 그 좌표에 이미 배치된 가구가 있는지 — 겹쳐 놓기 방지. */
    boolean existsByUserIdAndPositionXAndPositionY(Long userId, Short positionX, Short positionY);
}
