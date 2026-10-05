package com.example.quanlybaotri.cost.api;

import com.example.quanlybaotri.cost.application.CostReportQueryService;
import com.example.quanlybaotri.cost.application.CostReportQueryService.Report;
import com.example.quanlybaotri.cost.application.CostReportQueryService.Row;
import com.example.quanlybaotri.cost.domain.CostCategory;
import com.example.quanlybaotri.cost.domain.CostStatus;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports/costs")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class CostReportController {
    private final CostReportQueryService reports;

    public CostReportController(CostReportQueryService reports) {
        this.reports = reports;
    }

    @GetMapping
    public Report report(@RequestParam(required=false) Instant from, @RequestParam(required=false) Instant to,
        @RequestParam(required=false) UUID customerId, @RequestParam(required=false) UUID siteId,
        @RequestParam(required=false) UUID equipmentId, @RequestParam(required=false) UUID equipmentCategoryId,
        @RequestParam(required=false) CostCategory category, @RequestParam(required=false) CostStatus status) {
        return reports.load(from, to, customerId, siteId, equipmentId, equipmentCategoryId, category, status);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required=false) Instant from, @RequestParam(required=false) Instant to,
        @RequestParam(required=false) UUID customerId, @RequestParam(required=false) UUID siteId,
        @RequestParam(required=false) UUID equipmentId, @RequestParam(required=false) UUID equipmentCategoryId,
        @RequestParam(required=false) CostCategory category, @RequestParam(required=false) CostStatus status) {
        byte[] bytes = workbook(reports.load(from, to, customerId, siteId, equipmentId, equipmentCategoryId, category, status));
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chi-phi-bao-tri.xlsx")
            .body(bytes);
    }

    private byte[] workbook(Report report) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Chi phí");
            String[] headers = {"Mã phiếu","Khách hàng","Địa điểm","Thiết bị","Nhóm","Mô tả","Số lượng","Đơn vị","Đơn giá","Thành tiền","Ngày phát sinh","Nhà cung cấp","Tham chiếu","Trạng thái"};
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            int index = 1;
            for (Row row : report.rows()) {
                org.apache.poi.ss.usermodel.Row excel = sheet.createRow(index++);
                Object[] values = {row.ticketCode(),row.customerName(),row.siteName(),row.equipmentCode()+" - "+row.equipmentName(),row.category().name(),row.description(),row.quantity(),row.unit(),row.unitCost(),row.amount(),row.incurredAt().toString(),row.vendor(),row.referenceNumber(),row.status().name()};
                for (int column = 0; column < values.length; column++) {
                    Cell cell = excel.createCell(column); Object value = values[column];
                    if (value instanceof Number number) cell.setCellValue(number.doubleValue()); else cell.setCellValue(value == null ? "" : value.toString());
                }
            }
            for (int column = 0; column < headers.length; column++) sheet.autoSizeColumn(column);
            book.write(out); return out.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("Không thể xuất báo cáo chi phí", exception); }
    }

}
