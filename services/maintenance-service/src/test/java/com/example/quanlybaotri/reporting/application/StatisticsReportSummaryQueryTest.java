package com.example.quanlybaotri.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.reporting.application.StatisticsReportService.Filter;
import com.example.quanlybaotri.reporting.application.StatisticsReportService.RawTicket;
import com.example.quanlybaotri.reporting.application.StatisticsReportService.StatisticsReport;
import com.example.quanlybaotri.ticket.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class StatisticsReportSummaryQueryTest {
    private NamedParameterJdbcTemplate jdbc;
    private IdentityClient identities;
    private AssetClient assets;
    private OrganizationClient organizations;
    private StatisticsReportService service;
    private UUID equipmentId;
    private UUID categoryId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        identities = mock(IdentityClient.class);
        assets = mock(AssetClient.class);
        organizations = mock(OrganizationClient.class);
        StatisticsReportCache cache = mock(StatisticsReportCache.class);
        when(cache.getOrLoad(any(), any())).thenAnswer(invocation ->
            ((Supplier<StatisticsReport>) invocation.getArgument(1)).get()
        );
        equipmentId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of(ticket(equipmentId)));
        service = new StatisticsReportService(jdbc, identities, assets, organizations, cache);
    }

    @Test
    void summaryDoesNotResolveEquipmentUsersOrSitesWithoutCategoryFilter() {
        StatisticsReport report = service.get(filter(null));

        assertThat(report.openTotal()).isEqualTo(1);
        assertThat(report.queues().get(TicketWorkQueue.IN_PROGRESS)).isEqualTo(1);
        verifyNoInteractions(assets, identities, organizations);
    }

    @Test
    void summaryOnlyResolvesEquipmentWhenFilteringByCategory() {
        when(assets.resolve(anyCollection())).thenReturn(Map.of(equipmentId, equipment(equipmentId, categoryId)));

        StatisticsReport report = service.get(filter(categoryId));

        assertThat(report.openTotal()).isEqualTo(1);
        verify(assets).resolve(anyCollection());
        verifyNoInteractions(identities, organizations);
    }

    private Filter filter(UUID equipmentCategoryId) {
        return new Filter(null, null, null, equipmentCategoryId, null, null, null);
    }

    private RawTicket ticket(UUID id) {
        Instant now = Instant.now();
        return new RawTicket(
            UUID.randomUUID(), "BT-001", "Kiểm tra thiết bị", UUID.randomUUID(), UUID.randomUUID(),
            id, UUID.randomUUID(), TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM,
            now.minusSeconds(60), now.plusSeconds(60), now.plusSeconds(3600), null, BigDecimal.ZERO
        );
    }

    private AssetClient.EquipmentRef equipment(UUID id, UUID category) {
        return new AssetClient.EquipmentRef(
            id, "TB-001", "Thiết bị", category, "Điều hòa", UUID.randomUUID(), "Địa điểm",
            UUID.randomUUID(), "Khách hàng", null, null, "UNKNOWN", null, true
        );
    }
}
