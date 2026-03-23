package com.therapyCommunity_Vol1.backend.notification.repository;

import com.therapyCommunity_Vol1.backend.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    // 인덱싱을 통해 DB 부하를 줄임
    // 프론트에서
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipient.id = :recipientId AND n.isRead = false")
    long countUnreadByRecipientId(@Param("recipientId") Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :recipientId AND n.isRead = false")
    int markAllAsReadByRecipientId(@Param("recipientId") Long recipientId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.referenceType = :referenceType AND n.referenceId = :referenceId")
    void deleteByReference(@Param("referenceType") String referenceType, @Param("referenceId") Long referenceId);
}
