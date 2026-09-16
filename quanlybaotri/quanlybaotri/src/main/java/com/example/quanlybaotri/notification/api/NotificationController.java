package com.example.quanlybaotri.notification.api;

import com.example.quanlybaotri.notification.domain.Notification;
import com.example.quanlybaotri.notification.persistence.NotificationRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationRepository repo;
    private final CurrentUser current;

    public NotificationController(NotificationRepository r, CurrentUser c) {
        repo = r;
        current = c;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Page<View> list(Pageable p) {
        UUID id = current.require().getId();
        return repo.findByUserId(id, p).map(View::from);
    }

    @GetMapping("/unread-count")
    public Count unread() {
        return new Count(repo.countByUserIdAndReadAtIsNull(current.require().getId()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void read(@PathVariable UUID id) {
        Notification n = repo.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thông báo"));
        if (!n.getUser().getId().equals(current.require().getId()))
            throw ApiException.forbidden("Không có quyền đọc thông báo");
        n.read();
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void readAll() {
        repo.findByUserIdAndReadAtIsNull(current.require().getId()).forEach(Notification::read);
    }

    public record Count(long count) {
    }

    public record View(UUID id, String type, String title, String message, String referenceType, UUID referenceId,
            Instant readAt, Instant createdAt) {
        static View from(Notification n) {
            return new View(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getReferenceType(),
                    n.getReferenceId(), n.getReadAt(), n.getCreatedAt());
        }
    }
}
