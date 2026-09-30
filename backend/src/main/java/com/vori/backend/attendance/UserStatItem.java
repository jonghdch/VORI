package com.vori.backend.attendance;
import com.vori.backend.common.StatType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name = "user_stat_items")
@Getter @Builder @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class UserStatItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "stat_type", nullable = false) private StatType statType;
    @Column(name = "stat_delta", nullable = false) private Integer statDelta;
    @Column(name = "acquired_at", nullable = false) private LocalDateTime acquiredAt;
}
