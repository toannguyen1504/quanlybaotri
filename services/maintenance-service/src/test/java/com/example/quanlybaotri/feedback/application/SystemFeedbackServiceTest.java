package com.example.quanlybaotri.feedback.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.feedback.api.FeedbackManagementController.ReviewSystemFeedbackRequest;
import com.example.quanlybaotri.feedback.api.SystemFeedbackController.CreateSystemFeedbackRequest;
import com.example.quanlybaotri.feedback.domain.*;
import com.example.quanlybaotri.feedback.persistence.SystemFeedbackRepository;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class SystemFeedbackServiceTest {

    private SystemFeedbackRepository repository;
    private SystemFeedbackEventPublisher events;
    private SystemFeedbackService service;

    @BeforeEach
    void setUp() {
        repository = mock(SystemFeedbackRepository.class);
        events = mock(SystemFeedbackEventPublisher.class);
        IdentityClient identities = mock(IdentityClient.class);
        when(identities.resolve(any())).thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(invocation -> {
            SystemFeedback item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
            return item;
        });
        service = new SystemFeedbackService(
            repository,
            mock(TicketRepository.class),
            identities,
            mock(OrganizationClient.class),
            events
        );
    }

    @Test
    void requesterCreatesTrimmedFeedbackAndPublishesSafeEvent() {
        Actor requester = actor(UUID.randomUUID(), RoleName.REQUESTER);

        var result = service.create(
            new CreateSystemFeedbackRequest(
                SystemFeedbackCategory.SUGGESTION,
                "  Cải thiện tìm kiếm  ",
                "  Nội dung góp ý  "
            ),
            requester
        );

        assertThat(result.title()).isEqualTo("Cải thiện tìm kiếm");
        assertThat(result.content()).isEqualTo("Nội dung góp ý");
        assertThat(result.status()).isEqualTo(SystemFeedbackStatus.NEW);
        verify(events).publish(any(SystemFeedback.class), eq(requester.id()), eq("CREATED"));
    }

    @Test
    void technicianCannotCreateOrReadAnotherUsersFeedback() {
        Actor technician = actor(UUID.randomUUID(), RoleName.TECHNICIAN);
        SystemFeedback item = feedback(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(item, "id", id);
        when(repository.findById(id)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.create(
            new CreateSystemFeedbackRequest(
                SystemFeedbackCategory.BUG_REPORT, "Lỗi", "Chi tiết lỗi"
            ),
            technician
        )).isInstanceOfSatisfying(ApiException.class, error ->
            assertThat(error.getStatus().value()).isEqualTo(403)
        );
        assertThatThrownBy(() -> service.get(id, technician))
            .isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.getStatus().value()).isEqualTo(403)
            );
    }

    @Test
    void managerReviewsFeedbackAndTerminalFeedbackCannotBeReviewedAgain() {
        Actor manager = actor(UUID.randomUUID(), RoleName.MANAGER);
        SystemFeedback item = feedback(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(item, "id", id);
        when(repository.findLockedById(id)).thenReturn(Optional.of(item));

        var result = service.review(
            id,
            new ReviewSystemFeedbackRequest(SystemFeedbackStatus.RESOLVED, "  Đã xử lý  "),
            manager
        );

        assertThat(result.status()).isEqualTo(SystemFeedbackStatus.RESOLVED);
        assertThat(result.response()).isEqualTo("Đã xử lý");
        verify(events).publish(item, manager.id(), "STATUS_CHANGED");

        assertThatThrownBy(() -> service.review(
            id,
            new ReviewSystemFeedbackRequest(SystemFeedbackStatus.REJECTED, "Từ chối"),
            manager
        )).isInstanceOfSatisfying(ApiException.class, error ->
            assertThat(error.getStatus().value()).isEqualTo(409)
        );
    }

    @Test
    void requesterCannotUseManagementQueriesAndDateRangeIsValidated() {
        Actor requester = actor(UUID.randomUUID(), RoleName.REQUESTER);
        assertThatThrownBy(() -> service.manageSystem(
            null, null, null, null, null, null, PageRequest.of(0, 10), requester
        )).isInstanceOfSatisfying(ApiException.class, error ->
            assertThat(error.getStatus().value()).isEqualTo(403)
        );

        Actor admin = actor(UUID.randomUUID(), RoleName.ADMIN);
        assertThatThrownBy(() -> service.manageSystem(
            null, null, null, null,
            LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 14),
            PageRequest.of(0, 10), admin
        )).isInstanceOfSatisfying(ApiException.class, error ->
            assertThat(error.getCode()).isEqualTo("INVALID_DATE_RANGE")
        );
    }

    private SystemFeedback feedback(UUID requesterId) {
        return new SystemFeedback(
            requesterId,
            SystemFeedbackCategory.BUG_REPORT,
            "Không mở được báo cáo",
            "Màn hình báo lỗi"
        );
    }

    private Actor actor(UUID id, RoleName role) {
        return new Actor(
            id,
            role.name().toLowerCase(),
            role.name(),
            Set.of(role),
            "token",
            role == RoleName.REQUESTER ? "CUSTOMER" : "INTERNAL",
            null,
            "ALL_SITES",
            Set.of()
        );
    }
}
