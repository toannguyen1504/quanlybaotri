package com.example.quanlybaotri.ticket.persistence;

import com.example.quanlybaotri.ticket.domain.Attachment;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);

    @Override
    @EntityGraph(attributePaths = { "ticket" })
    Optional<Attachment> findById(UUID id);
}
