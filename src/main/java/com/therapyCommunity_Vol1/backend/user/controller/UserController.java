package com.therapyCommunity_Vol1.backend.user.controller;

import com.therapyCommunity_Vol1.backend.application.mypage.MyPageFacade;
import com.therapyCommunity_Vol1.backend.application.mypage.dto.MyCommentResponse;
import com.therapyCommunity_Vol1.backend.auth.support.RefreshTokenCookieManager;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.follow.dto.FollowCountResponse;
import com.therapyCommunity_Vol1.backend.follow.dto.FollowUserResponse;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.dto.UpdateProfileRequest;
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
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 유저 정보 + 치료사 인증 상태")
    @GetMapping
    public ResponseEntity<ApiResponse<CurrentUserResponse>> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CurrentUserResponse response = myPageFacade.getCurrentUser(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "내가 쓴 게시글", description = "내가 작성한 게시글 목록 (최신순, 페이징)")
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<PagedResponse<TherapyPostSummaryResponse>>> getMyPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) PostType postType
    ) {
        PagedResponse<TherapyPostSummaryResponse> response = myPageFacade.getMyPosts(userDetails.getUserId(), page, size, postType);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "내가 쓴 댓글", description = "내가 작성한 댓글 목록 (삭제된 댓글 포함, content 마스킹)")
    @GetMapping("/comments")
    public ResponseEntity<ApiResponse<PagedResponse<MyCommentResponse>>> getMyComments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PagedResponse<MyCommentResponse> response = myPageFacade.getMyComments(userDetails.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "프로필 이미지 업로드", description = "이미지 파일 업로드 → URL 반환. jpg/png/webp, 5MB 이하")
    @PostMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadProfileImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("file") MultipartFile file
    ) {
        String imageUrl = myPageFacade.uploadProfileImage(userDetails.getUserId(), file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("profileImageUrl", imageUrl)));
    }

    @Operation(summary = "프로필 이미지 조회", description = "인증 불필요. 프로필 이미지 파일 반환", security = {})
    @GetMapping("/profile-image/{filename}")
    public ResponseEntity<Resource> getProfileImage(@PathVariable String filename) {
        return serveProfileImage(filename);
    }

    /**
     * 구형 경로 호환용 매핑.
     * 이 PR 배포 이전의 응답이나 Redis 캐시(`CachedUser`)에 남아있을 수 있는
     * `/api/v1/me/profile-image/profile-images/{filename}` 형태의 URL을 계속 서비스하기 위함.
     * 제거 조건(ToDoListForAfterMvp.md 참조): (a) `CachedUser` TTL 완전 만료 후
     * (b) 1~2 릴리즈 관측 결과 구형 URL 요청이 더 이상 들어오지 않음이 확인되면 삭제.
     */
    @Deprecated
    @Operation(
            summary = "[DEPRECATED] 프로필 이미지 조회 — 구형 경로",
            description = "구형 URL 호환용. 신규 클라이언트는 /profile-image/{filename}를 사용할 것.",
            security = {}
    )
    @GetMapping("/profile-image/profile-images/{filename}")
    public ResponseEntity<Resource> getProfileImageLegacy(@PathVariable String filename) {
        return serveProfileImage(filename);
    }

    private ResponseEntity<Resource> serveProfileImage(String filename) {
        StoredFileResource storedFile = myPageFacade.loadProfileImage(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storedFile.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(storedFile.getOriginalFilename()).build().toString())
                .body(storedFile.getResource());
    }

    @Operation(summary = "내 팔로워 수/팔로잉 수", description = "본인의 팔로워/팔로잉 카운트 조회")
    @GetMapping("/follow-counts")
    public ResponseEntity<ApiResponse<FollowCountResponse>> getFollowCounts(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        FollowCountResponse response = myPageFacade.getFollowCounts(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "내 팔로워 목록", description = "나를 팔로우하는 사용자 목록 (페이징). 본인만 조회 가능")
    @GetMapping("/followers")
    public ResponseEntity<ApiResponse<PagedResponse<FollowUserResponse>>> getMyFollowers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PagedResponse<FollowUserResponse> response = myPageFacade.getMyFollowers(userDetails.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "내 팔로잉 목록", description = "내가 팔로우하는 치료사 목록 (페이징). 본인만 조회 가능")
    @GetMapping("/followings")
    public ResponseEntity<ApiResponse<PagedResponse<FollowUserResponse>>> getMyFollowings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PagedResponse<FollowUserResponse> response = myPageFacade.getMyFollowings(userDetails.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "프로필 수정", description = "닉네임(2~20자) 변경. null이면 기존 값 유지")
    @PatchMapping
    public ResponseEntity<ApiResponse<CurrentUserResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        CurrentUserResponse response = myPageFacade.updateProfile(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "회원 탈퇴", description = "계정 soft delete + 토큰 전체 폐기 + 쿠키 만료")
    @DeleteMapping
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletResponse httpServletResponse
    ) {
        myPageFacade.withdraw(userDetails.getUserId());
        refreshTokenCookieManager.expireRefreshTokenCookie(httpServletResponse);
        return ResponseEntity.noContent().build();
    }
}
