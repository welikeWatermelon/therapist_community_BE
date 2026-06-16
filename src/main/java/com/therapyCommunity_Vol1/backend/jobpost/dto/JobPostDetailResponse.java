package com.therapyCommunity_Vol1.backend.jobpost.dto;

import com.therapyCommunity_Vol1.backend.jobpost.domain.EmploymentType;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPost;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPostStatus;
import com.therapyCommunity_Vol1.backend.jobpost.domain.Region;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class JobPostDetailResponse {

    private Long id;
    private String title;
    private String organizationName;
    private String content;
    private TherapyArea therapyArea;
    private String therapyAreaLabel;
    private EmploymentType employmentType;
    private String employmentTypeLabel;
    private Region region;
    private String regionLabel;
    private String salaryText;
    private String qualification;
    private String preferred;
    private String sourceUrl;
    private LocalDate deadlineDate;
    private long dDay;
    private JobPostStatus status;
    private Long authorId;
    private String authorNickname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean canEdit;
    private boolean canClose;

    public static JobPostDetailResponse from(JobPost post, LocalDate today,
                                             Long currentUserId, UserRole currentUserRole) {
        boolean editable = currentUserRole == UserRole.ADMIN
                || (currentUserId != null && post.isAuthor(currentUserId));
        JobPostStatus status = post.deriveStatus(today);
        boolean closable = editable && status == JobPostStatus.OPEN;
        return new JobPostDetailResponse(
                post.getId(),
                post.getOrganizationName() + " 채용공고",
                post.getOrganizationName(),
                post.getContent(),
                post.getTherapyArea(),
                post.getTherapyArea().getDescription(),
                post.getEmploymentType(),
                post.getEmploymentType().getDescription(),
                post.getRegion(),
                post.getRegion().getDescription(),
                post.getSalaryText(),
                post.getQualification(),
                post.getPreferred(),
                post.getSourceUrl(),
                post.getDeadlineDate(),
                post.dDay(today),
                status,
                post.getAuthor().getId(),
                post.getAuthor().getNickname(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                editable,
                closable
        );
    }
}
