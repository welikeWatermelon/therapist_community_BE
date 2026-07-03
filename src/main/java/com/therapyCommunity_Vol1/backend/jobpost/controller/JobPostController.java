package com.therapyCommunity_Vol1.backend.jobpost.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.CursorPagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.jobpost.domain.EmploymentType;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPostStatus;
import com.therapyCommunity_Vol1.backend.jobpost.domain.Region;
import com.therapyCommunity_Vol1.backend.jobpost.dto.CreateJobPostRequest;
import com.therapyCommunity_Vol1.backend.jobpost.dto.JobPostDetailResponse;
import com.therapyCommunity_Vol1.backend.jobpost.dto.JobPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.jobpost.dto.UpdateJobPostRequest;
import com.therapyCommunity_Vol1.backend.jobpost.service.JobPostService;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "구인공고", description = "구인공고 게시판 CRUD + 목록/필터")
@RestController
@RequestMapping("/api/v1/job-posts")
@RequiredArgsConstructor
public class JobPostController {

    private final JobPostService jobPostService;

    @PostMapping
    public ResponseEntity<ApiResponse<JobPostDetailResponse>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateJobPostRequest request) {
        JobPostDetailResponse response = jobPostService.create(
                userDetails.getUserId(), userDetails.getUserRole(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/{jobPostId}")
    public ResponseEntity<ApiResponse<JobPostDetailResponse>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long jobPostId,
            @Valid @RequestBody UpdateJobPostRequest request) {
        JobPostDetailResponse response = jobPostService.update(
                userDetails.getUserId(), userDetails.getUserRole(), jobPostId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{jobPostId}/close")
    public ResponseEntity<ApiResponse<Void>> close(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long jobPostId) {
        jobPostService.close(userDetails.getUserId(), userDetails.getUserRole(), jobPostId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{jobPostId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long jobPostId) {
        jobPostService.delete(userDetails.getUserId(), userDetails.getUserRole(), jobPostId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CursorPagedResponse<JobPostSummaryResponse>>> getJobPosts(
            @RequestParam(required = false) JobPostStatus status,
            @RequestParam(required = false) TherapyArea therapyArea,
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        CursorPagedResponse<JobPostSummaryResponse> response = jobPostService.getJobPosts(
                status, therapyArea, region, employmentType, cursor, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{jobPostId}")
    public ResponseEntity<ApiResponse<JobPostDetailResponse>> getDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long jobPostId) {
        Long userId = (userDetails != null) ? userDetails.getUserId() : null;
        UserRole role = (userDetails != null) ? userDetails.getUserRole() : null;
        JobPostDetailResponse response = jobPostService.getDetail(jobPostId, userId, role);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
