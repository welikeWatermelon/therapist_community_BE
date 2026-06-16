package com.therapyCommunity_Vol1.backend.jobpost.service;

import com.therapyCommunity_Vol1.backend.global.common.CursorPagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.jobpost.domain.EmploymentType;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPost;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPostStatus;
import com.therapyCommunity_Vol1.backend.jobpost.domain.Region;
import com.therapyCommunity_Vol1.backend.jobpost.dto.CreateJobPostRequest;
import com.therapyCommunity_Vol1.backend.jobpost.dto.JobPostCursor;
import com.therapyCommunity_Vol1.backend.jobpost.dto.JobPostDetailResponse;
import com.therapyCommunity_Vol1.backend.jobpost.dto.JobPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.jobpost.dto.UpdateJobPostRequest;
import com.therapyCommunity_Vol1.backend.jobpost.repository.JobPostRepository;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostService {

    private static final int FEED_MAX_SIZE = 50;

    private final JobPostRepository jobPostRepository;
    private final UserService userService;
    private final ResourceAccessValidator resourceAccessValidator;

    @Transactional
    public JobPostDetailResponse create(Long currentUserId, UserRole currentUserRole,
                                        CreateJobPostRequest request) {
        User author = userService.findById(currentUserId);
        JobPost saved = jobPostRepository.save(JobPost.create(
                author, request.getOrganizationName(), request.getContent(),
                request.getTherapyArea(), request.getEmploymentType(), request.getRegion(),
                request.getSalaryText(), request.getQualification(), request.getPreferred(),
                request.getSourceUrl(), request.getDeadlineDate()));
        return JobPostDetailResponse.from(saved, LocalDate.now(), currentUserId, currentUserRole);
    }

    @Transactional
    public JobPostDetailResponse update(Long currentUserId, UserRole currentUserRole,
                                        Long jobPostId, UpdateJobPostRequest request) {
        JobPost post = findActive(jobPostId);
        resourceAccessValidator.validateAuthorOrAdmin(
                post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.JOB_POST_ACCESS_DENIED);
        post.update(request.getOrganizationName(), request.getContent(),
                request.getTherapyArea(), request.getEmploymentType(), request.getRegion(),
                request.getSalaryText(), request.getQualification(), request.getPreferred(),
                request.getSourceUrl(), request.getDeadlineDate());
        return JobPostDetailResponse.from(post, LocalDate.now(), currentUserId, currentUserRole);
    }

    @Transactional
    public void close(Long currentUserId, UserRole currentUserRole, Long jobPostId) {
        JobPost post = findActive(jobPostId);
        resourceAccessValidator.validateAuthorOrAdmin(
                post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.JOB_POST_ACCESS_DENIED);
        post.closeManually();
    }

    @Transactional
    public void delete(Long currentUserId, UserRole currentUserRole, Long jobPostId) {
        JobPost post = findActive(jobPostId);
        resourceAccessValidator.validateAuthorOrAdmin(
                post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.JOB_POST_ACCESS_DENIED);
        post.softDelete();
    }

    public JobPostDetailResponse getDetail(Long jobPostId, Long currentUserId, UserRole currentUserRole) {
        JobPost post = findActive(jobPostId);
        return JobPostDetailResponse.from(post, LocalDate.now(), currentUserId, currentUserRole);
    }

    public CursorPagedResponse<JobPostSummaryResponse> getJobPosts(
            JobPostStatus status, TherapyArea therapyArea, Region region,
            EmploymentType employmentType, String cursor, int size) {
        LocalDate today = LocalDate.now();
        JobPostStatus effective = (status == null) ? JobPostStatus.OPEN : status;
        int pageSize = Math.min(Math.max(size, 1), FEED_MAX_SIZE);

        LocalDate cursorDeadline = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            JobPostCursor decoded = JobPostCursor.decode(cursor);
            cursorDeadline = decoded.deadlineDate();
            cursorId = decoded.id();
        }

        Pageable pageable = PageRequest.of(0, pageSize + 1);
        List<JobPost> rows = (effective == JobPostStatus.OPEN)
                ? jobPostRepository.findOpenFeed(today, therapyArea, region, employmentType, cursorDeadline, cursorId, pageable)
                : jobPostRepository.findClosedFeed(today, therapyArea, region, employmentType, cursorDeadline, cursorId, pageable);

        List<JobPostSummaryResponse> items = rows.stream()
                .map(j -> JobPostSummaryResponse.from(j, today))
                .toList();

        return CursorPagedResponse.of(items, pageSize,
                item -> new JobPostCursor(item.getDeadlineDate(), item.getId()).encode());
    }

    private JobPost findActive(Long jobPostId) {
        return jobPostRepository.findByIdAndDeletedAtIsNull(jobPostId)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POST_NOT_FOUND));
    }
}
