package com.therapyCommunity_Vol1.backend.user.controller;

import com.therapyCommunity_Vol1.backend.auth.support.RefreshTokenCookieManager;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.dto.UpdateProfileRequest;
import com.therapyCommunity_Vol1.backend.user.mypage.MyPageFacade;
import com.therapyCommunity_Vol1.backend.user.mypage.dto.MyCommentResponse;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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

import java.util.Map;

@Tag(name = "마이페이지", description = "내 정보, 내 게시글/댓글, 프로필 수정, 회원 탈퇴")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserController {

    private final MyPageFacade myPageFacade;
    private final UserService userService;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 유저 정보 + 치료사 인증 상태")
    @GetMapping
    public ResponseEntity<ApiResponse<CurrentUserResponse>> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CurrentUserResponse response = myPageFacade.getCurrentUser(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "내가 쓴 게시글", description = "내가 작성한 게시글 목록 (최신순, 페이징)")
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<PagedResponse<TherapyPostSummaryResponse>>> getMyPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PagedResponse<TherapyPostSummaryResponse> response = myPageFacade.getMyPosts(userDetails.getUser().getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "내가 쓴 댓글", description = "내가 작성한 댓글 목록 (삭제된 댓글 포함, content 마스킹)")
    @GetMapping("/comments")
    public ResponseEntity<ApiResponse<PagedResponse<MyCommentResponse>>> getMyComments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PagedResponse<MyCommentResponse> response = myPageFacade.getMyComments(userDetails.getUser().getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "프로필 이미지 업로드", description = "이미지 파일 업로드 → URL 반환. jpg/png/webp, 5MB 이하")
    @PostMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadProfileImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("file") MultipartFile file
    ) {
        String imageUrl = myPageFacade.uploadProfileImage(userDetails.getUser().getId(), file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("profileImageUrl", imageUrl)));
    }

    @Operation(summary = "프로필 이미지 조회", description = "인증 불필요. 프로필 이미지 파일 반환", security = {})
    @GetMapping("/profile-image/profile-images/{filename}")
    public ResponseEntity<Resource> getProfileImage(@PathVariable String filename) {
        StoredFileResource storedFile = userService.loadProfileImage(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storedFile.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(storedFile.getOriginalFilename()).build().toString())
                .body(storedFile.getResource());
    }

    @Operation(summary = "프로필 수정", description = "닉네임(2~20자), 프로필 이미지 URL 변경. null이면 기존 값 유지")
    @PatchMapping
    public ResponseEntity<ApiResponse<CurrentUserResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        CurrentUserResponse response = myPageFacade.updateProfile(userDetails.getUser().getId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "회원 탈퇴", description = "계정 soft delete + 토큰 전체 폐기 + 쿠키 만료")
    @DeleteMapping
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletResponse httpServletResponse
    ) {
        myPageFacade.withdraw(userDetails.getUser().getId());
        refreshTokenCookieManager.expireRefreshTokenCookie(httpServletResponse);
        return ResponseEntity.noContent().build();
    }
}
