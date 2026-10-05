package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.telegram.domain.TelegramDelivery;
import com.example.quanlybaotri.telegram.domain.TelegramLink;
import com.example.quanlybaotri.telegram.persistence.TelegramDeliveryRepository;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkRepository;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TelegramUpdateProcessor {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateProcessor.class);
    private static final Pattern START = Pattern.compile(
        "^/start(?:@[A-Za-z0-9_]+)?\\s+([A-Za-z0-9_-]{1,64})\\s*$"
    );
    private static final Pattern START_TICKET = Pattern.compile(
        "^start:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$"
    );

    private final TelegramLinkService links;
    private final TelegramLinkRepository linkRepository;
    private final TelegramDeliveryRepository deliveries;
    private final MaintenanceTicketClient maintenance;
    private final TelegramKeyboardFactory keyboards;
    private final TelegramClient client;

    public TelegramUpdateProcessor(
        TelegramLinkService links,
        TelegramLinkRepository linkRepository,
        TelegramDeliveryRepository deliveries,
        MaintenanceTicketClient maintenance,
        TelegramKeyboardFactory keyboards,
        TelegramClient client
    ) {
        this.links = links;
        this.linkRepository = linkRepository;
        this.deliveries = deliveries;
        this.maintenance = maintenance;
        this.keyboards = keyboards;
        this.client = client;
    }

    public void process(TelegramClient.Update update) {
        if (update.isCallbackQuery()) {
            processCallback(update);
            return;
        }
        processMessage(update);
    }

    private void processMessage(TelegramClient.Update update) {
        if (!"private".equals(update.chatType())) return;
        Matcher matcher = START.matcher(update.text());
        if (!matcher.matches()) {
            reply(
                update.chatId(),
                "Hãy mở liên kết được tạo tại trang Hồ sơ của hệ thống bảo trì để kết nối tài khoản."
            );
            return;
        }

        TelegramLinkService.ConsumeResult result = links.consume(
            matcher.group(1),
            update.telegramUserId(),
            update.chatId(),
            update.telegramUsername()
        );
        String message = switch (result) {
            case LINKED -> "Liên kết thành công. Bạn sẽ nhận thông báo về các phiếu bảo trì được giao.";
            case EXPIRED -> "Liên kết đã hết hạn. Vui lòng tạo liên kết mới tại trang Hồ sơ.";
            case CONFLICT -> "Tài khoản Telegram này đã được liên kết với một tài khoản khác.";
            case INVALID -> "Liên kết không hợp lệ hoặc đã được sử dụng.";
        };
        reply(update.chatId(), message);
    }

    private void processCallback(TelegramClient.Update update) {
        Matcher matcher = START_TICKET.matcher(
            update.callbackData() == null ? "" : update.callbackData()
        );
        if (!"private".equals(update.chatType()) || !matcher.matches()) {
            answer(update.callbackQueryId(), "Thao tác không hợp lệ.", true);
            return;
        }

        TelegramDelivery delivery;
        try {
            delivery = deliveries.findById(UUID.fromString(matcher.group(1))).orElse(null);
        } catch (IllegalArgumentException invalidId) {
            delivery = null;
        }
        TelegramLink link = linkRepository.findByTelegramUserId(update.telegramUserId()).orElse(null);
        if (!validCallback(update, delivery, link)) {
            answer(update.callbackQueryId(), "Bạn không có quyền thực hiện thao tác này.", true);
            return;
        }

        try {
            MaintenanceTicketClient.StartResult result = maintenance.start(
                delivery.getReferenceId(),
                delivery.getUserId()
            );
            CallbackResult response = callbackResult(result.outcome());
            answer(update.callbackQueryId(), response.message(), response.alert());
            editCompletedMessage(update, delivery, response);
            log.info(
                "Telegram ticket action completed deliveryId={} outcome={}",
                delivery.getId(),
                result.outcome()
            );
        } catch (RuntimeException failure) {
            log.warn(
                "Telegram ticket action failed deliveryId={} error={}",
                delivery.getId(),
                safeMessage(failure)
            );
            answer(
                update.callbackQueryId(),
                "Hệ thống đang tạm thời không xử lý được. Vui lòng thử lại.",
                true
            );
        }
    }

    private boolean validCallback(
        TelegramClient.Update update,
        TelegramDelivery delivery,
        TelegramLink link
    ) {
        return delivery != null &&
            delivery.getStatus() == TelegramDelivery.Status.SENT &&
            delivery.getAction() == TelegramDelivery.Action.START_ASSIGNED_TICKET &&
            delivery.getReferenceId() != null &&
            delivery.getTelegramMessageId() != null &&
            link != null &&
            link.isActive() &&
            Objects.equals(link.getUserId(), delivery.getUserId()) &&
            link.getTelegramChatId() == update.chatId() &&
            delivery.getTelegramMessageId() == update.callbackMessageId();
    }

    private CallbackResult callbackResult(MaintenanceTicketClient.Outcome outcome) {
        return switch (outcome) {
            case STARTED -> new CallbackResult("Đã tiếp nhận và bắt đầu xử lý phiếu.", false);
            case ALREADY_HANDLED -> new CallbackResult("Phiếu đã được bắt đầu xử lý trước đó.", false);
            case NO_LONGER_ASSIGNED -> new CallbackResult("Phiếu không còn được phân công cho bạn.", true);
            case NOT_STARTABLE -> new CallbackResult("Phiếu không thể tiếp nhận ở trạng thái hiện tại.", true);
            case TECHNICIAN_INELIGIBLE -> new CallbackResult("Tài khoản không còn đủ quyền kỹ thuật viên.", true);
        };
    }

    private void editCompletedMessage(
        TelegramClient.Update update,
        TelegramDelivery delivery,
        CallbackResult result
    ) {
        try {
            client.editMessage(
                update.chatId(),
                update.callbackMessageId(),
                delivery.getMessageText() + "\n\n" + (result.alert() ? "⚠️ " : "✅ ") + result.message(),
                keyboards.rows(delivery, false)
            );
        } catch (RuntimeException failure) {
            log.warn(
                "Could not update completed Telegram message deliveryId={} error={}",
                delivery.getId(),
                safeMessage(failure)
            );
        }
    }

    private void answer(String callbackQueryId, String message, boolean alert) {
        if (callbackQueryId == null) return;
        try {
            client.answerCallbackQuery(callbackQueryId, message, alert);
        } catch (RuntimeException failure) {
            log.warn("Could not answer Telegram callback query: {}", safeMessage(failure));
        }
    }

    private void reply(long chatId, String message) {
        try {
            client.sendMessage(chatId, message, List.of());
        } catch (RuntimeException failure) {
            log.warn("Could not send Telegram link response to chat {}", chatId);
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
            ? failure.getClass().getSimpleName()
            : message.replaceAll("bot[0-9]+:[A-Za-z0-9_-]+", "bot[REDACTED]");
    }

    private record CallbackResult(String message, boolean alert) {}
}
