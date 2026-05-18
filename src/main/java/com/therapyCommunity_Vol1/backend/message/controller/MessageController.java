package com.therapyCommunity_Vol1.backend.message.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.message.dto.*;
import com.therapyCommunity_Vol1.backend.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "쪽지", description = "1:1 쪽지 발송/조회/삭제")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "쪽지 발송")
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MessageSendRequest request
    ) {
        MessageResponse response = messageService.sendMessage(
                userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "관리자 공지 쪽지 발송", description = "targetRole이 null이면 전체 발송")
    @PostMapping("/admin/messages/broadcast")
    public ResponseEntity<ApiResponse<Void>> broadcastMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody BroadcastMessageRequest request
    ) {
        messageService.broadcastMessage(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "받은 쪽지함", description = "받은 쪽지 목록 (페이징)")
    @GetMapping("/me/messages/received")
    public ResponseEntity<ApiResponse<PagedResponse<MessageResponse>>> getReceivedMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        PagedResponse<MessageResponse> response = messageService.getReceivedMessages(
                userDetails.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "보낸 쪽지함", description = "보낸 쪽지 목록 (페이징)")
    @GetMapping("/me/messages/sent")
    public ResponseEntity<ApiResponse<PagedResponse<MessageResponse>>> getSentMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        PagedResponse<MessageResponse> response = messageService.getSentMessages(
                userDetails.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "쪽지 상세 조회", description = "수신자가 조회하면 자동 읽음 처리")
    @GetMapping("/messages/{messageId}")
    public ResponseEntity<ApiResponse<MessageResponse>> getMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long messageId
    ) {
        MessageResponse response = messageService.getMessage(
                userDetails.getUserId(), messageId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "쪽지 삭제", description = "호출자 측에서만 삭제 (상대방에겐 유지)")
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long messageId
    ) {
        messageService.deleteMessage(userDetails.getUserId(), messageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "안읽은 쪽지 수")
    @GetMapping("/me/messages/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UnreadCountResponse response = messageService.getUnreadCount(
                userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
