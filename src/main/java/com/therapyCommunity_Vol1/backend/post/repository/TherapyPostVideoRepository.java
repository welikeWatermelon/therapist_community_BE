package com.therapyCommunity_Vol1.backend.post.repository;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostVideo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TherapyPostVideoRepository extends JpaRepository<TherapyPostVideo, Long> {

    List<TherapyPostVideo> findByPostIdOrderByCreatedAtAsc(Long postId);

    Optional<TherapyPostVideo> findByIdAndPostId(Long videoId, Long postId);

    int countByPostId(Long postId);
}
