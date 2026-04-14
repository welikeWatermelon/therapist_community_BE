package com.therapyCommunity_Vol1.backend.therapist.repository;

import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TherapistVerificationRepository extends JpaRepository<TherapistVerification, Long> {

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Optional<TherapistVerification> findByUserId(Long userId);

    boolean existsByLicenseCodeAndUserIdNot(String licenseCode, Long userId);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Optional<TherapistVerification> findWithUserById(Long id);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Page<TherapistVerification> findByStatus(TherapistVerificationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Page<TherapistVerification> findAll(Pageable pageable);

    long countByStatus(TherapistVerificationStatus status);
}
