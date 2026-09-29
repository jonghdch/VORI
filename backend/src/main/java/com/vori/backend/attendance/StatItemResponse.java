package com.vori.backend.attendance;
import com.vori.backend.common.StatType;
import java.time.LocalDateTime;
public record StatItemResponse(Long id, String name, StatType statType, int statDelta, LocalDateTime acquiredAt) {
    static StatItemResponse from(UserStatItem item) { return new StatItemResponse(item.getId(), item.getName(), item.getStatType(), item.getStatDelta(), item.getAcquiredAt()); }
}
