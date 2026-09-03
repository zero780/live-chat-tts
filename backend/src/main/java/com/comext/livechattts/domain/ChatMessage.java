package com.comext.livechattts.domain;

import java.time.Instant;
import java.util.Objects;

public record ChatMessage(String author, String text, Instant receivedAt, Type type) {
    public enum Type { CHAT, EVENT }

    public ChatMessage(String author, String text, Instant receivedAt) {
        this(author, text, receivedAt, Type.CHAT);
    }

    public ChatMessage {
        author = Objects.requireNonNull(author, "author").trim();
        text = Objects.requireNonNull(text, "text").trim();
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        type = Objects.requireNonNull(type, "type");
        if (author.isEmpty() || author.length() > 80) throw new IllegalArgumentException("Autor inválido");
        if (text.isEmpty() || text.length() > 300) throw new IllegalArgumentException("Texto inválido");
    }
}
