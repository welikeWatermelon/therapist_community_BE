package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.dto.PostImageResponse;
import com.therapyCommunity_Vol1.backend.post.service.PostImageService;
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

import java.util.List;

@Tag(name = "게시글 이미지", description = "게시글 이미지 업로드, 목록 조회, 다운로드")
@RestController
@RequestMapping("/api/v1/posts/{postId}/images")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PostImageController {

    private final PostImageService postImageService;

    @Operation(summary = "이미지 업로드", description = "jpg/png/webp, 5MB 이하")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PostImageResponse>> uploadImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @RequestPart("file") MultipartFile file
    ) {
        PostImageResponse response = postImageService.uploadImage(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId,
                file
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "이미지 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PostImageResponse>>> getImages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        List<PostImageResponse> response = postImageService.getImages(postId, userDetails.getUserRole());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "이미지 다운로드")
    @GetMapping("/{imageId}")
    public ResponseEntity<Resource> downloadImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long imageId
    ) {
        StoredFileResource storedFile = postImageService.loadImage(postId, imageId, userDetails.getUserRole());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storedFile.getContentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(storedFile.getOriginalFilename())
                                .build()
                                .toString()
                )
                .body(storedFile.getResource());
    }

    @Operation(summary = "이미지 삭제", description = "작성자 또는 관리자만 삭제 가능")
    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long imageId
    ) {
        postImageService.deleteImage(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId,
                imageId
        );
        return ResponseEntity.noContent().build();
    }
}
