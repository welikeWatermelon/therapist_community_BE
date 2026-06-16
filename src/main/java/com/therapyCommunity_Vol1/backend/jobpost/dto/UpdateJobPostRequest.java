package com.therapyCommunity_Vol1.backend.jobpost.dto;

import com.therapyCommunity_Vol1.backend.jobpost.domain.EmploymentType;
import com.therapyCommunity_Vol1.backend.jobpost.domain.Region;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateJobPostRequest {

    @NotBlank(message = "기관명은 필수입니다")
    @Size(max = 100, message = "기관명은 100자를 초과할 수 없습니다")
    private String organizationName;

    @NotBlank(message = "공고 내용은 필수입니다")
    private String content;

    @NotNull(message = "분야는 필수입니다")
    private TherapyArea therapyArea;

    @NotNull(message = "고용형태는 필수입니다")
    private EmploymentType employmentType;

    @NotNull(message = "지역은 필수입니다")
    private Region region;

    @Size(max = 100, message = "급여조건은 100자를 초과할 수 없습니다")
    private String salaryText;

    private String qualification;

    private String preferred;

    @NotBlank(message = "원문 URL은 필수입니다")
    @URL(message = "원문 URL 형식이 올바르지 않습니다")
    @Size(max = 500, message = "원문 URL은 500자를 초과할 수 없습니다")
    private String sourceUrl;

    @NotNull(message = "마감일은 필수입니다")
    private LocalDate deadlineDate;
}
