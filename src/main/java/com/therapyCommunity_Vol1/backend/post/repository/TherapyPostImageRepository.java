package com.therapyCommunity_Vol1.backend.post.repository;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TherapyPostImageRepository extends JpaRepository<TherapyPostImage, Long> {

    List<TherapyPostImage> findByPostIdOrderByDisplayOrderAsc(Long postId);

    int countByPostId(Long postId);
}
