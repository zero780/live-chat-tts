package com.comext.livechattts.application.service;

import java.text.Normalizer;

public final class TextSanitizer {
    private TextSanitizer() { }

    public static String sanitize(String input) {
        if (input == null) return "";
        String clean = input.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ").trim();
        if (clean.length() > 280) clean = clean.substring(0, 280).trim();
        return clean;
    }

    /** Creates a conservative pronunciation form without changing the displayed author name. */
    public static String sanitizeAuthorForSpeech(String input) {
        String clean = Normalizer.normalize(sanitize(input), Normalizer.Form.NFKC)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (clean.length() > 80) clean = clean.substring(0, 80).trim();
        return clean.isBlank() ? "Usuario" : clean;
    }
}
