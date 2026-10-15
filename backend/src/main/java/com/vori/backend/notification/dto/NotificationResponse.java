package com.vori.backend.notification.dto;

import com.vori.backend.notification.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String body,
        String link,
        LocalDateTime createdAt,
        boolean read
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
                n.getLink(), n.getCreatedAt(), n.getReadAt() != null);
    }
}
