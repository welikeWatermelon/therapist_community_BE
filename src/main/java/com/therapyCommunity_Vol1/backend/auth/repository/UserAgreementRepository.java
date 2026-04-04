package com.therapyCommunity_Vol1.backend.auth.repository;

import com.therapyCommunity_Vol1.backend.auth.domain.UserAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {
}
