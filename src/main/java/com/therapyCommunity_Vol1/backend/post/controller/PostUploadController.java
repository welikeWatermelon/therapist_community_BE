package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.dto.UploadConfirmRequest;
import com.therapyCommunity_Vol1.backend.post.dto.UploadConfirmResponse;
import com.therapyCommunity_Vol1.backend.post.dto.UploadInitRequest;
import com.therapyCommunity_Vol1.backend.post.dto.UploadInitResponse;
import com.therapyCommunity_Vol1.backend.post.service.upload.UploadConfirmService;
import com.therapyCommunity_Vol1.backend.post.service.upload.UploadInitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "게시글 업로드", description = "presigned PUT 기반 미디어 업로드 (init + confirm)")
@RestController
@RequestMapping("/api/v1/posts/{postId}/uploads")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PostUploadController {

    private final UploadInitService uploadInitService;
    private final UploadConfirmService uploadConfirmService;

    @Operation(summary = "업로드 init", description = "S3 presigned PUT URL 발급")
    @PostMapping("/init")
    public ResponseEntity<ApiResponse<UploadInitResponse>> init(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody UploadInitRequest request
    ) {
        UploadInitResponse response = uploadInitService.init(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId,
                request.getKind(),
                request.getOriginalFilename(),
                request.getContentType(),
                request.getSizeBytes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "업로드 confirm", description = "S3 PUT 완료 후 백엔드에 등록")
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<UploadConfirmResponse>> confirm(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody UploadConfirmRequest request
    ) {
        UploadConfirmResponse response = uploadConfirmService.confirm(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId,
                request.getKind(),
                request.getStoredKey(),
                request.getOriginalFilename()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
