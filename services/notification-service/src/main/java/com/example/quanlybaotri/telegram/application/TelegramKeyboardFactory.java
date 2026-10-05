package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.telegram.domain.TelegramDelivery;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TelegramKeyboardFactory {

    private final TelegramProperties properties;

    public TelegramKeyboardFactory(TelegramProperties properties) {
        this.properties = properties;
    }

    public List<List<TelegramClient.InlineButton>> rows(
        TelegramDelivery delivery,
        boolean includeAction
    ) {
        List<List<TelegramClient.InlineButton>> rows = new ArrayList<>();
        if (
            includeAction &&
            delivery.getAction() == TelegramDelivery.Action.START_ASSIGNED_TICKET
        ) rows.add(
            List.of(
                TelegramClient.InlineButton.callback(
                    "Tiếp nhận phiếu",
                    "start:" + delivery.getId()
                )
            )
        );
        String url = ticketUrl(delivery);
        if (url != null) rows.add(List.of(TelegramClient.InlineButton.url("Mở phiếu", url)));
        return List.copyOf(rows);
    }

    private String ticketUrl(TelegramDelivery delivery) {
        if (delivery.getReferenceId() == null || properties.getAppPublicUrl().isBlank()) return null;
        try {
            URI base = URI.create(properties.getAppPublicUrl());
            String host = base.getHost();
            if (
                host == null ||
                "localhost".equalsIgnoreCase(host) ||
                "127.0.0.1".equals(host) ||
                "::1".equals(host)
            ) return null;
            String scheme = base.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
            return properties.getAppPublicUrl() + "/tickets/" + delivery.getReferenceId();
        } catch (IllegalArgumentException invalidUrl) {
            return null;
        }
    }
}
