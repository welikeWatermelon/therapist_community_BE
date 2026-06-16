package com.therapyCommunity_Vol1.backend.jobpost.repository;

import com.therapyCommunity_Vol1.backend.jobpost.domain.EmploymentType;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPost;
import com.therapyCommunity_Vol1.backend.jobpost.domain.Region;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JobPostRepository extends JpaRepository<JobPost, Long> {

    @EntityGraph(attributePaths = "author")
    Optional<JobPost> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT j FROM JobPost j
            WHERE j.deletedAt IS NULL
              AND j.closedManually = false
              AND j.deadlineDate >= :today
              AND (:therapyArea IS NULL OR j.therapyArea = :therapyArea)
              AND (:region IS NULL OR j.region = :region)
              AND (:employmentType IS NULL OR j.employmentType = :employmentType)
              AND (:cursorDeadline IS NULL
                   OR j.deadlineDate > :cursorDeadline
                   OR (j.deadlineDate = :cursorDeadline AND j.id > :cursorId))
            ORDER BY j.deadlineDate ASC, j.id ASC
            """)
    List<JobPost> findOpenFeed(
            @Param("today") LocalDate today,
            @Param("therapyArea") TherapyArea therapyArea,
            @Param("region") Region region,
            @Param("employmentType") EmploymentType employmentType,
            @Param("cursorDeadline") LocalDate cursorDeadline,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT j FROM JobPost j
            WHERE j.deletedAt IS NULL
              AND (j.closedManually = true OR j.deadlineDate < :today)
              AND (:therapyArea IS NULL OR j.therapyArea = :therapyArea)
              AND (:region IS NULL OR j.region = :region)
              AND (:employmentType IS NULL OR j.employmentType = :employmentType)
              AND (:cursorDeadline IS NULL
                   OR j.deadlineDate < :cursorDeadline
                   OR (j.deadlineDate = :cursorDeadline AND j.id < :cursorId))
            ORDER BY j.deadlineDate DESC, j.id DESC
            """)
    List<JobPost> findClosedFeed(
            @Param("today") LocalDate today,
            @Param("therapyArea") TherapyArea therapyArea,
            @Param("region") Region region,
            @Param("employmentType") EmploymentType employmentType,
            @Param("cursorDeadline") LocalDate cursorDeadline,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
