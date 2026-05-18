package com.vori.backend.stats;

import com.vori.backend.common.StatType;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** user_stat_stats 의 복합 PK (user_id, stat_type). @IdClass 로 UserStatStats 가 참조. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserStatStatsId implements Serializable {
    private Long userId;
    private StatType statType;
}
