package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.ticket.api.TicketController.TicketView;
import com.example.quanlybaotri.ticket.domain.TicketChargeType;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class TicketExcelExporter {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern(
        "dd/MM/yyyy HH:mm"
    ).withZone(VIETNAM_ZONE);
    private static final String[] HEADERS = {
        "STT",
        "Mã phiếu",
        "Mã thiết bị",
        "Tên thiết bị",
        "Tiêu đề",
        "Mô tả sự cố",
        "Mức ưu tiên",
        "Trạng thái",
        "Người yêu cầu",
        "Người phụ trách",
        "Ngày gửi",
        "Hạn phản hồi",
        "Đã tiếp nhận",
        "Bắt đầu xử lý",
        "Hạn xử lý",
        "Hoàn tất xử lý",
        "Tình trạng SLA",
        "Kết quả xử lý",
        "Hình thức chi phí",
        "Chi phí linh kiện",
        "Thành tiền",
    };
    private static final int[] WIDTHS = {
        7, 20, 18, 28, 36, 48, 16, 20, 24, 24, 20, 20, 20, 20, 20, 20, 18, 42, 20, 20, 20,
    };

    public byte[] export(List<TicketView> tickets, Instant generatedAt) {
        try (
            XSSFWorkbook workbook = new XSSFWorkbook();
            ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            Sheet sheet = workbook.createSheet("Phiếu bảo trì");
            Styles styles = createStyles(workbook);
            writeTitle(sheet, styles, tickets.size(), generatedAt);
            writeHeaders(sheet, styles.header());

            int rowIndex = 3;
            for (int index = 0; index < tickets.size(); index++) {
                writeTicket(
                    sheet.createRow(rowIndex++),
                    tickets.get(index),
                    index + 1,
                    generatedAt,
                    styles
                );
            }

            sheet.createFreezePane(0, 3);
            int lastRow = Math.max(2, rowIndex - 1);
            sheet.setAutoFilter(new CellRangeAddress(2, lastRow, 0, HEADERS.length - 1));
            for (int index = 0; index < WIDTHS.length; index++) {
                sheet.setColumnWidth(index, WIDTHS[index] * 256);
            }
            sheet.setRepeatingRows(new CellRangeAddress(2, 2, -1, -1));
            sheet.setDisplayGridlines(false);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo tệp Excel phiếu bảo trì", exception);
        }
    }

    private void writeTitle(Sheet sheet, Styles styles, int count, Instant generatedAt) {
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        Cell title = titleRow.createCell(0);
        title.setCellValue("DANH SÁCH PHIẾU BẢO TRÌ");
        title.setCellStyle(styles.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

        Row metadataRow = sheet.createRow(1);
        metadataRow.setHeightInPoints(24);
        Cell metadata = metadataRow.createCell(0);
        metadata.setCellValue(
            "Xuất lúc " + DISPLAY_TIME.format(generatedAt) + " · Tổng số " + count + " phiếu"
        );
        metadata.setCellStyle(styles.metadata());
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, HEADERS.length - 1));
    }

    private void writeHeaders(Sheet sheet, CellStyle style) {
        Row row = sheet.createRow(2);
        row.setHeightInPoints(27);
        for (int index = 0; index < HEADERS.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(HEADERS[index]);
            cell.setCellStyle(style);
        }
    }

    private void writeTicket(
        Row row,
        TicketView ticket,
        int order,
        Instant generatedAt,
        Styles styles
    ) {
        row.setHeightInPoints(34);
        CellStyle textStyle = order % 2 == 0 ? styles.evenText() : styles.text();
        CellStyle numberStyle = order % 2 == 0 ? styles.evenNumber() : styles.number();

        setNumber(row, 0, order, numberStyle);
        setText(row, 1, ticket.code(), textStyle);
        setText(row, 2, ticket.equipmentCode(), textStyle);
        setText(row, 3, ticket.equipmentName(), textStyle);
        setText(row, 4, ticket.title(), textStyle);
        setText(row, 5, ticket.description(), textStyle);
        setText(row, 6, priorityLabel(ticket.priority()), textStyle);
        setText(row, 7, statusLabel(ticket.status()), textStyle);
        setText(row, 8, ticket.requesterName(), textStyle);
        setText(row, 9, ticket.assigneeName() == null ? "Chưa phân công" : ticket.assigneeName(), textStyle);
        setDate(row, 10, ticket.submittedAt(), textStyle);
        setDate(row, 11, ticket.responseDueAt(), textStyle);
        setDate(row, 12, ticket.acceptedAt(), textStyle);
        setDate(row, 13, ticket.startedAt(), textStyle);
        setDate(row, 14, ticket.resolutionDueAt(), textStyle);
        setDate(row, 15, ticket.resolvedAt(), textStyle);
        setText(row, 16, slaLabel(ticket, generatedAt), textStyle);
        setText(row, 17, ticket.resolutionSummary(), textStyle);
        setText(row, 18, chargeTypeLabel(ticket.chargeType()), textStyle);
        setCurrency(row, 19, ticket.partsCost(), numberStyle);
        setCurrency(row, 20, ticket.chargeAmount(), numberStyle);
    }

    private void setText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void setDate(Row row, int column, Instant value, CellStyle style) {
        setText(row, column, value == null ? "" : DISPLAY_TIME.format(value), style);
    }

    private void setNumber(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setCurrency(Row row, int column, BigDecimal value, CellStyle style) {
        setNumber(row, column, value == null ? 0 : value.doubleValue(), style);
    }

    private String priorityLabel(TicketPriority priority) {
        return switch (priority) {
            case CRITICAL -> "Khẩn cấp";
            case HIGH -> "Cao";
            case MEDIUM -> "Trung bình";
            case LOW -> "Thấp";
        };
    }

    private String statusLabel(TicketStatus status) {
        return switch (status) {
            case SUBMITTED -> "Mới gửi";
            case ACCEPTED -> "Đã tiếp nhận";
            case ASSIGNED -> "Đã phân công";
            case IN_PROGRESS -> "Đang xử lý";
            case WAITING_PARTS -> "Chờ linh kiện";
            case RESOLVED -> "Đã xử lý";
            case CLOSED -> "Đã đóng";
            case REJECTED -> "Từ chối";
            case CANCELLED -> "Đã hủy";
        };
    }

    private String chargeTypeLabel(TicketChargeType chargeType) {
        if (chargeType == null) return "Chưa xác định";
        return switch (chargeType) {
            case PENDING -> "Chưa xác định";
            case FREE -> "Miễn phí";
            case PAID -> "Trả phí";
        };
    }

    private String slaLabel(TicketView ticket, Instant now) {
        if (ticket.status() == TicketStatus.REJECTED || ticket.status() == TicketStatus.CANCELLED) {
            return "Không áp dụng";
        }
        if (ticket.resolvedAt() != null) {
            return ticket.resolvedAt().isAfter(ticket.resolutionDueAt()) ? "Hoàn tất quá hạn" : "Hoàn tất đúng hạn";
        }
        if (!now.isBefore(ticket.resolutionDueAt())) return "Quá hạn";
        long totalMillis = ticket.resolutionDueAt().toEpochMilli() - ticket.submittedAt().toEpochMilli();
        Instant warningAt = ticket.submittedAt().plusMillis(Math.round(totalMillis * 0.8));
        return now.isBefore(warningAt) ? "Đúng tiến độ" : "Sắp quá hạn";
    }

    private Styles createStyles(Workbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setColor(IndexedColors.WHITE.getIndex());

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        title.setAlignment(HorizontalAlignment.LEFT);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        Font metadataFont = workbook.createFont();
        metadataFont.setItalic(true);
        metadataFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        CellStyle metadata = workbook.createCellStyle();
        metadata.setFont(metadataFont);
        metadata.setVerticalAlignment(VerticalAlignment.CENTER);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.TEAL.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(header);

        CellStyle text = workbook.createCellStyle();
        text.setVerticalAlignment(VerticalAlignment.TOP);
        text.setWrapText(true);
        applyBorders(text);

        CellStyle evenText = workbook.createCellStyle();
        evenText.cloneStyleFrom(text);
        evenText.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        evenText.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle number = workbook.createCellStyle();
        number.cloneStyleFrom(text);
        number.setDataFormat(dataFormat.getFormat("#,##0"));
        number.setAlignment(HorizontalAlignment.RIGHT);
        CellStyle evenNumber = workbook.createCellStyle();
        evenNumber.cloneStyleFrom(number);
        evenNumber.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        evenNumber.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        return new Styles(title, metadata, header, text, evenText, number, evenNumber);
    }

    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        short color = IndexedColors.GREY_25_PERCENT.getIndex();
        style.setTopBorderColor(color);
        style.setRightBorderColor(color);
        style.setBottomBorderColor(color);
        style.setLeftBorderColor(color);
    }

    private record Styles(
        CellStyle title,
        CellStyle metadata,
        CellStyle header,
        CellStyle text,
        CellStyle evenText,
        CellStyle number,
        CellStyle evenNumber
    ) {}
}
