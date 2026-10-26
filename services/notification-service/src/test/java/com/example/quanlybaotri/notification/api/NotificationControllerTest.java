package com.example.quanlybaotri.notification.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.quanlybaotri.notification.domain.Notification;
import com.example.quanlybaotri.notification.persistence.NotificationRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationControllerTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final NotificationController controller = new NotificationController(repository, currentUser);

    @Test
    void returnsNotificationOwnedByCurrentUser() {
        UUID userId = UUID.randomUUID();
        Notification notification = notificationFor(userId);
        when(repository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(currentUser.require()).thenReturn(actor(userId));

        NotificationController.View result = controller.get(notification.getId());

        assertEquals(notification.getId(), result.id());
        assertEquals("ASSIGNED", result.type());
        assertEquals("TICKET", result.referenceType());
    }

    @Test
    void rejectsNotificationOwnedByAnotherUser() {
        Notification notification = notificationFor(UUID.randomUUID());
        when(repository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(currentUser.require()).thenReturn(actor(UUID.randomUUID()));

        assertThrows(ApiException.class, () -> controller.get(notification.getId()));
    }

    private Notification notificationFor(UUID userId) {
        return new Notification(
            userId,
            UUID.randomUUID(),
            "ASSIGNED",
            "Phiếu đã được phân công",
            "Bạn có một phiếu mới cần xử lý.",
            "TICKET",
            UUID.randomUUID()
        );
    }

    private CurrentUser.Actor actor(UUID id) {
        return new CurrentUser.Actor(id, "tester", Set.of("ROLE_REQUESTER"), "token");
    }
}
