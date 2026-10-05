package com.example.quanlybaotri.reporting.application;

import com.example.quanlybaotri.reporting.application.StatisticsReportService.OperationalTicket;
import com.example.quanlybaotri.reporting.application.StatisticsReportService.Snapshot;
import com.example.quanlybaotri.ticket.domain.TicketWorkQueue;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class StatisticsReportExcelExporter {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.of("Asia/Bangkok"));

    public byte[] export(Snapshot snapshot) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle heading = heading(book);
            CellStyle money = money(book);
            queues(book, snapshot, heading);
            tickets(book, snapshot, heading, money);
            book.write(out);
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể xuất báo cáo hàng chờ nghiệp vụ", exception);
        }
    }

    private void queues(Workbook book, Snapshot snapshot, CellStyle heading) {
        Sheet sheet = book.createSheet("Hàng chờ nghiệp vụ");
        org.apache.poi.ss.usermodel.Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("BÁO CÁO HÀNG CHỜ NGHIỆP VỤ");
        title.getCell(0).setCellStyle(heading);
        writePair(sheet.createRow(1), "Thời điểm chốt số liệu", DATE_TIME.format(snapshot.generatedAt()));
        writePair(sheet.createRow(2), "Tổng phiếu chưa kết thúc", snapshot.tickets().size());

        org.apache.poi.ss.usermodel.Row header = sheet.createRow(4);
        cells(header, "Nhóm", "Hàng chờ", "Số phiếu", "Vai trò xử lý", "Điều kiện nghiệp vụ");
        for (int index = 0; index < 5; index++) header.getCell(index).setCellStyle(heading);
        int rowIndex = 5;
        for (TicketWorkQueue queue : TicketWorkQueue.values()) {
            QueueDefinition definition = definition(queue);
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex++);
            cells(row, definition.group(), definition.label(),
                String.valueOf(snapshot.queues().getOrDefault(queue, 0L)),
                definition.owner(), definition.condition());
        }
        for (int column = 0; column < 5; column++) sheet.autoSizeColumn(column);
    }

    private void tickets(Workbook book, Snapshot snapshot, CellStyle heading, CellStyle money) {
        Sheet sheet = book.createSheet("Phiếu tồn đọng");
        String[] headers = {
            "Mã phiếu", "Tiêu đề", "Khách hàng", "Địa điểm", "Thiết bị", "Loại thiết bị",
            "Ưu tiên", "Trạng thái", "Kỹ thuật viên", "Ngày gửi", "Hạn phản hồi",
            "Hạn xử lý", "Trạng thái chi phí", "Tổng chi phí", "Hàng chờ liên quan"
        };
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            header.createCell(index).setCellValue(headers[index]);
            header.getCell(index).setCellStyle(heading);
        }
        int rowIndex = 1;
        for (OperationalTicket ticket : snapshot.tickets()) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex++);
            text(row, 0, ticket.code());
            text(row, 1, ticket.title());
            text(row, 2, ticket.customerName());
            text(row, 3, ticket.siteName());
            text(row, 4, join(ticket.equipmentCode(), ticket.equipmentName()));
            text(row, 5, ticket.equipmentCategoryName());
            text(row, 6, ticket.priority().name());
            text(row, 7, ticket.status().name());
            text(row, 8, ticket.assigneeName());
            text(row, 9, DATE_TIME.format(ticket.submittedAt()));
            text(row, 10, DATE_TIME.format(ticket.responseDueAt()));
            text(row, 11, DATE_TIME.format(ticket.resolutionDueAt()));
            text(row, 12, ticket.costStatus().name());
            Cell amount = row.createCell(13);
            amount.setCellValue(ticket.grandTotal().doubleValue());
            amount.setCellStyle(money);
            text(row, 14, ticket.queues().stream()
                .map(queue -> definition(queue).label())
                .collect(Collectors.joining(", ")));
        }
        sheet.createFreezePane(0, 1);
        for (int column = 0; column < headers.length; column++) sheet.autoSizeColumn(column);
    }

    private QueueDefinition definition(TicketWorkQueue queue) {
        return switch (queue) {
            case RESPONSE_OVERDUE -> new QueueDefinition("Cần xử lý ngay", "Quá hạn phản hồi", "Điều phối", "Phiếu mới gửi đã tới hạn phản hồi");
            case RESOLUTION_DUE_SOON -> new QueueDefinition("Cần xử lý ngay", "Sắp quá hạn xử lý", "Kỹ thuật viên", "Đã dùng ít nhất 80% SLA và chưa tới hạn");
            case RESOLUTION_OVERDUE -> new QueueDefinition("Cần xử lý ngay", "Quá hạn xử lý", "Kỹ thuật viên / Điều phối", "Phiếu đang xử lý đã tới hạn SLA");
            case AWAITING_ACCEPTANCE -> new QueueDefinition("Theo luồng công việc", "Chờ tiếp nhận", "Điều phối", "Trạng thái SUBMITTED");
            case AWAITING_ASSIGNMENT -> new QueueDefinition("Theo luồng công việc", "Chờ phân công", "Điều phối", "Trạng thái ACCEPTED");
            case AWAITING_START -> new QueueDefinition("Theo luồng công việc", "Đã phân công, chưa bắt đầu", "Kỹ thuật viên", "Trạng thái ASSIGNED");
            case IN_PROGRESS -> new QueueDefinition("Theo luồng công việc", "Đang xử lý", "Kỹ thuật viên", "Trạng thái IN_PROGRESS");
            case WAITING_PARTS -> new QueueDefinition("Theo luồng công việc", "Chờ linh kiện", "Kỹ thuật viên / Điều phối", "Trạng thái WAITING_PARTS");
            case AWAITING_CLOSURE -> new QueueDefinition("Theo luồng công việc", "Chờ xác nhận đóng", "Quản trị / Quản lý", "Trạng thái RESOLVED");
            case COST_DRAFT -> new QueueDefinition("Chi phí cần xử lý", "Chi phí chưa gửi duyệt", "Kỹ thuật viên", "Phiếu RESOLVED có chi phí DRAFT");
            case COST_READY -> new QueueDefinition("Chi phí cần xử lý", "Chi phí chờ duyệt", "Quản trị / Quản lý", "Phiếu RESOLVED có chi phí READY");
        };
    }

    private void writePair(org.apache.poi.ss.usermodel.Row row, String label, Object value) {
        row.createCell(0).setCellValue(label);
        if (value instanceof Number number) row.createCell(1).setCellValue(number.doubleValue());
        else row.createCell(1).setCellValue(value == null ? "" : value.toString());
    }

    private void cells(org.apache.poi.ss.usermodel.Row row, String... values) {
        for (int index = 0; index < values.length; index++) row.createCell(index).setCellValue(values[index]);
    }

    private void text(org.apache.poi.ss.usermodel.Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private String join(String code, String name) {
        if (code == null) return name == null ? "" : name;
        return name == null ? code : code + " - " + name;
    }

    private CellStyle heading(Workbook book) {
        Font font = book.createFont();
        font.setBold(true);
        CellStyle style = book.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle money(Workbook book) {
        CellStyle style = book.createCellStyle();
        style.setDataFormat(book.createDataFormat().getFormat("#,##0 [$₫-vi-VN]"));
        return style;
    }

    private record QueueDefinition(String group, String label, String owner, String condition) {}
}
