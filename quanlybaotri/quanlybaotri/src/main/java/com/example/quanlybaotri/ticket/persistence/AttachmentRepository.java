package com.example.quanlybaotri.ticket.persistence;

import com.example.quanlybaotri.ticket.domain.Attachment;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    @EntityGraph(attributePaths = { "uploadedBy" })
    List<Attachment> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);

    @Override
    @EntityGraph(attributePaths = { "ticket", "ticket.requester", "ticket.assignee", "uploadedBy" })
    Optional<Attachment> findById(UUID id);
}
