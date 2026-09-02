package com.comext.livechattts.adapter.out.live;

import com.comext.livechattts.application.port.out.LiveChatClient;
import com.comext.livechattts.domain.ChatMessage;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/** Test adapter only. It has no network access and accepts messages injected by the localhost API. */
public final class LocalTestLiveChatClient implements LiveChatClient {
    private volatile Consumer<ChatMessage> consumer;
    @Override public void connect(String username, Consumer<ChatMessage> onMessage, Consumer<Throwable> onFailure) { consumer = Objects.requireNonNull(onMessage); }
    @Override public void disconnect() { consumer = null; }
    public boolean publish(String author, String text) {
        Consumer<ChatMessage> current = consumer;
        if (current == null) return false;
        current.accept(new ChatMessage(author, text, Instant.now()));
        return true;
    }
}
