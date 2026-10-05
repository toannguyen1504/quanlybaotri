package com.example.quanlybaotri.telegram.application;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class TelegramClient {

    private final TelegramProperties properties;
    private final RestClient client;

    public TelegramClient(TelegramProperties properties) {
        this.properties = properties;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(
            Duration.ofSeconds(Math.max(35, properties.getPollTimeoutSeconds() + 10L))
        );
        String token = properties.getBotToken().isBlank() ? "disabled" : properties.getBotToken();
        client = RestClient.builder()
            .baseUrl("https://api.telegram.org/bot" + token)
            .requestFactory(factory)
            .build();
    }

    public List<Update> getUpdates(long offset) {
        ensureConfigured();
        Map<String, Object> request = Map.of(
            "offset",
            offset,
            "timeout",
            properties.getPollTimeoutSeconds(),
            "allowed_updates",
            List.of("message", "callback_query")
        );
        JsonNode result = call("getUpdates", request).path("result");
        List<Update> updates = new ArrayList<>();
        if (!result.isArray()) return updates;
        for (JsonNode item : result) {
            JsonNode callback = item.path("callback_query");
            boolean isCallback = !callback.isMissingNode();
            JsonNode message = isCallback ? callback.path("message") : item.path("message");
            JsonNode chat = message.path("chat");
            JsonNode from = isCallback ? callback.path("from") : message.path("from");
            updates.add(
                new Update(
                    item.path("update_id").asLong(),
                    message.path("text").asText(""),
                    chat.path("id").asLong(),
                    chat.path("type").asText(""),
                    from.path("id").asLong(),
                    nullableText(from, "username"),
                    nullableText(callback, "id"),
                    nullableText(callback, "data"),
                    message.path("message_id").asLong()
                )
            );
        }
        return updates;
    }

    public void deleteWebhook() {
        ensureConfigured();
        call("deleteWebhook", Map.of("drop_pending_updates", false));
    }

    public long sendMessage(long chatId, String text, List<List<InlineButton>> buttonRows) {
        ensureConfigured();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chat_id", chatId);
        request.put("text", text);
        if (buttonRows != null && !buttonRows.isEmpty()) request.put(
            "reply_markup",
            keyboard(buttonRows)
        );
        return call("sendMessage", request).path("result").path("message_id").asLong();
    }

    public void answerCallbackQuery(String callbackQueryId, String text, boolean showAlert) {
        ensureConfigured();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("callback_query_id", callbackQueryId);
        if (text != null && !text.isBlank()) request.put("text", text);
        request.put("show_alert", showAlert);
        call("answerCallbackQuery", request);
    }

    public void editMessage(
        long chatId,
        long messageId,
        String text,
        List<List<InlineButton>> buttonRows
    ) {
        ensureConfigured();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chat_id", chatId);
        request.put("message_id", messageId);
        request.put("text", text);
        request.put("reply_markup", keyboard(buttonRows == null ? List.of() : buttonRows));
        call("editMessageText", request);
    }

    private static Map<String, Object> keyboard(List<List<InlineButton>> rows) {
        return Map.of(
            "inline_keyboard",
            rows.stream().map(row -> row.stream().map(InlineButton::request).toList()).toList()
        );
    }

    private JsonNode call(String method, Object request) {
        try {
            JsonNode response = client
                .post()
                .uri("/" + method)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
            if (response == null || !response.path("ok").asBoolean(false)) {
                throw apiError(response, 0, "Telegram returned an invalid response");
            }
            return response;
        } catch (RestClientResponseException ex) {
            JsonNode response = null;
            try {
                response = new tools.jackson.databind.ObjectMapper().readTree(
                    ex.getResponseBodyAsString()
                );
            } catch (Exception ignored) {}
            throw apiError(response, ex.getStatusCode().value(), "Telegram request failed");
        }
    }

    private TelegramApiException apiError(JsonNode response, int fallbackCode, String fallback) {
        if (response == null) return new TelegramApiException(fallbackCode, fallback, null);
        int code = response.path("error_code").asInt(fallbackCode);
        String description = response.path("description").asText(fallback);
        JsonNode retry = response.path("parameters").path("retry_after");
        return new TelegramApiException(
            code,
            description,
            retry.isNumber() ? retry.asInt() : null
        );
    }

    private void ensureConfigured() {
        if (!properties.isConfigured()) throw new IllegalStateException(
            "Telegram integration is disabled or incomplete"
        );
    }

    private static String nullableText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    public record Update(
        long updateId,
        String text,
        long chatId,
        String chatType,
        long telegramUserId,
        String telegramUsername,
        String callbackQueryId,
        String callbackData,
        long callbackMessageId
    ) {
        public Update(
            long updateId,
            String text,
            long chatId,
            String chatType,
            long telegramUserId,
            String telegramUsername
        ) {
            this(updateId, text, chatId, chatType, telegramUserId, telegramUsername, null, null, 0);
        }

        public boolean isCallbackQuery() {
            return callbackQueryId != null;
        }
    }

    public record InlineButton(String text, String url, String callbackData) {
        public static InlineButton url(String text, String url) {
            return new InlineButton(text, url, null);
        }

        public static InlineButton callback(String text, String callbackData) {
            return new InlineButton(text, null, callbackData);
        }

        private Map<String, String> request() {
            if (url != null) return Map.of("text", text, "url", url);
            return Map.of("text", text, "callback_data", callbackData);
        }
    }
}
