package com.example.quanlybaotri.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.cost.domain.CostStatus;
import com.example.quanlybaotri.reporting.application.StatisticsReportService.*;
import com.example.quanlybaotri.ticket.domain.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class StatisticsReportExcelExporterTest {
    @Test
    void createsQueueSummaryAndUniqueTicketDetailSheets() throws Exception {
        Instant generatedAt = Instant.parse("2026-10-01T10:00:00Z");
        OperationalTicket ticket = new OperationalTicket(
            UUID.randomUUID(), "BT-001", "Thay ổ cứng", UUID.randomUUID(), "Khách hàng A",
            UUID.randomUUID(), "Hà Nội", UUID.randomUUID(), "TB-001", "Máy chủ", "Máy chủ",
            TicketPriority.HIGH, TicketStatus.RESOLVED, UUID.randomUUID(), "Kỹ thuật viên A",
            generatedAt.minusSeconds(7200), generatedAt.minusSeconds(3600),
            generatedAt.plusSeconds(3600), CostStatus.READY, BigDecimal.valueOf(1_500_000),
            List.of(TicketWorkQueue.AWAITING_CLOSURE, TicketWorkQueue.COST_READY)
        );
        EnumMap<TicketWorkQueue, Long> queues = new EnumMap<>(TicketWorkQueue.class);
        for (TicketWorkQueue queue : TicketWorkQueue.values()) queues.put(queue, 0L);
        queues.put(TicketWorkQueue.AWAITING_CLOSURE, 1L);
        queues.put(TicketWorkQueue.COST_READY, 1L);
        Snapshot snapshot = new Snapshot(generatedAt, queues, List.of(ticket));

        byte[] bytes = new StatisticsReportExcelExporter().export(snapshot);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(0)).isEqualTo("Hàng chờ nghiệp vụ");
            assertThat(workbook.getSheetName(1)).isEqualTo("Phiếu tồn đọng");
            assertThat(workbook.getSheet("Phiếu tồn đọng").getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheet("Phiếu tồn đọng").getRow(1).getCell(0).getStringCellValue())
                .isEqualTo("BT-001");
            assertThat(workbook.getSheet("Phiếu tồn đọng").getRow(1).getCell(14).getStringCellValue())
                .contains("Chờ xác nhận đóng", "Chi phí chờ duyệt");
        }
    }
}
