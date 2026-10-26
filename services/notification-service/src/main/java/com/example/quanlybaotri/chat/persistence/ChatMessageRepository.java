package com.example.quanlybaotri.chat.persistence;

import com.example.quanlybaotri.chat.domain.ChatMessage;
import java.util.*;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    @Query("""
        select m from ChatMessage m
        where m.conversationId = :conversationId
          and (:before is null or m.sequence < :before)
        order by m.sequence desc
        """)
    List<ChatMessage> history(
        @Param("conversationId") UUID conversationId,
        @Param("before") Long before,
        Pageable pageable
    );

    @Query("""
        select count(m) from ChatMessage m
        where m.conversationId = :conversationId
          and m.sequence > :after
          and m.senderId <> :userId
        """)
    long countUnread(
        @Param("conversationId") UUID conversationId,
        @Param("userId") UUID userId,
        @Param("after") long after
    );

    @Query("""
        select count(m) from ChatMessage m, ChatConversation c
        where m.conversationId = c.id
          and (:elevated = true or c.requesterId = :userId or c.assigneeId = :userId)
          and m.senderId <> :userId
          and m.sequence > coalesce((select max(r.lastReadSequence) from ChatReadState r where r.conversationId = c.id and r.userId = :userId), 0)
        """)
    long countUnreadVisible(@Param("userId") UUID userId, @Param("elevated") boolean elevated);

    @Query("""
        select m.conversationId as conversationId, count(m) as unreadCount
        from ChatMessage m
        where m.conversationId in :conversationIds
          and m.senderId <> :userId
          and m.sequence > coalesce((
              select max(r.lastReadSequence) from ChatReadState r
              where r.conversationId = m.conversationId and r.userId = :userId
          ), 0)
        group by m.conversationId
        """)
    List<ConversationUnread> countUnreadByConversation(
        @Param("conversationIds") Collection<UUID> conversationIds,
        @Param("userId") UUID userId
    );

    @Query("""
        select count(m) from ChatMessage m, ChatConversation c
        where m.conversationId = c.id
          and (:elevated = true or c.requesterId = :userId or c.assigneeId = :userId)
          and m.createdAt >= :from and m.createdAt < :to
        """)
    long countVisibleSince(
        @Param("userId") UUID userId,
        @Param("elevated") boolean elevated,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    interface ConversationUnread {
        UUID getConversationId();
        long getUnreadCount();
    }

}
