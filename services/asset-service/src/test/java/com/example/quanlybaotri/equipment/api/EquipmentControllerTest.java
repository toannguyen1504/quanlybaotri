package com.example.quanlybaotri.equipment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.equipment.domain.*;
import com.example.quanlybaotri.equipment.persistence.*;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.security.CurrentUser;
import jakarta.persistence.criteria.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionTemplate;

class EquipmentControllerTest {
    private EquipmentRepository equipment;
    private OrganizationClient organizations;
    private CurrentUser current;
    private EquipmentController controller;

    @BeforeEach
    void setUp() {
        equipment = mock(EquipmentRepository.class);
        organizations = mock(OrganizationClient.class);
        current = mock(CurrentUser.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation ->
            invocation.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0)
                .doInTransaction(null)
        );
        controller = new EquipmentController(
            equipment,
            mock(EquipmentCategoryRepository.class),
            organizations,
            transactions,
            current
        );
    }

    @Test
    void summaryReturnsExactStatusCounts() {
        when(current.require()).thenReturn(internalActor());
        when(equipment.count(any(Specification.class))).thenReturn(12L, 7L, 3L, 2L, 4L, 5L, 1L);

        var summary = controller.summary(null, null, null, null, null, null, 30);

        assertThat(summary).isEqualTo(new EquipmentController.SummaryView(12, 7, 3, 2, 4, 5, 1));
        verify(equipment, times(7)).count(any(Specification.class));
    }

    @Test
    void summaryReturnsEmptyWithoutQueryingDataForAnotherCustomer() {
        UUID ownCustomer = UUID.randomUUID();
        when(current.require()).thenReturn(customerActor(ownCustomer));

        var summary = controller.summary(null, null, UUID.randomUUID(), null, null, null, 30);

        assertThat(summary).isEqualTo(new EquipmentController.SummaryView(0, 0, 0, 0, 0, 0, 0));
        verifyNoInteractions(equipment, organizations);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    void filtersSearchAllAssetFieldsAndApplyCategory() {
        UUID categoryId = UUID.randomUUID();
        Root<Equipment> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteria = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        Expression<String> lowered = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get(anyString())).thenReturn(path);
        when(path.get(anyString())).thenReturn(path);
        when(criteria.lower(any(Expression.class))).thenReturn(lowered);
        when(criteria.like(any(Expression.class), anyString())).thenReturn(predicate);
        when(criteria.equal(any(Expression.class), any())).thenReturn(predicate);
        when(criteria.or(any(Predicate[].class))).thenReturn(predicate);
        when(criteria.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Equipment> specification = controller.filters(
            "laser",
            EquipmentStatus.ACTIVE,
            categoryId,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.now()
        );
        specification.toPredicate(root, query, criteria);

        verify(criteria, times(7)).like(lowered, "%laser%");
        verify(criteria).equal(any(Expression.class), eq(categoryId));
        verify(criteria).equal(any(Expression.class), eq(EquipmentStatus.ACTIVE));
    }

    private CurrentUser.Actor internalActor() {
        return new CurrentUser.Actor(
            UUID.randomUUID(),
            "Quản trị",
            Set.of("ADMIN"),
            "INTERNAL",
            null,
            null,
            Set.of()
        );
    }

    private CurrentUser.Actor customerActor(UUID customerId) {
        return new CurrentUser.Actor(
            UUID.randomUUID(),
            "Khách hàng",
            Set.of("REQUESTER"),
            "CUSTOMER",
            customerId,
            "ALL_SITES",
            Set.of()
        );
    }
}
