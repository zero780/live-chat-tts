package com.comext.livechattts.application.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TextSanitizer {
    private static final Map<Integer, EmojiName> EMOJI_NAMES = Map.ofEntries(
            Map.entry(0x1F600, new EmojiName("cara sonriente", "grinning face")), Map.entry(0x1F601, new EmojiName("cara radiante", "beaming face")),
            Map.entry(0x1F602, new EmojiName("cara llorando de risa", "face with tears of joy")), Map.entry(0x1F603, new EmojiName("cara sonriente", "smiling face")),
            Map.entry(0x1F604, new EmojiName("cara sonriente con ojos sonrientes", "smiling face with smiling eyes")), Map.entry(0x1F605, new EmojiName("cara sonriente con sudor", "grinning face with sweat")),
            Map.entry(0x1F606, new EmojiName("cara riendo", "grinning squinting face")), Map.entry(0x1F609, new EmojiName("cara guiñando", "winking face")),
            Map.entry(0x1F60D, new EmojiName("cara con ojos de corazón", "smiling face with heart eyes")), Map.entry(0x1F60E, new EmojiName("cara con lentes de sol", "smiling face with sunglasses")),
            Map.entry(0x1F60F, new EmojiName("cara con sonrisa pícara", "smirking face")), Map.entry(0x1F614, new EmojiName("cara pensativa", "pensive face")),
            Map.entry(0x1F615, new EmojiName("cara confundida", "confused face")), Map.entry(0x1F618, new EmojiName("cara mandando un beso", "face blowing a kiss")),
            Map.entry(0x1F622, new EmojiName("cara llorando", "crying face")), Map.entry(0x1F62D, new EmojiName("cara llorando fuertemente", "loudly crying face")),
            Map.entry(0x1F631, new EmojiName("cara gritando de miedo", "face screaming in fear")), Map.entry(0x1F643, new EmojiName("cara al revés", "upside-down face")),
            Map.entry(0x1F914, new EmojiName("cara pensando", "thinking face")), Map.entry(0x1F923, new EmojiName("cara riendo en el suelo", "rolling on the floor laughing")),
            Map.entry(0x2764, new EmojiName("corazón rojo", "red heart")), Map.entry(0x1F494, new EmojiName("corazón roto", "broken heart")),
            Map.entry(0x1F495, new EmojiName("dos corazones", "two hearts")), Map.entry(0x1F496, new EmojiName("corazón brillante", "sparkling heart")),
            Map.entry(0x1F44D, new EmojiName("pulgar arriba", "thumbs up")), Map.entry(0x1F44E, new EmojiName("pulgar abajo", "thumbs down")),
            Map.entry(0x1F44F, new EmojiName("aplausos", "clapping hands")), Map.entry(0x1F64F, new EmojiName("manos juntas", "folded hands")),
            Map.entry(0x1F525, new EmojiName("fuego", "fire")), Map.entry(0x1F4AF, new EmojiName("cien puntos", "hundred points")),
            Map.entry(0x1F389, new EmojiName("confeti", "party popper")), Map.entry(0x1F31F, new EmojiName("estrella brillante", "glowing star")),
            Map.entry(0x2B50, new EmojiName("estrella", "star")), Map.entry(0x1F680, new EmojiName("cohete", "rocket")),
            Map.entry(0x1F4A5, new EmojiName("explosión", "collision")), Map.entry(0x1F4A9, new EmojiName("popó", "pile of poo")),
            Map.entry(0x1F63A, new EmojiName("gato sonriente", "grinning cat")), Map.entry(0x1F436, new EmojiName("perro", "dog")),
            Map.entry(0x1F408, new EmojiName("gato", "cat")), Map.entry(0x1F48B, new EmojiName("marca de beso", "kiss mark")));
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

    /** Replaces common emoji with deterministic localized names before TTS. */
    public static String emojisForSpeech(String input, boolean english) {
        String text = sanitize(input);
        StringBuilder output = new StringBuilder(text.length());
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == 0xFE0F || codePoint == 0xFE0E || codePoint == 0x200D || (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF)) continue;
            EmojiName known = EMOJI_NAMES.get(codePoint);
            if (known != null) output.append(' ').append(english ? known.english() : known.spanish()).append(' ');
            else if (isEmoji(codePoint)) output.append(english ? " emoji " : " emoji ");
            else output.appendCodePoint(codePoint);
        }
        return output.toString().replaceAll("\\s+", " ").trim();
    }

    private static boolean isEmoji(int codePoint) {
        return (codePoint >= 0x1F000 && codePoint <= 0x1FAFF) || (codePoint >= 0x2600 && codePoint <= 0x27BF)
                || codePoint == 0x00A9 || codePoint == 0x00AE || codePoint == 0x3030 || codePoint == 0x303D;
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

    private record EmojiName(String spanish, String english) { }
}
