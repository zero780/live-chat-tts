package com.comext.livechattts.application.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

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

    /** Splits long speech into small natural fragments to reduce time to first audio. */
    public static List<String> speechFragments(String input) {
        String text = sanitize(input);
        if (text.length() <= 120) return List.of(text);
        List<String> fragments = new ArrayList<>();
        for (String sentence : text.split("(?<=[.!?;:])\\s+")) {
            String remaining = sentence.trim();
            while (remaining.length() > 120) {
                int split = remaining.lastIndexOf(' ', 120);
                if (split < 40) split = 120;
                fragments.add(remaining.substring(0, split).trim());
                remaining = remaining.substring(split).trim();
            }
            if (!remaining.isBlank()) fragments.add(remaining);
        }
        return fragments.isEmpty() ? List.of(text) : fragments;
    }
}
