package com.therapyCommunity_Vol1.backend.jobpost.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "job_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "organization_name", nullable = false, length = 100)
    private String organizationName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "therapy_area", nullable = false, length = 50)
    private TherapyArea therapyArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Region region;

    @Column(name = "salary_text", length = 100)
    private String salaryText;

    @Column(columnDefinition = "TEXT")
    private String qualification;

    @Column(columnDefinition = "TEXT")
    private String preferred;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "deadline_date", nullable = false)
    private LocalDate deadlineDate;

    @Column(name = "closed_manually", nullable = false)
    private boolean closedManually = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private JobPost(User author, String organizationName, String content,
                    TherapyArea therapyArea, EmploymentType employmentType, Region region,
                    String salaryText, String qualification, String preferred,
                    String sourceUrl, LocalDate deadlineDate) {
        this.author = author;
        this.organizationName = organizationName;
        this.content = content;
        this.therapyArea = therapyArea;
        this.employmentType = employmentType;
        this.region = region;
        this.salaryText = salaryText;
        this.qualification = qualification;
        this.preferred = preferred;
        this.sourceUrl = sourceUrl;
        this.deadlineDate = deadlineDate;
        this.closedManually = false;
    }

    public static JobPost create(User author, String organizationName, String content,
                                 TherapyArea therapyArea, EmploymentType employmentType, Region region,
                                 String salaryText, String qualification, String preferred,
                                 String sourceUrl, LocalDate deadlineDate) {
        return new JobPost(author, organizationName, content, therapyArea, employmentType, region,
                salaryText, qualification, preferred, sourceUrl, deadlineDate);
    }

    public void update(String organizationName, String content,
                       TherapyArea therapyArea, EmploymentType employmentType, Region region,
                       String salaryText, String qualification, String preferred,
                       String sourceUrl, LocalDate deadlineDate) {
        this.organizationName = organizationName;
        this.content = content;
        this.therapyArea = therapyArea;
        this.employmentType = employmentType;
        this.region = region;
        this.salaryText = salaryText;
        this.qualification = qualification;
        this.preferred = preferred;
        this.sourceUrl = sourceUrl;
        this.deadlineDate = deadlineDate;
    }

    public void closeManually() {
        this.closedManually = true;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isAuthor(Long userId) {
        return author.getId().equals(userId);
    }

    /**
     * 상시모집(마감 없음)을 나타내는 sentinel 마감일. 이 값이면 마감으로 넘어가지 않고 항상 OPEN이며,
     * 응답의 alwaysOpen=true 로 파생된다. 프론트는 이 플래그로 "상시모집" 렌더 + D-day 숨김 처리.
     */
    public static final LocalDate ALWAYS_OPEN_DEADLINE = LocalDate.of(9999, 12, 31);

    public JobPostStatus deriveStatus(LocalDate today) {
        if (closedManually || deadlineDate.isBefore(today)) {
            return JobPostStatus.CLOSED;
        }
        return JobPostStatus.OPEN;
    }

    public boolean isAlwaysOpen() {
        return ALWAYS_OPEN_DEADLINE.equals(deadlineDate);
    }

    public long dDay(LocalDate today) {
        return ChronoUnit.DAYS.between(today, deadlineDate);
    }
}
