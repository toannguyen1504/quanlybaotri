package com.example.quanlybaotri.telegram.api;

import com.example.quanlybaotri.shared.security.CurrentUser;
import com.example.quanlybaotri.telegram.application.TelegramLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications/telegram")
@PreAuthorize("hasRole('TECHNICIAN')")
public class TelegramController {

    private final TelegramLinkService service;
    private final CurrentUser current;

    public TelegramController(TelegramLinkService service, CurrentUser current) {
        this.service = service;
        this.current = current;
    }

    @GetMapping
    public TelegramLinkService.Status status() {
        return service.status(current.require().id());
    }

    @PostMapping("/link")
    public TelegramLinkService.LinkRequest createLink() {
        return service.createLink(current.require().id());
    }

    @DeleteMapping("/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect() {
        service.disconnect(current.require().id());
    }
}
