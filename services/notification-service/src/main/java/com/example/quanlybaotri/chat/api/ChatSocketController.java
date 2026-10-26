package com.example.quanlybaotri.chat.api;

import com.example.quanlybaotri.chat.application.ChatService;
import com.example.quanlybaotri.shared.api.ApiException;
import java.security.Principal;
import java.util.*;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

@Controller
public class ChatSocketController {
    private final ChatService service;
    private final SimpMessagingTemplate messaging;
    public ChatSocketController(ChatService service, SimpMessagingTemplate messaging) {
        this.service = service; this.messaging = messaging;
    }

    @MessageMapping("/chat.send")
    public void send(SendCommand command, Principal principal) {
        try {
            Actor actor = actor(principal);
            service.send(actor.id(), actor.roles(), actor.token(), command.ticketId(), command.clientMessageId(), command.content());
        } catch (RuntimeException error) {
            sendError(principal, command == null ? null : command.ticketId(),
                command == null ? null : command.clientMessageId(), null, error);
        }
    }

    @MessageMapping("/chat.read")
    public void read(ReadCommand command, Principal principal) {
        try {
            Actor actor = actor(principal);
            service.markRead(actor.id(), actor.token(), command.ticketId(), command.throughSequence(), command.clientCommandId());
        } catch (RuntimeException error) {
            sendError(principal, command == null ? null : command.ticketId(), null,
                command == null ? null : command.clientCommandId(), error);
        }
    }

    private Actor actor(Principal principal) {
        if (!(principal instanceof UsernamePasswordAuthenticationToken auth)) throw new IllegalStateException("Unauthenticated chat session");
        Set<String> roles = new LinkedHashSet<>();
        auth.getAuthorities().forEach(a -> roles.add(a.getAuthority()));
        return new Actor(UUID.fromString(auth.getName()), (String) auth.getCredentials(), roles);
    }

    private void sendError(Principal principal, UUID ticketId, UUID clientMessageId, String clientCommandId, RuntimeException error) {
        if (principal == null) return;
        String code = error instanceof ApiException api ? api.getCode() : "CHAT_COMMAND_FAILED";
        String message = error.getMessage() == null ? "Không thể xử lý thao tác chat" : error.getMessage();
        messaging.convertAndSendToUser(principal.getName(), "/exchange/amq.direct/chat-errors",
            new ChatViews.CommandError("COMMAND_ERROR", code, message, ticketId, clientMessageId, clientCommandId));
    }

    public record SendCommand(UUID ticketId, UUID clientMessageId, String content) {}
    public record ReadCommand(UUID ticketId, String clientCommandId, long throughSequence) {}
    private record Actor(UUID id, String token, Set<String> roles) {}
}
