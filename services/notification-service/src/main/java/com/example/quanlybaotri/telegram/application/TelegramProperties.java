package com.example.quanlybaotri.telegram.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.telegram")
public class TelegramProperties {

    private boolean enabled;
    private String botToken = "";
    private String botUsername = "technician001_bot";
    private String appPublicUrl = "http://localhost:4200";
    private Duration linkTtl = Duration.ofMinutes(10);
    private int pollTimeoutSeconds = 25;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken == null ? "" : botToken.trim();
    }

    public String getBotUsername() {
        return botUsername.startsWith("@") ? botUsername.substring(1) : botUsername;
    }

    public void setBotUsername(String botUsername) {
        this.botUsername = botUsername == null ? "" : botUsername.trim();
    }

    public String getAppPublicUrl() {
        return appPublicUrl.endsWith("/")
            ? appPublicUrl.substring(0, appPublicUrl.length() - 1)
            : appPublicUrl;
    }

    public void setAppPublicUrl(String appPublicUrl) {
        this.appPublicUrl = appPublicUrl == null ? "" : appPublicUrl.trim();
    }

    public Duration getLinkTtl() {
        return linkTtl;
    }

    public void setLinkTtl(Duration linkTtl) {
        this.linkTtl = linkTtl;
    }

    public int getPollTimeoutSeconds() {
        return pollTimeoutSeconds;
    }

    public void setPollTimeoutSeconds(int pollTimeoutSeconds) {
        this.pollTimeoutSeconds = pollTimeoutSeconds;
    }

    public boolean isConfigured() {
        return enabled && !botToken.isBlank() && !botUsername.isBlank();
    }
}
