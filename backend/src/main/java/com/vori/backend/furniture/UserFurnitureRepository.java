package com.vori.backend.furniture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserFurnitureRepository extends JpaRepository<UserFurniture, Long> {

    List<UserFurniture> findByUserId(Long userId);

    List<UserFurniture> findByUserIdAndPositionXIsNotNullAndPositionYIsNotNull(Long userId);
}
