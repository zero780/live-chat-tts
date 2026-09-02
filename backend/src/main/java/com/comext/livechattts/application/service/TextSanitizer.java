package com.comext.livechattts.application.service;

public final class TextSanitizer {
    private TextSanitizer() { }

    public static String sanitize(String input) {
        if (input == null) return "";
        String clean = input.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ").trim();
        if (clean.length() > 280) clean = clean.substring(0, 280).trim();
        return clean;
    }
}
