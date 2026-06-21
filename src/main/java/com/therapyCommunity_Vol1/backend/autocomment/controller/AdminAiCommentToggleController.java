package com.therapyCommunity_Vol1.backend.autocomment.controller;

import com.therapyCommunity_Vol1.backend.autocomment.dto.AiCommentToggleRequest;
import com.therapyCommunity_Vol1.backend.autocomment.service.AiCommentToggleService;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 댓글 토글 (Admin)", description = "자동답글 기능 런타임 on/off")
@RestController
@RequestMapping("/api/v1/admin/ai-comment")
@RequiredArgsConstructor
public class AdminAiCommentToggleController {

    private final AiCommentToggleService toggleService;

    @Operation(summary = "자동답글 활성 상태 조회")
    @GetMapping("/enabled")
    public ResponseEntity<ApiResponse<Boolean>> getEnabled() {
        return ResponseEntity.ok(ApiResponse.success(toggleService.isEnabled()));
    }

    @Operation(summary = "자동답글 활성 토글", description = "재배포 없이 즉시 on/off. DB에 persist되어 재시작에도 유지된다.")
    @PutMapping("/enabled")
    public ResponseEntity<ApiResponse<Boolean>> setEnabled(@Valid @RequestBody AiCommentToggleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(toggleService.setEnabled(request.getEnabled())));
    }
}
