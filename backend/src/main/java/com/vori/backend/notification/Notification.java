package com.vori.backend.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 헤더 알림 목록의 한 줄. readAt 이 NULL 이면 안 읽음. */
@Entity
@Table(name = "notifications")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 255)
    private String body;

    /** 누르면 갈 화면 경로 (/report?month=2026-09 등). */
    @Column(length = 255)
    private String link;

    /** 같은 일을 두 번 알리지 않기 위한 키. (user_id, dedupe_key) UNIQUE. */
    @Column(name = "dedupe_key", length = 100)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public void markRead(LocalDateTime at) {
        if (readAt == null) readAt = at;
    }
}
