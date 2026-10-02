package com.vori.backend.notification;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 헤더 알림. 인증 필요, 본인 알림만. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** GET /api/notifications — 최근 30개, 최신순. */
    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.list(principal.getId());
    }

    /** GET /api/notifications/unread-count — 종 아이콘 점 표시용. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("count", notificationService.unreadCount(principal.getId()));
    }

    /** POST /api/notifications/{id}/read */
    @PostMapping("/{id}/read")
    public void read(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        notificationService.markRead(principal.getId(), id);
    }

    /** DELETE /api/notifications — 본인 알림 전체 삭제. */
    @DeleteMapping
    public void deleteAll(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.deleteAll(principal.getId());
    }

    /** POST /api/notifications/read-all */
    @PostMapping("/read-all")
    public void readAll(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllRead(principal.getId());
    }
}
