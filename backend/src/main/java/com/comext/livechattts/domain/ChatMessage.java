package com.comext.livechattts.domain;

import java.time.Instant;
import java.util.Objects;

public record ChatMessage(String author, String text, Instant receivedAt, Type type, int giftQuantity) {
    public enum Type {
        CHAT,
        GIFT,
        FOLLOW,
        SUBSCRIBE,
        LIVE_STARTED,
        LIVE_RESUMED,
        LIVE_PAUSED,
        LIVE_ENDED
    }

    public ChatMessage(String author, String text, Instant receivedAt) {
        this(author, text, receivedAt, Type.CHAT, 0);
    }

    public ChatMessage(String author, String text, Instant receivedAt, Type type) {
        this(author, text, receivedAt, type, 0);
    }

    public ChatMessage {
        author = Objects.requireNonNull(author, "author").trim();
        text = Objects.requireNonNull(text, "text").trim();
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        type = Objects.requireNonNull(type, "type");
        if (giftQuantity < 0) throw new IllegalArgumentException("Cantidad de regalo invÃ¡lida");
        if (author.isEmpty() || author.length() > 80) throw new IllegalArgumentException("Autor inválido");
        if (text.isEmpty() || text.length() > 300) throw new IllegalArgumentException("Texto inválido");
    }
}
