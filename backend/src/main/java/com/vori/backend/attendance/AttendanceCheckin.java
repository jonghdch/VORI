package com.vori.backend.attendance;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.vori.backend.common.StatType;

@Entity
@Table(name = "attendance_checkins", uniqueConstraints = @UniqueConstraint(name = "uk_attendance_user_date", columnNames = {"user_id", "checked_in_date"}))
@Getter @Builder @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class AttendanceCheckin {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "checked_in_date", nullable = false) private LocalDate checkedInDate;
    @Column(name = "item_awarded", nullable = false) private boolean itemAwarded;
    @Column(name = "reward_name") private String rewardName;
    @Enumerated(EnumType.STRING) @Column(name = "reward_stat_type") private StatType rewardStatType;
    @Column(name = "reward_stat_delta") private Integer rewardStatDelta;
    @Column(name = "streak_count", nullable = false) private Integer streakCount;
    @Column(name = "streak_bonus", nullable = false) private boolean streakBonus;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
