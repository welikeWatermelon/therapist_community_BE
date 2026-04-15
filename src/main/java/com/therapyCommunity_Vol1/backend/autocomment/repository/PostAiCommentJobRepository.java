package com.therapyCommunity_Vol1.backend.autocomment.repository;

import com.therapyCommunity_Vol1.backend.autocomment.domain.PostAiCommentJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostAiCommentJobRepository extends JpaRepository<PostAiCommentJob, Long> {

    Optional<PostAiCommentJob> findByPostId(Long postId);

    @Query("SELECT j FROM PostAiCommentJob j JOIN FETCH j.post WHERE j.id = :id")
    Optional<PostAiCommentJob> findByIdWithPost(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM PostAiCommentJob j WHERE j.id = :id")
    Optional<PostAiCommentJob> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT * FROM post_ai_comment_jobs
            WHERE status = 'QUEUED'
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY next_attempt_at ASC NULLS FIRST
            LIMIT :maxRows
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PostAiCommentJob> findDueJobs(@Param("now") LocalDateTime now, @Param("maxRows") int maxRows);
}
