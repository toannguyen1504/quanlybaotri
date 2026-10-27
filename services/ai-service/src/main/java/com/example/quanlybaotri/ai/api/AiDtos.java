package com.example.quanlybaotri.ai.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class AiDtos {
    private AiDtos() {}
    public enum ResponseType { TEXT, TICKET_RESULTS, EQUIPMENT_RESULTS, TICKET_DRAFT }
    public enum DraftStatus { INCOMPLETE, READY, CREATED }
    public record ConversationResponse(UUID conversationId,Instant expiresAt) {}
    public record MessageRequest(@NotBlank @Size(max=2000) String message) {}
    public record SourceRef(String sourceId,String title,String section) {}
    public record TicketCard(UUID id,String code,String title,String status,String priority,String equipmentCode,
        String equipmentName,String siteName,Instant submittedAt) {}
    public record EquipmentCard(UUID id,String assetCode,String name,String serialNumber,String location,
        String siteName,String customerName,String status,String warrantyStatus,boolean active) {}
    public record RequesterOption(UUID id,String fullName,String email) {}
    public record TicketDraft(UUID id,DraftStatus status,UUID equipmentId,String equipmentCode,String equipmentName,
        UUID requesterId,String requesterName,String title,String description,String priority,List<String> missingFields,
        List<RequesterOption> requesterOptions) {
        public TicketDraft {
            missingFields=missingFields==null?List.of():List.copyOf(missingFields);
            requesterOptions=requesterOptions==null?List.of():List.copyOf(requesterOptions);
        }
    }
    public record AssistantResponse(UUID conversationId,ResponseType type,String reply,String intent,
        List<TicketCard> tickets,List<EquipmentCard> equipment,TicketDraft draft,List<SourceRef> sources,Instant expiresAt) {
        public AssistantResponse {
            tickets=tickets==null?List.of():List.copyOf(tickets);
            equipment=equipment==null?List.of():List.copyOf(equipment);
            sources=sources==null?List.of():List.copyOf(sources);
        }
    }
    public record ConfirmResponse(TicketCard ticket,String message) {}
}
