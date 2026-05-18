package com.vori.backend.stats;

import com.vori.backend.common.StatType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserStatStatsRepository extends JpaRepository<UserStatStats, UserStatStatsId> {

    Optional<UserStatStats> findByUserIdAndStatType(Long userId, StatType statType);

    List<UserStatStats> findByUserId(Long userId);
}
