package com.therapyCommunity_Vol1.backend.chat.repository;

import com.therapyCommunity_Vol1.backend.chat.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = "sender")
    List<Message> findByConversationIdAndIdLessThanOrderByIdDesc(
            Long conversationId, Long beforeId, Pageable pageable);

    @EntityGraph(attributePaths = "sender")
    List<Message> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    @Modifying
    @Query("UPDATE Message m SET m.read = true " +
            "WHERE m.conversation.id = :conversationId AND m.sender.id != :userId AND m.read = false")
    int markAllAsReadInConversation(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Query("SELECT m.conversation.id, COUNT(m) FROM Message m " +
            "WHERE m.conversation.id IN :conversationIds " +
            "AND m.sender.id != :userId AND m.read = false " +
            "GROUP BY m.conversation.id")
    List<Object[]> countUnreadByConversationIds(
            @Param("conversationIds") List<Long> conversationIds,
            @Param("userId") Long userId);
}
