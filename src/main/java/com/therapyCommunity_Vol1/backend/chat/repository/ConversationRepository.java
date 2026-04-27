package com.therapyCommunity_Vol1.backend.chat.repository;

import com.therapyCommunity_Vol1.backend.chat.domain.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c WHERE " +
            "(c.participant1.id = :uid1 AND c.participant2.id = :uid2)")
    Optional<Conversation> findByParticipants(@Param("uid1") Long smallerId, @Param("uid2") Long largerId);

    @Query(value = "SELECT c FROM Conversation c " +
            "LEFT JOIN FETCH c.participant1 " +
            "LEFT JOIN FETCH c.participant2 " +
            "WHERE c.participant1.id = :userId OR c.participant2.id = :userId " +
            "ORDER BY c.lastMessageAt DESC",
            countQuery = "SELECT COUNT(c) FROM Conversation c " +
            "WHERE c.participant1.id = :userId OR c.participant2.id = :userId")
    Page<Conversation> findByParticipantId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT m.conversation.id) FROM Message m " +
            "WHERE (m.conversation.participant1.id = :userId OR m.conversation.participant2.id = :userId) " +
            "AND m.sender.id != :userId AND m.read = false")
    long countUnreadConversations(@Param("userId") Long userId);
}
