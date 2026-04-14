package com.therapyCommunity_Vol1.backend.user.repository;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    boolean existsByNickname(String nickname);

    @Query("SELECT u.id FROM User u WHERE u.role = :role AND u.deletedAt IS NULL")
    List<Long> findIdsByRole(@Param("role") UserRole role);

    // ===== Admin 통계용 =====

    long countByDeletedAtIsNull();

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL AND u.createdAt >= :since")
    long countActiveUsersCreatedAfter(@Param("since") LocalDateTime since);

    long countByRole(UserRole role);

    @Query("SELECT u.role, COUNT(u) FROM User u WHERE u.deletedAt IS NULL GROUP BY u.role")
    List<Object[]> countGroupByRole();

    @Query("SELECT FUNCTION('DATE', u.createdAt), COUNT(u) " +
            "FROM User u " +
            "WHERE u.deletedAt IS NULL AND u.createdAt >= :since " +
            "GROUP BY FUNCTION('DATE', u.createdAt) " +
            "ORDER BY FUNCTION('DATE', u.createdAt)")
    List<Object[]> countDailySignups(@Param("since") LocalDateTime since);

    @Query("""
            SELECT u FROM User u
            WHERE (:keyword IS NULL
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:role IS NULL OR u.role = :role)
            """)
    Page<User> searchByKeywordAndRole(
            @Param("keyword") String keyword,
            @Param("role") UserRole role,
            Pageable pageable
    );
}
