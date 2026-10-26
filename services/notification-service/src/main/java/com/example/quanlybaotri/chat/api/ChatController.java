package com.example.quanlybaotri.chat.api;

import com.example.quanlybaotri.chat.application.ChatService;
import com.example.quanlybaotri.shared.security.CurrentUser;
import java.time.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    private final ChatService service;
    private final CurrentUser current;
    public ChatController(ChatService service, CurrentUser current) { this.service = service; this.current = current; }

    @GetMapping("/conversations")
    public ChatViews.ConversationPage conversations(
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "ALL") String bucket,
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        @RequestParam(defaultValue = "20") int size
    ) {
        var actor = current.require();
        return service.inbox(actor.id(), actor.roles(), actor.token(), cursor, q, bucket, unreadOnly, size);
    }

    @GetMapping("/conversations/summary")
    public ChatViews.ConversationSummary summary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        var actor = current.require();
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate start = from == null ? LocalDate.now(zone).minusDays(29) : from;
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        return service.summary(actor.id(), actor.roles(), start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant());
    }

    @GetMapping("/unread-count")
    public ChatViews.UnreadCount unread() {
        var actor = current.require();
        return new ChatViews.UnreadCount(service.unread(actor.id(), actor.roles()));
    }

    @GetMapping("/tickets/{ticketId}/messages")
    public ChatViews.MessagePage messages(
        @PathVariable java.util.UUID ticketId,
        @RequestParam(required = false) Long beforeSequence,
        @RequestParam(defaultValue = "50") int size
    ) {
        var actor = current.require();
        return service.history(actor.id(), actor.token(), ticketId, beforeSequence, size);
    }
}
