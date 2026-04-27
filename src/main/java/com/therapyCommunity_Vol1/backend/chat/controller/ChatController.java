package com.therapyCommunity_Vol1.backend.chat.controller;

import com.therapyCommunity_Vol1.backend.chat.dto.*;
import com.therapyCommunity_Vol1.backend.chat.service.ChatService;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "쪽지", description = "1:1 쪽지(채팅) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/conversations")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "대화 시작", description = "상대방과의 대화를 시작하고 첫 메시지를 전송합니다. 기존 대화가 있으면 메시지만 추가합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateConversationRequest request
    ) {
        ChatService.ConversationWithCreatedFlag result =
                chatService.createConversation(userDetails.getUser().getId(), request);

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(result.conversation()));
    }

    @Operation(summary = "내 대화 목록", description = "참여 중인 대화 목록을 최신 메시지 순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ConversationResponse>>> getConversations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<ConversationResponse> response =
                chatService.getConversations(userDetails.getUser().getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "메시지 목록", description = "특정 대화의 메시지를 커서 기반으로 조회합니다. before 파라미터로 이전 메시지를 로드합니다.")
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<CursorPageResponse<MessageResponse>>> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "50") int size
    ) {
        CursorPageResponse<MessageResponse> response =
                chatService.getMessages(userDetails.getUser().getId(), conversationId, before, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "메시지 전송", description = "기존 대화에 메시지를 전송합니다.")
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        MessageResponse response =
                chatService.sendMessage(userDetails.getUser().getId(), conversationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "읽음 처리", description = "대화 내 상대방 메시지를 모두 읽음 처리합니다.")
    @PatchMapping("/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long conversationId
    ) {
        chatService.markAsRead(userDetails.getUser().getId(), conversationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "안읽은 대화 수", description = "읽지 않은 메시지가 있는 대화 수를 반환합니다.")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadConversationCountResponse>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UnreadConversationCountResponse response =
                chatService.getUnreadCount(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
