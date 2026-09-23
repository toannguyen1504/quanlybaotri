package com.example.quanlybaotri.telegram.application;

public class TelegramApiException extends RuntimeException {

    private final int errorCode;
    private final Integer retryAfterSeconds;

    public TelegramApiException(int errorCode, String message, Integer retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
