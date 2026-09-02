package com.comext.livechattts.domain;

public enum LiveSource {
    LOCAL_TEST,
    TIKTOK_LIVE_JAVA;

    public static LiveSource fromEnvironment(String value) {
        try {
            return LiveSource.valueOf(value == null || value.isBlank() ? "LOCAL_TEST" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("LIVE_SOURCE debe ser LOCAL_TEST o TIKTOK_LIVE_JAVA");
        }
    }
}
