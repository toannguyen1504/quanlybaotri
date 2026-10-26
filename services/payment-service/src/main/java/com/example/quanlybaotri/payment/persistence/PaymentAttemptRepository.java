package com.example.quanlybaotri.payment.persistence;

import com.example.quanlybaotri.payment.domain.PaymentAttempt;
import com.example.quanlybaotri.payment.domain.PaymentStatus;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<PaymentAttempt> {
    Optional<PaymentAttempt> findByTxnRef(String txnRef);
    Optional<PaymentAttempt> findFirstByTicketIdAndStatusOrderByCreatedAtDesc(UUID ticketId, PaymentStatus status);
    List<PaymentAttempt> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);
    List<PaymentAttempt> findByTicketIdInOrderByCreatedAtDesc(Collection<UUID> ticketIds);
    boolean existsByTicketIdAndStatus(UUID ticketId, PaymentStatus status);

    @Query("""
        select a.status as status, count(a) as total, coalesce(sum(a.amount), 0) as amount
        from PaymentAttempt a
        where a.createdAt >= :from and a.createdAt < :to
        group by a.status
        """)
    List<StatusAggregate> summarize(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
        select a.status as status, count(a) as total, coalesce(sum(a.amount), 0) as amount
        from PaymentAttempt a
        where a.initiatedById = :actorId and a.createdAt >= :from and a.createdAt < :to
        group by a.status
        """)
    List<StatusAggregate> summarizeForInitiator(
        @Param("actorId") UUID actorId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    interface StatusAggregate {
        PaymentStatus getStatus();
        long getTotal();
        BigDecimal getAmount();
    }
}

