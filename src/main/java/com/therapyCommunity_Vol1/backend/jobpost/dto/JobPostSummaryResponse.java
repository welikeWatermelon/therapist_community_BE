package com.therapyCommunity_Vol1.backend.jobpost.dto;

import com.therapyCommunity_Vol1.backend.jobpost.domain.EmploymentType;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPost;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPostStatus;
import com.therapyCommunity_Vol1.backend.jobpost.domain.Region;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class JobPostSummaryResponse {

    private Long id;
    private String title;
    private String organizationName;
    private TherapyArea therapyArea;
    private String therapyAreaLabel;
    private EmploymentType employmentType;
    private String employmentTypeLabel;
    private Region region;
    private String regionLabel;
    private String salaryText;
    private LocalDate deadlineDate;
    private long dDay;
    private boolean alwaysOpen;
    private JobPostStatus status;
    private LocalDateTime createdAt;

    public static JobPostSummaryResponse from(JobPost post, LocalDate today) {
        return new JobPostSummaryResponse(
                post.getId(),
                post.getOrganizationName() + " 채용공고",
                post.getOrganizationName(),
                post.getTherapyArea(),
                post.getTherapyArea().getDescription(),
                post.getEmploymentType(),
                post.getEmploymentType().getDescription(),
                post.getRegion(),
                post.getRegion().getDescription(),
                post.getSalaryText(),
                post.getDeadlineDate(),
                post.dDay(today),
                post.isAlwaysOpen(),
                post.deriveStatus(today),
                post.getCreatedAt()
        );
    }
}
