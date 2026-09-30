package com.vori.backend.attendance;
import java.time.LocalDate;
public record AttendanceResponse(LocalDate date, boolean checkedIn, boolean itemAwarded, StatItemResponse awardedItem, int streakCount, boolean streakBonus) {}
