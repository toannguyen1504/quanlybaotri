package com.example.quanlybaotri.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.ticket.api.TicketController.TicketView;
import com.example.quanlybaotri.ticket.domain.TicketChargeType;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TicketExcelExporterTest {

    private final TicketExcelExporter exporter = new TicketExcelExporter();

    @Test
    void createsFormattedWorkbookWithLocalizedTicketData() throws Exception {
        Instant submittedAt = Instant.parse("2026-09-20T01:00:00Z");
        Instant dueAt = Instant.parse("2026-09-20T05:00:00Z");
        TicketView ticket = new TicketView(
            UUID.randomUUID(),
            "BT-2026-000123",
            UUID.randomUUID(),
            "TB-001",
            "Máy in tầng 5",
            UUID.randomUUID(),
            "Nguyễn Văn A",
            UUID.randomUUID(),
            "Trần Kỹ Thuật",
            "Máy in không nhận giấy",
            "Thiết bị báo lỗi khay giấy",
            TicketPriority.HIGH,
            TicketStatus.RESOLVED,
            submittedAt,
            submittedAt.plusSeconds(1800),
            dueAt,
            submittedAt.plusSeconds(600),
            submittedAt.plusSeconds(1200),
            dueAt.plusSeconds(60),
            dueAt.plusSeconds(120),
            "Đã thay bộ cuốn giấy",
            TicketChargeType.PAID,
            new BigDecimal("150000.00"),
            new BigDecimal("150000.00"),
            2L
        );

        byte[] content = exporter.export(
            List.of(ticket),
            Instant.parse("2026-09-21T03:30:00Z")
        );

        assertThat(content).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheet("Phiếu bảo trì");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                .isEqualTo("DANH SÁCH PHIẾU BẢO TRÌ");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                .contains("21/09/2026 10:30")
                .contains("Tổng số 1 phiếu");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("Mã phiếu");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue())
                .isEqualTo("BT-2026-000123");
            assertThat(sheet.getRow(3).getCell(6).getStringCellValue()).isEqualTo("Cao");
            assertThat(sheet.getRow(3).getCell(16).getStringCellValue())
                .isEqualTo("Hoàn tất quá hạn");
            assertThat(sheet.getRow(3).getCell(20).getNumericCellValue()).isEqualTo(150000d);
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
        }
    }

    @Test
    void createsAValidWorkbookWhenNoTicketsMatch() throws Exception {
        byte[] content = exporter.export(List.of(), Instant.parse("2026-09-21T03:30:00Z"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheet("Phiếu bảo trì");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                .contains("Tổng số 0 phiếu");
            assertThat(sheet.getCTWorksheet().isSetAutoFilter()).isTrue();
        }
    }
}
