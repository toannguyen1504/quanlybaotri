package com.example.quanlybaotri.telegram.application;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
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

    private static final long MAX_DOWNLOAD = 20L * 1024L * 1024L;

    private final TelegramProperties properties;
    private final RestClient client;
    private final HttpClient fileClient;
    private final String fileBaseUrl;

    public TelegramClient(TelegramProperties properties) {
        this.properties = properties;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        fileClient = http;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(
            Duration.ofSeconds(Math.max(35, properties.getPollTimeoutSeconds() + 10L))
        );
        String token = properties.getBotToken().isBlank() ? "disabled" : properties.getBotToken();
        fileBaseUrl = "https://api.telegram.org/file/bot" + token + "/";
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
            Media media = isCallback ? null : media(message);
            updates.add(
                new Update(
                    item.path("update_id").asLong(),
                    message.path("text").asText(message.path("caption").asText("")),
                    chat.path("id").asLong(),
                    chat.path("type").asText(""),
                    from.path("id").asLong(),
                    nullableText(from, "username"),
                    nullableText(callback, "id"),
                    nullableText(callback, "data"),
                    message.path("message_id").asLong(),
                    media
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

    public DownloadedFile download(Media media) {
        ensureConfigured();
        if (media == null || media.fileId() == null) throw new IllegalArgumentException(
            "Telegram media is missing"
        );
        if (media.fileSize() > MAX_DOWNLOAD) throw new IllegalArgumentException(
            "Tệp Telegram vượt quá giới hạn 20 MB"
        );
        String filePath = call("getFile", Map.of("file_id", media.fileId()))
            .path("result").path("file_path").asText("");
        if (filePath.isBlank() || filePath.contains("..")) throw new IllegalStateException(
            "Telegram returned an invalid file path"
        );
        String suffix = suffix(media.fileName(), filePath);
        Path target = null;
        try {
            target = Files.createTempFile("telegram-upload-", suffix);
            HttpRequest request = HttpRequest.newBuilder(URI.create(fileBaseUrl + filePath))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
            HttpResponse<Path> response = fileClient.send(
                request,
                HttpResponse.BodyHandlers.ofFile(target)
            );
            long size = Files.size(target);
            if (response.statusCode() < 200 || response.statusCode() >= 300 || size <= 0 || size > MAX_DOWNLOAD)
                throw new IllegalStateException("Không thể tải tệp từ Telegram");
            return new DownloadedFile(
                target,
                media.fileName() == null || media.fileName().isBlank()
                    ? "telegram-" + media.fileUniqueId() + suffix
                    : media.fileName(),
                media.mimeType(),
                size
            );
        } catch (Exception failure) {
            if (target != null) try { Files.deleteIfExists(target); } catch (Exception ignored) {}
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Không thể tải tệp từ Telegram", failure);
        }
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

    private static Media media(JsonNode message) {
        JsonNode photos = message.path("photo");
        if (photos.isArray() && photos.size() > 0) {
            JsonNode photo = photos.get(photos.size() - 1);
            String unique = photo.path("file_unique_id").asText("photo");
            return new Media(
                photo.path("file_id").asText(),
                unique,
                "photo-" + unique + ".jpg",
                "image/jpeg",
                photo.path("file_size").asLong(0),
                "PHOTO"
            );
        }
        JsonNode video = message.path("video");
        if (!video.isMissingNode() && video.hasNonNull("file_id")) return new Media(
            video.path("file_id").asText(),
            video.path("file_unique_id").asText("video"),
            nullableText(video, "file_name"),
            nullableText(video, "mime_type") == null ? "video/mp4" : nullableText(video, "mime_type"),
            video.path("file_size").asLong(0),
            "VIDEO"
        );
        JsonNode document = message.path("document");
        if (!document.isMissingNode() && document.hasNonNull("file_id")) return new Media(
            document.path("file_id").asText(),
            document.path("file_unique_id").asText("document"),
            nullableText(document, "file_name"),
            nullableText(document, "mime_type"),
            document.path("file_size").asLong(0),
            "DOCUMENT"
        );
        return null;
    }

    private static String suffix(String name, String filePath) {
        String candidate = name == null || name.isBlank() ? filePath : name;
        int dot = candidate.lastIndexOf('.');
        String value = dot < 0 ? ".bin" : candidate.substring(dot);
        return value.matches("\\.[A-Za-z0-9]{1,10}") ? value.toLowerCase() : ".bin";
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
        long callbackMessageId,
        Media media
    ) {
        public Update(
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
            this(updateId, text, chatId, chatType, telegramUserId, telegramUsername,
                callbackQueryId, callbackData, callbackMessageId, null);
        }

        public Update(
            long updateId,
            String text,
            long chatId,
            String chatType,
            long telegramUserId,
            String telegramUsername
        ) {
            this(updateId, text, chatId, chatType, telegramUserId, telegramUsername, null, null, 0, null);
        }

        public boolean isCallbackQuery() {
            return callbackQueryId != null;
        }
    }

    public record Media(
        String fileId,
        String fileUniqueId,
        String fileName,
        String mimeType,
        long fileSize,
        String kind
    ) {}

    public record DownloadedFile(Path path, String filename, String contentType, long size)
        implements AutoCloseable {
        @Override public void close() {
            try { Files.deleteIfExists(path); } catch (Exception ignored) {}
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
