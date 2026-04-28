package com.therapyCommunity_Vol1.backend.analytics.repository;

import com.therapyCommunity_Vol1.backend.analytics.domain.UserEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEventRepository extends JpaRepository<UserEvent, Long> {
}
