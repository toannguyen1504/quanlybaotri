package com.example.quanlybaotri.telegram.application;

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

    private final TelegramLinkService links;
    private final TelegramClient client;

    public TelegramUpdateProcessor(TelegramLinkService links, TelegramClient client) {
        this.links = links;
        this.client = client;
    }

    public void process(TelegramClient.Update update) {
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

    private void reply(long chatId, String message) {
        try {
            client.sendMessage(chatId, message, null);
        } catch (RuntimeException failure) {
            log.warn("Could not send Telegram link response to chat {}", chatId);
        }
    }
}
