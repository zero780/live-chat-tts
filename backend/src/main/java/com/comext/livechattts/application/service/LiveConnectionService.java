package com.comext.livechattts.application.service;

import com.comext.livechattts.application.port.in.ConnectionUseCase;
import com.comext.livechattts.application.port.out.LiveChatClient;
import com.comext.livechattts.domain.ChatMessage;
import com.comext.livechattts.domain.ConnectionState;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class LiveConnectionService implements ConnectionUseCase, AutoCloseable {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private final LiveChatClient client;
    private final SpeechQueueService speechQueue;
    private final RuntimeDiagnostics diagnostics;
    private final Duration reconnectDelay;
    private final int reconnectMaxAttempts;
    private final AtomicBoolean acceptingLiveMessages = new AtomicBoolean();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicLong connectionGeneration = new AtomicLong();
    private final AtomicReference<ConnectionSnapshot> snapshot = new AtomicReference<>(new ConnectionSnapshot(ConnectionState.DISCONNECTED, "", "Disconnected"));
    private final ScheduledExecutorService reconnectWorker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "live-reconnect");
        thread.setDaemon(true);
        return thread;
    });

    public LiveConnectionService(LiveChatClient client, SpeechQueueService speechQueue, RuntimeDiagnostics diagnostics, int reconnectDelaySeconds, int reconnectMaxAttempts) {
        this.client = Objects.requireNonNull(client);
        this.speechQueue = Objects.requireNonNull(speechQueue);
        this.diagnostics = Objects.requireNonNull(diagnostics);
        if (reconnectDelaySeconds < 1 || reconnectMaxAttempts < 0) throw new IllegalArgumentException("Parametros de reconexion invalidos");
        this.reconnectDelay = Duration.ofSeconds(reconnectDelaySeconds);
        this.reconnectMaxAttempts = reconnectMaxAttempts;
    }

    @Override public synchronized void connect(String rawUsername) {
        String username = rawUsername == null ? "" : rawUsername.trim().replaceFirst("^@", "");
        if (!USERNAME.matcher(username).matches()) throw new IllegalArgumentException("Usuario de TikTok invalido");
        long generation = connectionGeneration.incrementAndGet();
        reconnectScheduled.set(false);
        acceptingLiveMessages.set(false);
        client.disconnect();
        connectNow(username, generation, 0);
    }

    private synchronized void connectNow(String username, long generation, int attempt) {
        if (generation != connectionGeneration.get()) return;
        snapshot.set(new ConnectionSnapshot(ConnectionState.CONNECTING, username, attempt == 0 ? "Conectando..." : "Reconectando (intento " + attempt + "/" + reconnectMaxAttempts + ")..."));
        try {
            acceptingLiveMessages.set(true);
            client.connect(username, this::onMessage, error -> onFailure(error, username, generation, attempt));
            if (generation != connectionGeneration.get()) return;
            snapshot.set(new ConnectionSnapshot(ConnectionState.CONNECTED, username, "Conectado", client.profileImageUrl()));
            diagnostics.clear();
        } catch (RuntimeException exception) {
            acceptingLiveMessages.set(false);
            snapshot.set(new ConnectionSnapshot(ConnectionState.ERROR, username, safeMessage(exception)));
            onFailure(exception, username, generation, attempt);
            if (attempt == 0) throw exception;
        }
    }

    private void onMessage(ChatMessage raw) {
        if (!acceptingLiveMessages.get()) return;
        String author = TextSanitizer.sanitize(raw.author());
        String text = TextSanitizer.sanitize(raw.text());
        if (!author.isBlank() && !text.isBlank()) speechQueue.submit(new ChatMessage(author, text, Instant.now(), raw.type(), raw.giftQuantity(), raw.giftCoinValue()));
    }

    private void onFailure(Throwable error, String username, long generation, int failedAttempt) {
        if (generation != connectionGeneration.get()) return;
        acceptingLiveMessages.set(false);
        diagnostics.record("TikTok", error);
        ConnectionSnapshot current = snapshot.get();
        snapshot.set(new ConnectionSnapshot(ConnectionState.ERROR, current.username(), safeMessage(error), current.avatarUrl()));
        scheduleReconnect(username, generation, failedAttempt + 1);
    }

    private void scheduleReconnect(String username, long generation, int nextAttempt) {
        if (nextAttempt > reconnectMaxAttempts || !reconnectScheduled.compareAndSet(false, true)) return;
        reconnectWorker.schedule(() -> {
            reconnectScheduled.set(false);
            if (generation != connectionGeneration.get()) return;
            connectNow(username, generation, nextAttempt);
        }, reconnectDelay.toSeconds(), TimeUnit.SECONDS);
    }

    @Override public synchronized void disconnect() {
        connectionGeneration.incrementAndGet();
        reconnectScheduled.set(false);
        acceptingLiveMessages.set(false);
        client.disconnect();
        speechQueue.discardPending();
        snapshot.set(new ConnectionSnapshot(ConnectionState.DISCONNECTED, "", "Disconnected"));
    }

    @Override public ConnectionSnapshot snapshot() { return snapshot.get(); }

    @Override public void close() {
        disconnect();
        reconnectWorker.shutdownNow();
    }

    private String safeMessage(Throwable error) { return error.getMessage() == null ? "Error de conexion" : TextSanitizer.sanitize(error.getMessage()); }
}
