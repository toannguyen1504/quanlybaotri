package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.telegram.application.InventoryWorkflowClient.*;
import com.example.quanlybaotri.telegram.application.MaintenanceTicketClient.*;
import com.example.quanlybaotri.telegram.domain.*;
import com.example.quanlybaotri.telegram.domain.TelegramConversation.State;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.*;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TelegramWorkflowProcessor {
    private static final String HELP = """
        Bot hỗ trợ xử lý phiếu bảo trì ngay trong Telegram.
        /phieu - Danh sách phiếu được giao
        /huy - Hủy bước nhập hiện tại
        /trogiup - Xem hướng dẫn
        """;

    private final TelegramLinkRepository links;
    private final TelegramConversationService conversations;
    private final MaintenanceTicketClient maintenance;
    private final InventoryWorkflowClient inventory;
    private final TelegramClient client;
    private final MeterRegistry metrics;

    public TelegramWorkflowProcessor(TelegramLinkRepository links,
        TelegramConversationService conversations, MaintenanceTicketClient maintenance,
        InventoryWorkflowClient inventory, TelegramClient client, MeterRegistry metrics) {
        this.links = links;
        this.conversations = conversations;
        this.maintenance = maintenance;
        this.inventory = inventory;
        this.client = client;
        this.metrics = metrics;
    }

    public boolean supports(TelegramClient.Update update) {
        String text = normalized(update.text());
        return (update.callbackData() != null && update.callbackData().startsWith("wf:")) ||
            text.equals("/phieu") || text.equals("/huy") || text.equals("/trogiup") ||
            conversations.get(linkedUser(update)) != null;
    }

    public void process(TelegramClient.Update update) {
        if (!"private".equals(update.chatType())) return;
        TelegramLink link = validLink(update);
        if (link == null) {
            if (update.isCallbackQuery()) answer(update, "Tài khoản Telegram chưa được liên kết.", true);
            else send(update.chatId(), "Vui lòng liên kết Telegram từ trang Hồ sơ trước khi sử dụng bot.", List.of());
            return;
        }
        Timer.Sample latency = Timer.start(metrics);
        try {
            if (update.isCallbackQuery()) {
                answer(update, null, false);
                callback(update, link);
            } else message(update, link);
            metrics.counter("telegram.workflow.commands", "result", "success").increment();
        } catch (RuntimeException failure) {
            metrics.counter("telegram.workflow.commands", "result", "failure").increment();
            send(update.chatId(), "Không thể thực hiện thao tác lúc này. Dữ liệu nhập vẫn được giữ; hãy thử lại hoặc dùng /huy.", List.of());
        } finally {
            latency.stop(metrics.timer("telegram.workflow.duration"));
        }
    }

    public void openTicket(UUID userId, UUID ticketId, long chatId) {
        conversations.select(userId, ticketId);
        showMenu(chatId, userId, ticketId);
    }

    private void message(TelegramClient.Update update, TelegramLink link) {
        String text = normalized(update.text());
        if (text.equals("/phieu")) { showTickets(update.chatId(), link.getUserId(), 0); return; }
        if (text.equals("/trogiup")) { send(update.chatId(), HELP, List.of()); return; }
        if (text.equals("/huy")) {
            TelegramConversation current = conversations.get(link.getUserId());
            if (current == null || current.getTicketId() == null) showTickets(update.chatId(), link.getUserId(), 0);
            else { conversations.menu(link.getUserId()); showMenu(update.chatId(), link.getUserId(), current.getTicketId()); }
            return;
        }
        TelegramConversation conversation = conversations.get(link.getUserId());
        if (conversation == null || conversation.getTicketId() == null) {
            send(update.chatId(), "Chưa chọn phiếu. Dùng /phieu để xem danh sách.", List.of());
            return;
        }
        consume(update, link, conversation);
    }

    private void callback(TelegramClient.Update update, TelegramLink link) {
        String data = update.callbackData();
        if (data.startsWith("wf:list:")) {
            showTickets(update.chatId(), link.getUserId(), integer(data.substring(8), 0));
            return;
        }
        if (data.startsWith("wf:open:")) {
            UUID ticketId = UUID.fromString(data.substring(8));
            maintenance.get(ticketId, link.getUserId());
            openTicket(link.getUserId(), ticketId, update.chatId());
            return;
        }
        TelegramConversation conversation = conversations.get(link.getUserId());
        if (conversation == null || conversation.getTicketId() == null) {
            send(update.chatId(), "Phiên thao tác đã hết hạn. Dùng /phieu để chọn lại phiếu.", List.of());
            metrics.counter("telegram.workflow.sessions", "result", "expired").increment();
            return;
        }
        UUID ticketId = conversation.getTicketId();
        Map<String, String> draft = conversations.draft(conversation);
        switch (data) {
            case "wf:menu", "wf:cancel", "wf:back" -> {
                conversations.menu(link.getUserId());
                showMenu(update.chatId(), link.getUserId(), ticketId);
            }
            case "wf:start" -> {
                maintenance.action(ticketId, link.getUserId(), "start", key(update));
                conversations.menu(link.getUserId());
                showMenu(update.chatId(), link.getUserId(), ticketId);
            }
            case "wf:log" -> prompt(update.chatId(), link.getUserId(), State.LOG_CONTENT,
                new LinkedHashMap<>(), "Nhập công việc đã thực hiện:");
            case "wf:wait" -> prompt(update.chatId(), link.getUserId(), State.WAIT_REASON,
                new LinkedHashMap<>(), "Nhập lý do chờ linh kiện:");
            case "wf:resume" -> {
                maintenance.action(ticketId, link.getUserId(), "resume", key(update));
                conversations.menu(link.getUserId());
                showMenu(update.chatId(), link.getUserId(), ticketId);
            }
            case "wf:resolve" -> prompt(update.chatId(), link.getUserId(), State.RESOLVE_SUMMARY,
                new LinkedHashMap<>(), "Nhập kết quả xử lý để hoàn tất phiếu:");
            case "wf:part" -> prompt(update.chatId(), link.getUserId(), State.PART_SEARCH,
                new LinkedHashMap<>(), "Nhập mã hoặc tên linh kiện cần tìm:");
            case "wf:cost" -> showCosts(update.chatId(), link.getUserId(), ticketId);
            case "wf:ca" -> chooseCostCategory(update.chatId(), link.getUserId(), new LinkedHashMap<>());
            case "wf:attach" -> chooseAttachmentType(update.chatId(), link.getUserId());
            case "wf:al" -> showAttachments(update.chatId(), link.getUserId(), ticketId);
            case "wf:submit" -> confirmSubmit(update.chatId(), link.getUserId(), ticketId);
            case "wf:subyes" -> {
                CostView result = maintenance.quoteCosts(ticketId, link.getUserId(), key(update));
                conversations.menu(link.getUserId());
                send(update.chatId(), "✅ Đã gửi báo giá cho khách hàng. Trạng thái: " + costStatus(result.status()), menuBack());
            }
            case "wf:yes" -> confirm(update, link, conversation, draft);
            case "wf:skip" -> skip(update.chatId(), link.getUserId(), conversation, draft);
            case "wf:aig" -> attachmentReady(update.chatId(), link.getUserId(), draft, null);
            default -> dynamicCallback(update, link, conversation, draft, data);
        }
    }

    private void dynamicCallback(TelegramClient.Update update, TelegramLink link,
        TelegramConversation conversation, Map<String, String> draft, String data) {
        if (data.startsWith("wf:pp:")) {
            showPartResults(update.chatId(), link.getUserId(), draft.getOrDefault("query", ""),
                integer(data.substring(6), 0));
        } else if (data.startsWith("wf:ps:")) {
            UUID id = UUID.fromString(data.substring(6));
            PartRef part = inventory.search(draft.getOrDefault("query", ""),
                integer(draft.get("page"), 0)).content().stream()
                .filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Part selection is stale"));
            draft.put("partId", id.toString());
            draft.put("part", part.code() + " · " + part.name() + " (tồn " + part.currentStock() + " " + part.unit() + ")");
            draft.put("unit", part.unit());
            prompt(update.chatId(), link.getUserId(), State.PART_QUANTITY, draft,
                "Nhập số lượng sử dụng (" + part.unit() + "):");
        } else if (data.startsWith("wf:cc:")) {
            draft.put("category", "OTHER");
            prompt(update.chatId(), link.getUserId(), State.COST_DESCRIPTION, draft,
                "Nhập rõ nội dung chi phí phát sinh (bắt buộc):");
        } else if (data.startsWith("wf:ce:")) {
            UUID itemId = UUID.fromString(data.substring(6));
            CostItemRef item = findCost(conversation.getTicketId(), link.getUserId(), itemId);
            draft = costDraft(item);
            chooseCostCategory(update.chatId(), link.getUserId(), draft);
        } else if (data.startsWith("wf:cd:")) {
            draft.clear();
            draft.put("operation", "DELETE_COST");
            draft.put("itemId", data.substring(6));
            ensureCommandKey(draft);
            conversations.step(link.getUserId(), State.COST_CONFIRM, draft);
            send(update.chatId(), "Xác nhận xóa khoản chi phí này?", confirmRows());
        } else if (data.startsWith("wf:at:")) {
            draft.clear();
            draft.put("attachmentType", data.substring(6));
            if ("INVOICE".equals(draft.get("attachmentType"))) chooseInvoiceCost(update.chatId(), link.getUserId(), draft, conversation.getTicketId());
            else attachmentReady(update.chatId(), link.getUserId(), draft, null);
        } else if (data.startsWith("wf:ai:")) {
            attachmentReady(update.chatId(), link.getUserId(), draft, UUID.fromString(data.substring(6)));
        } else if (data.startsWith("wf:ad:")) {
            draft.clear();
            draft.put("attachmentId", data.substring(6));
            ensureCommandKey(draft);
            conversations.step(link.getUserId(), State.ATTACHMENT_DELETE_CONFIRM, draft);
            send(update.chatId(), "Xác nhận xóa tệp này?", confirmRows());
        } else {
            send(update.chatId(), "Nút thao tác đã hết hiệu lực. Đang tải lại phiếu.", menuBack());
        }
    }

    private void consume(TelegramClient.Update update, TelegramLink link, TelegramConversation conversation) {
        Map<String, String> draft = conversations.draft(conversation);
        String text = update.text() == null ? "" : update.text().trim();
        switch (conversation.getState()) {
            case LOG_CONTENT -> {
                required(text); draft.put("content", text);
                prompt(update.chatId(), link.getUserId(), State.LOG_MINUTES, draft, "Nhập số phút thực hiện (0–1440):");
            }
            case LOG_MINUTES -> {
                int minutes = integerStrict(text, 0, 1440); draft.put("minutes", String.valueOf(minutes));
                ensureCommandKey(draft);
                conversations.step(link.getUserId(), State.LOG_CONFIRM, draft);
                send(update.chatId(), "Xác nhận nhật ký:\n" + draft.get("content") + "\nThời gian: " + minutes + " phút", confirmRows());
            }
            case WAIT_REASON -> {
                required(text); draft.put("reason", text);
                ensureCommandKey(draft);
                conversations.step(link.getUserId(), State.WAIT_CONFIRM, draft);
                send(update.chatId(), "Xác nhận chuyển sang chờ linh kiện?\n" + text, confirmRows());
            }
            case RESOLVE_SUMMARY -> {
                required(text); draft.put("summary", text);
                ensureCommandKey(draft);
                conversations.step(link.getUserId(), State.RESOLVE_CONFIRM, draft);
                send(update.chatId(), "Xác nhận hoàn tất xử lý?\n" + text, confirmRows());
            }
            case PART_SEARCH -> {
                required(text); draft.put("query", text);
                conversations.step(link.getUserId(), State.PART_SEARCH, draft);
                showPartResults(update.chatId(), link.getUserId(), text, 0);
            }
            case PART_QUANTITY -> {
                BigDecimal quantity = decimal(text, true); draft.put("quantity", quantity.toPlainString());
                ensureCommandKey(draft);
                conversations.step(link.getUserId(), State.PART_CONFIRM, draft);
                send(update.chatId(), "Xác nhận xuất kho:\n" + draft.get("part") + "\nSố lượng: " + quantity, confirmRows());
            }
            case COST_DESCRIPTION -> { required(text); draft.put("description", text); prompt(update.chatId(), link.getUserId(), State.COST_QUANTITY, draft, "Nhập số lượng:"); }
            case COST_QUANTITY -> { draft.put("quantity", decimal(text, true).toPlainString()); prompt(update.chatId(), link.getUserId(), State.COST_UNIT, draft, "Nhập đơn vị (lần, km, giờ...):"); }
            case COST_UNIT -> { required(text); draft.put("unit", text); prompt(update.chatId(), link.getUserId(), State.COST_UNIT_COST, draft, "Nhập đơn giá (VNĐ):"); }
            case COST_UNIT_COST -> { draft.put("unitCost", decimal(text, false).toPlainString()); promptOptional(update.chatId(), link.getUserId(), State.COST_VENDOR, draft, "Nhập nhà cung cấp hoặc bấm Bỏ qua:"); }
            case COST_VENDOR -> { if (!"-".equals(text)) draft.put("vendor", text); promptOptional(update.chatId(), link.getUserId(), State.COST_REFERENCE, draft, "Nhập số tham chiếu hoặc bấm Bỏ qua:"); }
            case COST_REFERENCE -> { if (!"-".equals(text)) draft.put("reference", text); costConfirmation(update.chatId(), link.getUserId(), draft); }
            case ATTACHMENT_FILE -> upload(update, link, conversation, draft);
            default -> send(update.chatId(), "Hãy dùng các nút trong menu hoặc /huy để hủy bước nhập.", menuBack());
        }
    }

    private void confirm(TelegramClient.Update update, TelegramLink link,
        TelegramConversation conversation, Map<String, String> draft) {
        UUID ticketId = conversation.getTicketId();
        switch (conversation.getState()) {
            case LOG_CONFIRM -> maintenance.addWorkLog(ticketId, link.getUserId(), draft.get("content"),
                integer(draft.get("minutes"), 0), commandKey(draft, update));
            case WAIT_CONFIRM -> maintenance.reason(ticketId, link.getUserId(), "wait-parts", draft.get("reason"), commandKey(draft, update));
            case RESOLVE_CONFIRM -> {
                maintenance.reason(ticketId, link.getUserId(), "resolve", draft.get("summary"), commandKey(draft, update));
                conversations.menu(link.getUserId());
                showCosts(update.chatId(), link.getUserId(), ticketId);
                return;
            }
            case PART_CONFIRM -> inventory.use(ticketId, link.getUserId(), UUID.fromString(draft.get("partId")),
                new BigDecimal(draft.get("quantity")), commandKey(draft, update));
            case COST_CONFIRM -> {
                if ("DELETE_COST".equals(draft.get("operation"))) maintenance.deleteCost(ticketId,
                    UUID.fromString(draft.get("itemId")), link.getUserId(), commandKey(draft, update));
                else saveCost(ticketId, link.getUserId(), draft, commandKey(draft, update));
            }
            case ATTACHMENT_DELETE_CONFIRM -> maintenance.deleteAttachment(
                ticketId, UUID.fromString(draft.get("attachmentId")), link.getUserId(),
                commandKey(draft, update)
            );
            default -> { showMenu(update.chatId(), link.getUserId(), ticketId); return; }
        }
        conversations.menu(link.getUserId());
        send(update.chatId(), "✅ Đã cập nhật phiếu.", menuBack());
    }

    private void saveCost(UUID ticketId, UUID technicianId, Map<String, String> draft, String key) {
        CostRequest request = new CostRequest(technicianId, draft.get("category"), draft.get("description"),
            new BigDecimal(draft.get("quantity")), draft.get("unit"), new BigDecimal(draft.get("unitCost")),
            Instant.now().toString(), blankToNull(draft.get("vendor")), blankToNull(draft.get("reference")));
        if (draft.containsKey("itemId")) maintenance.updateCost(ticketId, UUID.fromString(draft.get("itemId")), request, key);
        else maintenance.addCost(ticketId, request, key);
    }

    private void upload(TelegramClient.Update update, TelegramLink link,
        TelegramConversation conversation, Map<String, String> draft) {
        if (update.media() == null) {
            send(update.chatId(), "Hãy gửi ảnh, video hoặc tài liệu. Giới hạn tối đa 20 MB.", cancelRows());
            return;
        }
        try (TelegramClient.DownloadedFile file = client.download(update.media())) {
            UUID costItemId = draft.containsKey("costItemId") ? UUID.fromString(draft.get("costItemId")) : null;
            maintenance.upload(conversation.getTicketId(), link.getUserId(), draft.get("attachmentType"),
                costItemId, file, key(update));
            metrics.counter("telegram.workflow.files", "result", "success").increment();
        } catch (RuntimeException failure) {
            metrics.counter("telegram.workflow.files", "result", "failure").increment();
            throw failure;
        }
        send(update.chatId(), "✅ Đã lưu tệp. Bạn có thể gửi thêm tệp cùng loại hoặc bấm Xong.",
            List.of(List.of(button("Xong", "wf:back"), button("Hủy", "wf:cancel"))));
    }

    private void showTickets(long chatId, UUID userId, int page) {
        TicketPage result = maintenance.list(userId, Math.max(0, page));
        if (result.content() == null || result.content().isEmpty()) {
            send(chatId, "Bạn không có phiếu đang xử lý.", List.of()); return;
        }
        List<List<TelegramClient.InlineButton>> rows = new ArrayList<>();
        for (TicketRef ticket : result.content()) rows.add(List.of(button(
            ticket.code() + " · " + status(ticket.status()), "wf:open:" + ticket.id())));
        List<TelegramClient.InlineButton> nav = new ArrayList<>();
        if (page > 0) nav.add(button("‹ Trước", "wf:list:" + (page - 1)));
        if (page + 1 < result.totalPages()) nav.add(button("Sau ›", "wf:list:" + (page + 1)));
        if (!nav.isEmpty()) rows.add(nav);
        send(chatId, "🔧 Phiếu bảo trì của bạn (" + result.totalElements() + ")", rows);
    }

    private void showMenu(long chatId, UUID userId, UUID ticketId) {
        TicketRef ticket = maintenance.get(ticketId, userId);
        List<List<TelegramClient.InlineButton>> rows = new ArrayList<>();
        switch (ticket.status()) {
            case "ASSIGNED" -> rows.add(List.of(button("Tiếp nhận phiếu", "wf:start")));
            case "IN_PROGRESS" -> {
                rows.add(List.of(button("Thêm nhật ký", "wf:log"), button("Minh chứng", "wf:attach")));
                rows.add(List.of(button("Dùng linh kiện", "wf:part"), button("Chi phí", "wf:cost")));
                rows.add(List.of(button("Chờ linh kiện", "wf:wait"), button("Hoàn tất xử lý", "wf:resolve")));
            }
            case "WAITING_PARTS" -> {
                rows.add(List.of(button("Thêm nhật ký", "wf:log"), button("Minh chứng", "wf:attach")));
                rows.add(List.of(button("Dùng linh kiện", "wf:part"), button("Chi phí", "wf:cost")));
                rows.add(List.of(button("Tiếp tục xử lý", "wf:resume")));
            }
            case "RESOLVED" -> rows.add(List.of(button("Xem và gửi chi phí", "wf:cost"), button("Minh chứng", "wf:attach")));
            default -> {}
        }
        rows.add(List.of(button("Danh sách phiếu", "wf:list:0")));
        send(chatId, ticketText(ticket), rows);
    }

    private void showPartResults(long chatId, UUID userId, String query, int page) {
        PartPage result = inventory.search(query, Math.max(0, page));
        TelegramConversation current = conversations.get(userId);
        Map<String, String> draft = conversations.draft(current);
        draft.put("query", query); draft.put("page", String.valueOf(page));
        conversations.step(userId, State.PART_SEARCH, draft);
        List<List<TelegramClient.InlineButton>> rows = new ArrayList<>();
        for (PartRef part : result.content()) rows.add(List.of(button(
            part.code() + " · " + part.name() + " · tồn " + part.currentStock(), "wf:ps:" + part.id())));
        List<TelegramClient.InlineButton> nav = new ArrayList<>();
        if (page > 0) nav.add(button("‹ Trước", "wf:pp:" + (page - 1)));
        if (page + 1 < result.totalPages()) nav.add(button("Sau ›", "wf:pp:" + (page + 1)));
        if (!nav.isEmpty()) rows.add(nav);
        rows.add(List.of(button("Tìm lại", "wf:part"), button("Hủy", "wf:cancel")));
        send(chatId, result.content().isEmpty() ? "Không tìm thấy linh kiện." : "Chọn linh kiện:", rows);
    }

    private void showCosts(long chatId, UUID userId, UUID ticketId) {
        CostView costs = maintenance.costs(ticketId, userId);
        List<List<TelegramClient.InlineButton>> rows = new ArrayList<>();
        if ("DRAFT".equals(costs.status())) {
            rows.add(List.of(button("Thêm chi phí phát sinh", "wf:ca")));
            if (costs.items() != null) for (CostItemRef item : costs.items()) {
                if (!"MANUAL".equals(item.sourceType())) continue;
                rows.add(List.of(button("Sửa: " + shorten(item.description(), 20), "wf:ce:" + item.id()),
                    button("Xóa", "wf:cd:" + item.id())));
            }
            if ("RESOLVED".equals(maintenance.get(ticketId, userId).status()))
                rows.add(List.of(button("Gửi báo giá", "wf:submit")));
        }
        rows.add(List.of(button("Về phiếu", "wf:menu")));
        send(chatId, costText(costs), rows);
    }

    private void chooseCostCategory(long chatId, UUID userId, Map<String, String> draft) {
        draft.put("category", "OTHER");
        prompt(chatId, userId, State.COST_DESCRIPTION, draft,
            "Nhập rõ nội dung chi phí phát sinh (bắt buộc):");
    }

    private void chooseAttachmentType(long chatId, UUID userId) {
        conversations.step(userId, State.ATTACHMENT_TYPE, new LinkedHashMap<>());
        send(chatId, "Chọn loại minh chứng:", List.of(
            List.of(button("Ảnh trước sửa", "wf:at:BEFORE_REPAIR"), button("Ảnh sau sửa", "wf:at:AFTER_REPAIR")),
            List.of(button("Báo cáo", "wf:at:REPORT"), button("Hóa đơn", "wf:at:INVOICE")),
            List.of(button("Tệp khác", "wf:at:OTHER"), button("Tệp đã tải", "wf:al")),
            List.of(button("Hủy", "wf:cancel"))));
    }

    private void showAttachments(long chatId, UUID userId, UUID ticketId) {
        List<AttachmentRef> files = maintenance.attachments(ticketId, userId);
        List<List<TelegramClient.InlineButton>> rows = new ArrayList<>();
        for (AttachmentRef file : files) if (file.canDelete()) rows.add(List.of(
            button("Xóa: " + shorten(file.originalName(), 28), "wf:ad:" + file.id())
        ));
        rows.add(List.of(button("Thêm tệp", "wf:attach"), button("Về phiếu", "wf:menu")));
        send(chatId, files.isEmpty() ? "Phiếu chưa có tệp đính kèm." :
            "Tệp đã tải: " + files.size() + ". Chỉ các tệp bạn được phép xóa mới có nút Xóa.", rows);
    }

    private void chooseInvoiceCost(long chatId, UUID userId, Map<String, String> draft, UUID ticketId) {
        CostView costs = maintenance.costs(ticketId, userId);
        List<List<TelegramClient.InlineButton>> rows = new ArrayList<>();
        rows.add(List.of(button("Chứng từ chung", "wf:aig")));
        if (costs.items() != null) for (CostItemRef item : costs.items())
            rows.add(List.of(button(shorten(item.description(), 32), "wf:ai:" + item.id())));
        rows.add(List.of(button("Hủy", "wf:cancel")));
        conversations.step(userId, State.ATTACHMENT_TYPE, draft);
        send(chatId, "Gắn hóa đơn với khoản chi phí nào?", rows);
    }

    private void attachmentReady(long chatId, UUID userId, Map<String, String> draft, UUID costItemId) {
        if (costItemId != null) draft.put("costItemId", costItemId.toString());
        conversations.step(userId, State.ATTACHMENT_FILE, draft);
        send(chatId, "Gửi ảnh, video hoặc tài liệu (tối đa 20 MB).", cancelRows());
    }

    private void confirmSubmit(long chatId, UUID userId, UUID ticketId) {
        CostView costs = maintenance.costs(ticketId, userId);
        if (!costs.complete()) {
            send(chatId, costText(costs) + "\n\n⚠️ Chưa có đơn giá cho một hoặc nhiều nhật ký. Hãy nhờ quản lý cấu hình trước khi gửi.", menuBack());
            return;
        }
        send(chatId, costText(costs) + "\n\nXác nhận gửi báo giá để khách hàng xem và quyết định?",
            List.of(List.of(button("Xác nhận gửi", "wf:subyes"), button("Quay lại", "wf:cost"))));
    }

    private void costConfirmation(long chatId, UUID userId, Map<String, String> draft) {
        ensureCommandKey(draft);
        conversations.step(userId, State.COST_CONFIRM, draft);
        BigDecimal amount = new BigDecimal(draft.get("quantity")).multiply(new BigDecimal(draft.get("unitCost")));
        send(chatId, "Xác nhận chi phí:\n" + draft.get("description") + "\n" + draft.get("quantity") + " " +
            draft.get("unit") + " × " + money(new BigDecimal(draft.get("unitCost"))) + " = " + money(amount), confirmRows());
    }

    private void skip(long chatId, UUID userId, TelegramConversation conversation, Map<String, String> draft) {
        if (conversation.getState() == State.COST_VENDOR)
            promptOptional(chatId, userId, State.COST_REFERENCE, draft, "Nhập số tham chiếu hoặc bấm Bỏ qua:");
        else if (conversation.getState() == State.COST_REFERENCE) costConfirmation(chatId, userId, draft);
        else send(chatId, "Không thể bỏ qua bước này.", cancelRows());
    }

    private CostItemRef findCost(UUID ticketId, UUID userId, UUID itemId) {
        return maintenance.costs(ticketId, userId).items().stream().filter(item -> item.id().equals(itemId))
            .findFirst().orElseThrow(() -> new IllegalStateException("Cost item is stale"));
    }

    private static Map<String, String> costDraft(CostItemRef item) {
        Map<String, String> draft = new LinkedHashMap<>();
        draft.put("itemId", item.id().toString());
        if (item.category() != null) draft.put("category", item.category());
        if (item.description() != null) draft.put("description", item.description());
        if (item.quantity() != null) draft.put("quantity", item.quantity().toPlainString());
        if (item.unit() != null) draft.put("unit", item.unit());
        if (item.unitCost() != null) draft.put("unitCost", item.unitCost().toPlainString());
        if (item.vendor() != null) draft.put("vendor", item.vendor());
        if (item.referenceNumber() != null) draft.put("reference", item.referenceNumber());
        return draft;
    }

    private void prompt(long chatId, UUID userId, State state, Map<String, String> draft, String text) {
        conversations.step(userId, state, draft);
        send(chatId, text, cancelRows());
    }

    private void promptOptional(long chatId, UUID userId, State state, Map<String, String> draft, String text) {
        conversations.step(userId, state, draft);
        send(chatId, text, List.of(List.of(button("Bỏ qua", "wf:skip"), button("Hủy", "wf:cancel"))));
    }

    private TelegramLink validLink(TelegramClient.Update update) {
        return links.findByTelegramUserId(update.telegramUserId())
            .filter(TelegramLink::isActive)
            .filter(link -> link.getTelegramChatId() == update.chatId())
            .orElse(null);
    }

    private UUID linkedUser(TelegramClient.Update update) {
        TelegramLink link = validLink(update);
        return link == null ? new UUID(0, 0) : link.getUserId();
    }

    private void send(long chatId, String text, List<List<TelegramClient.InlineButton>> rows) {
        client.sendMessage(chatId, text, rows);
    }

    private void answer(TelegramClient.Update update, String text, boolean alert) {
        if (update.callbackQueryId() != null) client.answerCallbackQuery(update.callbackQueryId(), text, alert);
    }

    private static TelegramClient.InlineButton button(String text, String data) {
        if (data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64)
            throw new IllegalArgumentException("Telegram callback data is too long");
        return TelegramClient.InlineButton.callback(text, data);
    }

    private static List<List<TelegramClient.InlineButton>> confirmRows() {
        return List.of(List.of(button("Xác nhận", "wf:yes"), button("Hủy", "wf:cancel")));
    }
    private static List<List<TelegramClient.InlineButton>> cancelRows() {
        return List.of(List.of(button("Hủy nhập", "wf:cancel")));
    }
    private static List<List<TelegramClient.InlineButton>> menuBack() {
        return List.of(List.of(button("Về phiếu", "wf:menu"), button("Danh sách", "wf:list:0")));
    }

    private static String ticketText(TicketRef t) {
        return "🔧 " + t.code() + " · " + status(t.status()) + "\n" + t.title() +
            "\nThiết bị: " + value(t.equipmentCode()) + " · " + value(t.equipmentName()) +
            "\nĐịa điểm: " + value(t.siteName()) + "\nƯu tiên: " + value(t.priority()) +
            "\n\n" + value(t.description());
    }

    private static String costText(CostView c) {
        return "💰 Chi phí · " + costStatus(c.status()) +
            "\nLinh kiện: " + money(c.partsTotal()) +
            "\nPhát sinh: " + money(c.otherTotal()) +
            "\nTổng cộng: " + money(c.grandTotal());
    }

    private static String status(String value) {
        return switch (value == null ? "" : value) {
            case "ASSIGNED" -> "Đã phân công"; case "IN_PROGRESS" -> "Đang xử lý";
            case "WAITING_PARTS" -> "Chờ linh kiện"; case "RESOLVED" -> "Đã xử lý";
            default -> value;
        };
    }
    private static String costStatus(String value) {
        return switch (value == null ? "DRAFT" : value) {
            case "QUOTED" -> "Chờ khách hàng xác nhận"; case "ACCEPTED" -> "Khách hàng đã chấp nhận"; default -> "Bản nháp";
        };
    }
    private static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
        return format.format(value == null ? BigDecimal.ZERO : value) + " ₫";
    }
    private static String normalized(String value) {
        if (value == null) return "";
        return value.trim().replaceFirst("@[A-Za-z0-9_]+$", "").toLowerCase(Locale.ROOT);
    }
    private static void required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Nội dung không được để trống");
    }
    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
    }
    private static int integerStrict(String value, int min, int max) {
        int parsed;
        try { parsed = Integer.parseInt(value.trim()); }
        catch (Exception invalid) { throw new IllegalArgumentException("Số phút không hợp lệ"); }
        if (parsed < min || parsed > max) throw new IllegalArgumentException("Số phút ngoài phạm vi");
        return parsed;
    }
    private static BigDecimal decimal(String value, boolean positive) {
        try {
            BigDecimal parsed = new BigDecimal(value.trim().replace(" ", "").replace(',', '.'));
            if ((positive && parsed.signum() <= 0) || (!positive && parsed.signum() < 0))
                throw new IllegalArgumentException("Giá trị không hợp lệ");
            return parsed;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Giá trị không hợp lệ"); }
    }
    private static String key(TelegramClient.Update update) { return "telegram:" + update.updateId(); }
    private static void ensureCommandKey(Map<String, String> draft) {
        draft.computeIfAbsent("commandKey", ignored -> "telegram-command:" + UUID.randomUUID());
    }
    private static String commandKey(Map<String, String> draft, TelegramClient.Update update) {
        return draft.getOrDefault("commandKey", key(update));
    }
    private static String value(String value) { return value == null || value.isBlank() ? "—" : value; }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static String shorten(String value, int length) {
        if (value == null) return "Chi phí";
        return value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }
}
