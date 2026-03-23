package com.therapyCommunity_Vol1.backend.notification.controller;

import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.notification.dto.NotificationListResponse;
import com.therapyCommunity_Vol1.backend.notification.dto.UnreadCountResponse;
import com.therapyCommunity_Vol1.backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationListResponse> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        NotificationListResponse response = notificationService.getNotifications(
                userDetails.getUser().getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UnreadCountResponse response = notificationService.getUnreadCount(userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        notificationService.markAsRead(userDetails.getUser().getId(), id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAllAsRead(userDetails.getUser().getId());
        return ResponseEntity.ok().build();
    }
}
