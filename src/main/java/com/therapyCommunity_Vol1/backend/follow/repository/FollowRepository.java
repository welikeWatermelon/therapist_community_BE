package com.therapyCommunity_Vol1.backend.follow.repository;

import com.therapyCommunity_Vol1.backend.follow.domain.Follow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    @Query("SELECT COUNT(f) FROM Follow f WHERE f.following.id = :followingId AND f.follower.deletedAt IS NULL")
    long countByFollowingId(@Param("followingId") Long followingId);

    @Query("SELECT COUNT(f) FROM Follow f WHERE f.follower.id = :followerId AND f.following.deletedAt IS NULL")
    long countByFollowerId(@Param("followerId") Long followerId);

    @Query(value = "SELECT f FROM Follow f JOIN FETCH f.follower WHERE f.following.id = :userId AND f.follower.deletedAt IS NULL",
           countQuery = "SELECT COUNT(f) FROM Follow f WHERE f.following.id = :userId AND f.follower.deletedAt IS NULL")
    Page<Follow> findFollowersByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(value = "SELECT f FROM Follow f JOIN FETCH f.following WHERE f.follower.id = :userId AND f.following.deletedAt IS NULL",
           countQuery = "SELECT COUNT(f) FROM Follow f WHERE f.follower.id = :userId AND f.following.deletedAt IS NULL")
    Page<Follow> findFollowingsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT f.following.id FROM Follow f WHERE f.follower.id = :followerId AND f.following.deletedAt IS NULL")
    List<Long> findFollowingIdsByFollowerId(@Param("followerId") Long followerId);
}
