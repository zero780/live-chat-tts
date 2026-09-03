package com.comext.livechattts.domain;

import java.util.Locale;

/** Available local speech engines. */
public enum TtsEngine {
    SAPI,
    PIPER;

    public static TtsEngine fromEnvironment(String raw) {
        if (raw == null || raw.isBlank()) return SAPI;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("TTS_ENGINE must be SAPI or PIPER");
        }
    }
}
