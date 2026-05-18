package com.vori.backend.stats;

import com.vori.backend.common.StatType;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserStatStatsId implements Serializable {
    private Long userId;
    private StatType statType;
}
