package com.example.quanlybaotri.reporting.api;

import com.example.quanlybaotri.reporting.application.StatisticsReportExcelExporter;
import com.example.quanlybaotri.reporting.application.StatisticsReportService;
import com.example.quanlybaotri.reporting.application.StatisticsReportService.Filter;
import com.example.quanlybaotri.reporting.application.StatisticsReportService.StatisticsReport;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import com.example.quanlybaotri.ticket.domain.WarrantyStatus;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports/statistics")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class StatisticsReportController {
    private final StatisticsReportService reports;
    private final StatisticsReportExcelExporter excel;

    public StatisticsReportController(
        StatisticsReportService reports,
        StatisticsReportExcelExporter excel
    ) {
        this.reports = reports;
        this.excel = excel;
    }

    @GetMapping
    public StatisticsReport report(
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) UUID siteId,
        @RequestParam(required = false) UUID equipmentId,
        @RequestParam(required = false) UUID equipmentCategoryId,
        @RequestParam(required = false) WarrantyStatus warrantyStatus,
        @RequestParam(required = false) TicketPriority priority,
        @RequestParam(required = false) UUID technicianId
    ) {
        return reports.get(filter(customerId, siteId, equipmentId, equipmentCategoryId,
            warrantyStatus, priority, technicianId));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) UUID siteId,
        @RequestParam(required = false) UUID equipmentId,
        @RequestParam(required = false) UUID equipmentCategoryId,
        @RequestParam(required = false) WarrantyStatus warrantyStatus,
        @RequestParam(required = false) TicketPriority priority,
        @RequestParam(required = false) UUID technicianId
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bao-cao-hang-cho-nghiep-vu.xlsx")
            .body(excel.export(reports.snapshot(filter(customerId, siteId, equipmentId,
                equipmentCategoryId, warrantyStatus, priority, technicianId))));
    }

    private Filter filter(
        UUID customerId,
        UUID siteId,
        UUID equipmentId,
        UUID equipmentCategoryId,
        WarrantyStatus warrantyStatus,
        TicketPriority priority,
        UUID technicianId
    ) {
        return new Filter(customerId, siteId, equipmentId, equipmentCategoryId,
            warrantyStatus, priority, technicianId);
    }
}
