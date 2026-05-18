package com.therapyCommunity_Vol1.backend.message.repository;

import com.therapyCommunity_Vol1.backend.message.domain.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m " +
           "JOIN FETCH m.sender " +
           "WHERE m.receiver.id = :receiverId AND m.deletedByReceiver = false " +
           "ORDER BY m.createdAt DESC")
    Page<Message> findReceivedMessages(@Param("receiverId") Long receiverId, Pageable pageable);

    @Query("SELECT m FROM Message m " +
           "JOIN FETCH m.receiver " +
           "WHERE m.sender.id = :senderId AND m.deletedBySender = false " +
           "ORDER BY m.createdAt DESC")
    Page<Message> findSentMessages(@Param("senderId") Long senderId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m " +
           "WHERE m.receiver.id = :receiverId AND m.isRead = false AND m.deletedByReceiver = false")
    long countUnreadMessages(@Param("receiverId") Long receiverId);
}
