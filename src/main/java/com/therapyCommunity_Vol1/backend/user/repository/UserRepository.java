package com.therapyCommunity_Vol1.backend.user.repository;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    boolean existsByNickname(String nickname);

    @Query("SELECT u.id FROM User u WHERE u.role = :role AND u.deletedAt IS NULL")
    List<Long> findIdsByRole(@Param("role") UserRole role);
}
