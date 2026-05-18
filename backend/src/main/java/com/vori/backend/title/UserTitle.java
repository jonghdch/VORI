package com.vori.backend.title;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_titles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "unlock_condition", columnDefinition = "JSON")
    private String unlockCondition;

    @Column(name = "unlocks_theme_id")
    private Long unlocksThemeId;

    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;
}
