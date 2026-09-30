package com.vori.backend.attendance;
import com.vori.backend.common.StatType;
import java.time.LocalDate;
public record AttendanceHistoryResponse(LocalDate date, boolean itemAwarded, String rewardName, StatType rewardStatType, Integer rewardStatDelta, int streakCount, boolean streakBonus) {
    static AttendanceHistoryResponse from(AttendanceCheckin checkin) { return new AttendanceHistoryResponse(checkin.getCheckedInDate(), checkin.isItemAwarded(), checkin.getRewardName(), checkin.getRewardStatType(), checkin.getRewardStatDelta(), checkin.getStreakCount(), checkin.isStreakBonus()); }
}
