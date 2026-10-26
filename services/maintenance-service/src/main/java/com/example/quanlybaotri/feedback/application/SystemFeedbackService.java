package com.example.quanlybaotri.feedback.application;

import static com.example.quanlybaotri.feedback.api.FeedbackManagementController.*;
import static com.example.quanlybaotri.feedback.api.SystemFeedbackController.*;

import com.example.quanlybaotri.feedback.domain.*;
import com.example.quanlybaotri.feedback.persistence.SystemFeedbackRepository;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemFeedbackService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final SystemFeedbackRepository feedback;
    private final TicketRepository tickets;
    private final IdentityClient identities;
    private final OrganizationClient organizations;
    private final SystemFeedbackEventPublisher events;

    public SystemFeedbackService(
        SystemFeedbackRepository feedback,
        TicketRepository tickets,
        IdentityClient identities,
        OrganizationClient organizations,
        SystemFeedbackEventPublisher events
    ) {
        this.feedback = feedback;
        this.tickets = tickets;
        this.identities = identities;
        this.organizations = organizations;
        this.events = events;
    }

    @Transactional
    public SystemFeedbackView create(CreateSystemFeedbackRequest request, Actor actor) {
        requireRequester(actor);
        SystemFeedback created = feedback.save(new SystemFeedback(
            actor.id(), request.category(), request.title(), request.content()
        ));
        feedback.flush();
        events.publish(created, actor.id(), "CREATED");
        return view(created);
    }

    @Transactional(readOnly = true)
    public Page<SystemFeedbackView> mine(Actor actor, Pageable pageable) {
        requireRequester(actor);
        return views(feedback.findAll(
            (root, query, cb) -> cb.equal(root.get("requesterId"), actor.id()),
            pageable
        ));
    }

    @Transactional(readOnly = true)
    public SystemFeedbackView get(UUID id, Actor actor) {
        SystemFeedback item = find(id);
        if (!elevated(actor) && !item.getRequesterId().equals(actor.id())) {
            throw ApiException.forbidden("Không có quyền xem phản hồi này");
        }
        return view(item);
    }

    @Transactional(readOnly = true)
    public Page<SystemFeedbackView> manageSystem(
        String q,
        SystemFeedbackStatus status,
        SystemFeedbackCategory category,
        UUID requesterId,
        LocalDate from,
        LocalDate to,
        Pageable pageable,
        Actor actor
    ) {
        requireElevated(actor);
        validateRange(from, to);
        return views(feedback.findAll(systemFilters(q, status, category, requesterId, from, to), pageable));
    }

    @Transactional
    public SystemFeedbackView review(UUID id, ReviewSystemFeedbackRequest request, Actor actor) {
        requireElevated(actor);
        SystemFeedback item = feedback.findLockedById(id)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy phản hồi"));
        try {
            item.review(request.status(), request.response(), actor.id());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK_REVIEW", ex.getMessage());
        } catch (IllegalStateException ex) {
            throw ApiException.conflict(ex.getMessage());
        }
        feedback.flush();
        events.publish(item, actor.id(), "STATUS_CHANGED");
        return view(item);
    }

    @Transactional(readOnly = true)
    public Page<TicketFeedbackManagementView> manageTicketFeedback(
        String q,
        Integer rating,
        UUID customerId,
        UUID siteId,
        UUID requesterId,
        LocalDate from,
        LocalDate to,
        Pageable pageable,
        Actor actor
    ) {
        requireElevated(actor);
        validateRange(from, to);
        Page<MaintenanceTicket> page = tickets.findAll(
            ticketFeedbackFilters(q, rating, customerId, siteId, requesterId, from, to),
            pageable
        );
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(
            page.getContent().stream().map(MaintenanceTicket::getRequesterId).toList()
        );
        Map<UUID, OrganizationClient.DepartmentRef> sites = organizations.resolve(
            page.getContent().stream().map(MaintenanceTicket::getSiteId).toList()
        );
        return page.map(ticket -> {
            IdentityClient.UserRef requester = users.get(ticket.getRequesterId());
            OrganizationClient.DepartmentRef site = sites.get(ticket.getSiteId());
            return new TicketFeedbackManagementView(
                ticket.getId(), ticket.getCode(), ticket.getTitle(), ticket.getRequesterId(),
                requester == null ? null : requester.fullName(), ticket.getCustomerId(),
                site == null ? null : site.customerName(), ticket.getSiteId(),
                site == null ? null : site.name(), ticket.getFeedbackRating(),
                ticket.getFeedbackComment(), ticket.getFeedbackSubmittedAt()
            );
        });
    }

    private Specification<SystemFeedback> systemFilters(
        String q,
        SystemFeedbackStatus status,
        SystemFeedbackCategory category,
        UUID requesterId,
        LocalDate from,
        LocalDate to
    ) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String value = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                conditions.add(cb.or(
                    cb.like(cb.lower(root.get("title")), value),
                    cb.like(cb.lower(root.get("content")), value),
                    cb.like(cb.lower(root.get("response")), value)
                ));
            }
            if (status != null) conditions.add(cb.equal(root.get("status"), status));
            if (category != null) conditions.add(cb.equal(root.get("category"), category));
            if (requesterId != null) conditions.add(cb.equal(root.get("requesterId"), requesterId));
            addDateRange(conditions, root.get("createdAt"), cb, from, to);
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    private Specification<MaintenanceTicket> ticketFeedbackFilters(
        String q,
        Integer rating,
        UUID customerId,
        UUID siteId,
        UUID requesterId,
        LocalDate from,
        LocalDate to
    ) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.isNotNull(root.get("feedbackSubmittedAt")));
            if (q != null && !q.isBlank()) {
                String value = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                conditions.add(cb.or(
                    cb.like(cb.lower(root.get("code")), value),
                    cb.like(cb.lower(root.get("title")), value),
                    cb.like(cb.lower(root.get("feedbackComment")), value)
                ));
            }
            if (rating != null) conditions.add(cb.equal(root.get("feedbackRating"), rating));
            if (customerId != null) conditions.add(cb.equal(root.get("customerId"), customerId));
            if (siteId != null) conditions.add(cb.equal(root.get("siteId"), siteId));
            if (requesterId != null) conditions.add(cb.equal(root.get("requesterId"), requesterId));
            addDateRange(conditions, root.get("feedbackSubmittedAt"), cb, from, to);
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    private <T> void addDateRange(
        List<Predicate> conditions,
        jakarta.persistence.criteria.Path<Instant> path,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        LocalDate from,
        LocalDate to
    ) {
        if (from != null) conditions.add(cb.greaterThanOrEqualTo(
            path, from.atStartOfDay(BUSINESS_ZONE).toInstant()
        ));
        if (to != null) conditions.add(cb.lessThan(
            path, to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()
        ));
    }

    private Page<SystemFeedbackView> views(Page<SystemFeedback> page) {
        Set<UUID> ids = new LinkedHashSet<>();
        page.getContent().forEach(item -> {
            ids.add(item.getRequesterId());
            if (item.getReviewedById() != null) ids.add(item.getReviewedById());
        });
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(ids);
        return page.map(item -> view(item, users));
    }

    private SystemFeedbackView view(SystemFeedback item) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(item.getRequesterId());
        if (item.getReviewedById() != null) ids.add(item.getReviewedById());
        return view(item, identities.resolve(ids));
    }

    private SystemFeedbackView view(SystemFeedback item, Map<UUID, IdentityClient.UserRef> users) {
        IdentityClient.UserRef requester = users.get(item.getRequesterId());
        IdentityClient.UserRef reviewer = item.getReviewedById() == null
            ? null : users.get(item.getReviewedById());
        return new SystemFeedbackView(
            item.getId(), item.getRequesterId(), requester == null ? null : requester.fullName(),
            item.getCategory(), item.getTitle(), item.getContent(), item.getStatus(),
            item.getResponse(), item.getReviewedById(), reviewer == null ? null : reviewer.fullName(),
            item.getReviewedAt(), item.getCreatedAt(), item.getUpdatedAt(), item.getVersion()
        );
    }

    private SystemFeedback find(UUID id) {
        return feedback.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phản hồi"));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw new ApiException(
            HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Ngày bắt đầu không được sau ngày kết thúc"
        );
    }

    private void requireRequester(Actor actor) {
        if (!actor.has(RoleName.REQUESTER)) throw ApiException.forbidden(
            "Chỉ requester được gửi và xem phản hồi của mình"
        );
    }

    private void requireElevated(Actor actor) {
        if (!elevated(actor)) throw ApiException.forbidden("Chỉ admin hoặc manager được quản lý phản hồi");
    }

    private boolean elevated(Actor actor) {
        return actor.has(RoleName.ADMIN) || actor.has(RoleName.MANAGER);
    }
}
