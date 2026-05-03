package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.post.dto.PostAttachmentResponse;
import com.therapyCommunity_Vol1.backend.post.service.PostAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "첨부파일", description = "게시글 첨부파일 업로드, 다운로드, 삭제")
@RestController
@RequestMapping("/api/v1/posts/{postId}/attachments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PostAttachmentController {

    private final PostAttachmentService postAttachmentService;

    @Operation(
            summary = "첨부파일 업로드 (deprecated)",
            description = "presigned PUT 흐름(/uploads/init + /uploads/confirm)을 사용하세요. 후속 PR에서 제거 예정. 파일 업로드 시 게시글 postType 이 RESOURCE 로 자동 변경",
            deprecated = true
    )
    @Deprecated
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PostAttachmentResponse>> uploadAttachment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @RequestPart("file") MultipartFile file
    ) {
        PostAttachmentResponse response = postAttachmentService.uploadAttachment(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId,
                file
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "첨부파일 삭제", description = "마지막 첨부파일 삭제 시 postType이 COMMUNITY로 롤백")
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long attachmentId
    ) {
        postAttachmentService.deleteAttachment(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId,
                attachmentId
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "첨부파일 다운로드", description = "다운로드 이력 자동 기록")
    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long attachmentId
    ) {
        StoredFileResource storedFile = postAttachmentService.downloadAttachment(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId,
                attachmentId
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storedFile.getContentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(storedFile.getOriginalFilename())
                                .build()
                                .toString()
                )
                .body(storedFile.getResource());
    }
}
