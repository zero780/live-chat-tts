package com.comext.livechattts.application.service;

import com.comext.livechattts.application.port.in.ConnectionUseCase;
import com.comext.livechattts.application.port.out.LiveChatClient;
import com.comext.livechattts.domain.ChatMessage;
import com.comext.livechattts.domain.ConnectionState;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class LiveConnectionService implements ConnectionUseCase {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private final LiveChatClient client;
    private final SpeechQueueService speechQueue;
    private final RuntimeDiagnostics diagnostics;
    private final AtomicBoolean acceptingLiveMessages = new AtomicBoolean();
    private final AtomicReference<ConnectionSnapshot> snapshot = new AtomicReference<>(new ConnectionSnapshot(ConnectionState.DISCONNECTED, "", "Sin conexión"));

    public LiveConnectionService(LiveChatClient client, SpeechQueueService speechQueue, RuntimeDiagnostics diagnostics) {
        this.client = Objects.requireNonNull(client);
        this.speechQueue = Objects.requireNonNull(speechQueue);
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    @Override public synchronized void connect(String rawUsername) {
        String username = rawUsername == null ? "" : rawUsername.trim().replaceFirst("^@", "");
        if (!USERNAME.matcher(username).matches()) throw new IllegalArgumentException("Usuario de TikTok inválido");
        disconnect();
        snapshot.set(new ConnectionSnapshot(ConnectionState.CONNECTING, username, "Conectando…"));
        try {
            acceptingLiveMessages.set(true);
            client.connect(username, this::onMessage, this::onFailure);
            snapshot.set(new ConnectionSnapshot(ConnectionState.CONNECTED, username, "Conectado", client.profileImageUrl()));
            diagnostics.clear();
        } catch (RuntimeException exception) {
            acceptingLiveMessages.set(false);
            snapshot.set(new ConnectionSnapshot(ConnectionState.ERROR, username, safeMessage(exception)));
            throw exception;
        }
    }

    private void onMessage(ChatMessage raw) {
        if (!acceptingLiveMessages.get()) return;
        String author = TextSanitizer.sanitize(raw.author());
        String text = TextSanitizer.sanitize(raw.text());
        if (!author.isBlank() && !text.isBlank()) speechQueue.submit(new ChatMessage(author, text, Instant.now(), raw.type()));
    }

    private void onFailure(Throwable error) {
        acceptingLiveMessages.set(false);
        speechQueue.discardPending();
        diagnostics.record("TikTok", error);
        ConnectionSnapshot current = snapshot.get();
        snapshot.set(new ConnectionSnapshot(ConnectionState.ERROR, current.username(), safeMessage(error), current.avatarUrl()));
    }

    @Override public synchronized void disconnect() {
        acceptingLiveMessages.set(false);
        client.disconnect();
        speechQueue.discardPending();
        snapshot.set(new ConnectionSnapshot(ConnectionState.DISCONNECTED, "", "Sin conexión"));
    }
    @Override public ConnectionSnapshot snapshot() { return snapshot.get(); }
    private String safeMessage(Throwable error) { return error.getMessage() == null ? "Error de conexión" : TextSanitizer.sanitize(error.getMessage()); }
}
