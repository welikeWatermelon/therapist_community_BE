package com.therapyCommunity_Vol1.backend.notification.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.notification.dto.NotificationResponse;
import com.therapyCommunity_Vol1.backend.notification.dto.UnreadCountResponse;
import com.therapyCommunity_Vol1.backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "알림", description = "SSE 실시간 알림 + 알림 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "SSE 알림 구독", description = "text/event-stream으로 실시간 알림 수신. Last-Event-ID 헤더로 유실 이벤트 복구")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "") String lastEventId
    ) {
        return notificationService.subscribe(userDetails.getUser().getId(), lastEventId);
    }

    @Operation(summary = "알림 목록 조회", description = "페이징 지원. 최신순 정렬")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<NotificationResponse> response =
                notificationService.getNotifications(userDetails.getUser().getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "미읽은 알림 수")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UnreadCountResponse response = notificationService.getUnreadCount(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "단건 읽음 처리")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long notificationId
    ) {
        notificationService.markAsRead(userDetails.getUser().getId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "전체 읽음 처리")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        notificationService.markAllAsRead(userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "알림 삭제")
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long notificationId
    ) {
        notificationService.deleteNotification(userDetails.getUser().getId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
