package com.comext.livechattts.domain;

public enum Platform {
    WINDOWS,
    MACOS,
    LINUX;

    public static Platform fromEnvironment(String value) {
        try {
            return Platform.valueOf(value == null ? "WINDOWS" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("APP_PLATFORM debe ser WINDOWS, MACOS o LINUX");
        }
    }
}
