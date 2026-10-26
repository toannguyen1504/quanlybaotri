package com.example.quanlybaotri.chat.persistence;

import com.example.quanlybaotri.chat.domain.ChatConversation;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {
    Optional<ChatConversation> findByTicketId(UUID ticketId);

    @Query("""
        select c from ChatConversation c
        where c.lastMessageAt is not null
          and (:elevated = true or c.requesterId = :userId or c.assigneeId = :userId)
        order by c.lastMessageAt desc
        """)
    List<ChatConversation> findInboxFirstPage(
        @Param("userId") UUID userId,
        @Param("elevated") boolean elevated,
        Pageable pageable
    );

    @Query("""
        select c from ChatConversation c
        where c.lastMessageAt is not null
          and (:elevated = true or c.requesterId = :userId or c.assigneeId = :userId)
          and c.lastMessageAt < :before
        order by c.lastMessageAt desc
        """)
    List<ChatConversation> findInboxBefore(
        @Param("userId") UUID userId,
        @Param("elevated") boolean elevated,
        @Param("before") Instant before,
        Pageable pageable
    );

    @Query("""
        select c.id from ChatConversation c
        where c.lastMessageAt is not null
          and (:elevated = true or c.requesterId = :userId or c.assigneeId = :userId)
        """)
    List<UUID> findVisibleIds(@Param("userId") UUID userId, @Param("elevated") boolean elevated);

    @Query("""
        select c from ChatConversation c
        where c.lastMessageAt is not null
          and (:elevated = true or c.requesterId = :userId or c.assigneeId = :userId)
          and (:q = '' or lower(c.ticketCode) like lower(concat('%', :q, '%'))
               or lower(c.ticketTitle) like lower(concat('%', :q, '%')))
          and (:bucket = 'ALL'
               or (:bucket = 'ACTIVE' and c.ticketStatus not in ('CLOSED','REJECTED','CANCELLED'))
               or (:bucket = 'CLOSED' and c.ticketStatus in ('CLOSED','REJECTED','CANCELLED')))
          and (:unreadOnly = false or exists (
              select m.id from ChatMessage m
              where m.conversationId = c.id and m.senderId <> :userId
                and m.sequence > coalesce((select max(r.lastReadSequence) from ChatReadState r where r.conversationId = c.id and r.userId = :userId), 0)
          ))
        order by c.lastMessageAt desc, c.id desc
        """)
    List<ChatConversation> searchInboxFirstPage(
        @Param("userId") UUID userId,
        @Param("elevated") boolean elevated,
        @Param("q") String query,
        @Param("bucket") String bucket,
        @Param("unreadOnly") boolean unreadOnly,
        Pageable pageable
    );

    @Query("""
        select c from ChatConversation c
        where c.lastMessageAt is not null
          and (:elevated = true or c.requesterId = :userId or c.assigneeId = :userId)
          and c.lastMessageAt < :before
          and (:q = '' or lower(c.ticketCode) like lower(concat('%', :q, '%'))
               or lower(c.ticketTitle) like lower(concat('%', :q, '%')))
          and (:bucket = 'ALL'
               or (:bucket = 'ACTIVE' and c.ticketStatus not in ('CLOSED','REJECTED','CANCELLED'))
               or (:bucket = 'CLOSED' and c.ticketStatus in ('CLOSED','REJECTED','CANCELLED')))
          and (:unreadOnly = false or exists (
              select m.id from ChatMessage m
              where m.conversationId = c.id and m.senderId <> :userId
                and m.sequence > coalesce((select max(r.lastReadSequence) from ChatReadState r where r.conversationId = c.id and r.userId = :userId), 0)
          ))
        order by c.lastMessageAt desc, c.id desc
        """)
    List<ChatConversation> searchInboxBefore(
        @Param("userId") UUID userId,
        @Param("elevated") boolean elevated,
        @Param("before") Instant before,
        @Param("q") String query,
        @Param("bucket") String bucket,
        @Param("unreadOnly") boolean unreadOnly,
        Pageable pageable
    );

    @Query("""
        select c from ChatConversation c
        where c.lastMessageAt is not null
          and (:elevated = true or c.requesterId = :userId or c.assigneeId = :userId)
          and (c.lastMessageAt < :before or (c.lastMessageAt = :before and c.id < :beforeId))
          and (:q = '' or lower(c.ticketCode) like lower(concat('%', :q, '%'))
               or lower(c.ticketTitle) like lower(concat('%', :q, '%')))
          and (:bucket = 'ALL'
               or (:bucket = 'ACTIVE' and c.ticketStatus not in ('CLOSED','REJECTED','CANCELLED'))
               or (:bucket = 'CLOSED' and c.ticketStatus in ('CLOSED','REJECTED','CANCELLED')))
          and (:unreadOnly = false or exists (
              select m.id from ChatMessage m
              where m.conversationId = c.id and m.senderId <> :userId
                and m.sequence > coalesce((select max(r.lastReadSequence) from ChatReadState r where r.conversationId = c.id and r.userId = :userId), 0)
          ))
        order by c.lastMessageAt desc, c.id desc
        """)
    List<ChatConversation> searchInboxAfterCursor(
        @Param("userId") UUID userId,
        @Param("elevated") boolean elevated,
        @Param("before") Instant before,
        @Param("beforeId") UUID beforeId,
        @Param("q") String query,
        @Param("bucket") String bucket,
        @Param("unreadOnly") boolean unreadOnly,
        Pageable pageable
    );

    @Query("""
        select count(c) from ChatConversation c
        where c.lastMessageAt is not null
          and (:elevated = true or c.requesterId = :userId or c.assigneeId = :userId)
          and (:bucket = 'ALL'
               or (:bucket = 'ACTIVE' and c.ticketStatus not in ('CLOSED','REJECTED','CANCELLED'))
               or (:bucket = 'CLOSED' and c.ticketStatus in ('CLOSED','REJECTED','CANCELLED')))
        """)
    long countVisibleByBucket(
        @Param("userId") UUID userId,
        @Param("elevated") boolean elevated,
        @Param("bucket") String bucket
    );

    @Query("""
        select count(c) from ChatConversation c
        where c.lastMessageAt is not null
          and (:elevated = true or c.requesterId = :userId or c.assigneeId = :userId)
          and exists (
              select m.id from ChatMessage m
              where m.conversationId = c.id and m.senderId <> :userId
                and m.sequence > coalesce((select max(r.lastReadSequence) from ChatReadState r where r.conversationId = c.id and r.userId = :userId), 0)
          )
        """)
    long countUnreadConversations(@Param("userId") UUID userId, @Param("elevated") boolean elevated);
}
