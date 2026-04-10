package com.therapyCommunity_Vol1.backend.autocomment.repository;

import com.therapyCommunity_Vol1.backend.autocomment.domain.PostAiCommentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostAiCommentJobRepository extends JpaRepository<PostAiCommentJob, Long> {

    Optional<PostAiCommentJob> findByPostId(Long postId);

    @Query(value = """
            SELECT * FROM post_ai_comment_jobs
            WHERE status = 'QUEUED'
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY next_attempt_at ASC NULLS FIRST
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PostAiCommentJob> findDueJobs(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
