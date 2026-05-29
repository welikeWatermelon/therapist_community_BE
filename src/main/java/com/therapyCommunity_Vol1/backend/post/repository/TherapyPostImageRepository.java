package com.therapyCommunity_Vol1.backend.post.repository;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TherapyPostImageRepository extends JpaRepository<TherapyPostImage, Long> {

    List<TherapyPostImage> findByPostIdOrderByDisplayOrderAsc(Long postId);

    /** 멱등 confirm 판정용 — finalKey(stored_path) 로 기존 레코드 조회. */
    Optional<TherapyPostImage> findByStoredPath(String storedPath);

    /**
     * 목록 응답 빌드 시점에 N+1 제거를 위한 batch 조회.
     * post_id ASC, display_order ASC 정렬로 호출처가 groupingBy 후 그대로 사용 가능.
     */
    List<TherapyPostImage> findByPostIdInOrderByPostIdAscDisplayOrderAsc(List<Long> postIds);

    int countByPostId(Long postId);
}
