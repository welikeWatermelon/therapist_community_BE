package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.global.storage.StoredFileResource;
import com.therapyCommunity_Vol1.backend.post.dto.PostAttachmentResponse;
import com.therapyCommunity_Vol1.backend.post.service.PostAttachmentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@RestController
@RequestMapping("/api/v1/posts/{postId}/attachments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PostAttachmentController {

    private final PostAttachmentService postAttachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PostAttachmentResponse>> uploadAttachment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @RequestPart("file") MultipartFile file
    ) {
        PostAttachmentResponse response = postAttachmentService.uploadAttachment(
                userDetails.getUser().getId(),
                userDetails.getUser().getRole(),
                postId,
                file
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long attachmentId
    ) {
        StoredFileResource storedFile = postAttachmentService.downloadAttachment(
                userDetails.getUser().getId(),
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
