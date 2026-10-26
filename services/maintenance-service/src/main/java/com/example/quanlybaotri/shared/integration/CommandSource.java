package com.example.quanlybaotri.shared.integration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class CommandSource {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CommandSource() {}

    public static <T> T telegram(Supplier<T> action) {
        String previous = CURRENT.get();
        CURRENT.set("TELEGRAM");
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static void telegram(Runnable action) {
        telegram(() -> {
            action.run();
            return null;
        });
    }

    public static Map<String, Object> metadata(Map<String, Object> original) {
        String source = CURRENT.get();
        if (source == null) return original;
        Map<String, Object> result = new LinkedHashMap<>();
        if (original != null) result.putAll(original);
        result.put("source", source);
        return result;
    }
}
