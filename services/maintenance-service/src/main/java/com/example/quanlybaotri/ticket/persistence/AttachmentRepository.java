package com.example.quanlybaotri.ticket.persistence;

import com.example.quanlybaotri.ticket.domain.Attachment;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByTicketIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ticketId);

    List<Attachment> findTop100ByDeletedAtIsNotNullOrderByDeletedAtAsc();

    @Override
    @EntityGraph(attributePaths = { "ticket" })
    Optional<Attachment> findById(UUID id);

    @EntityGraph(attributePaths = { "ticket" })
    Optional<Attachment> findByIdAndDeletedAtIsNull(UUID id);
}
