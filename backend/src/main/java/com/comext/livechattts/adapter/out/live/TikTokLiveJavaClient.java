package com.comext.livechattts.adapter.out.live;

import com.comext.livechattts.application.port.out.LiveChatClient;
import com.comext.livechattts.domain.ChatMessage;
import io.github.jwdeveloper.tiktok.TikTokLive;
import io.github.jwdeveloper.tiktok.data.models.users.User;
import io.github.jwdeveloper.tiktok.live.LiveClient;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Outbound adapter for TikTokLiveJava chat and supported LIVE activity events.
 * It does not use cookies, proxies, identity rotation or automatic reconnect loops.
 */
public final class TikTokLiveJavaClient implements LiveChatClient {
    private final AtomicReference<LiveClient> client = new AtomicReference<>();

    @Override public synchronized void connect(String username, Consumer<ChatMessage> onMessage, Consumer<Throwable> onFailure) {
        disconnect();
        try {
            LiveClient connected = TikTokLive.newClient(username)
                .onComment((liveClient, event) -> publish(onMessage, displayName(event.getUser()), event.getText(), ChatMessage.Type.CHAT))
                .onGift((liveClient, event) -> {
                    int quantity = Math.max(1, event.getCombo());
                    publish(onMessage, displayName(event.getUser()), "ha enviado el regalo " + event.getGift().getName() + " por " + quantity, ChatMessage.Type.EVENT);
                })
                .onFollow((liveClient, event) -> publish(onMessage, displayName(event.getUser()), "ahora sigue el canal", ChatMessage.Type.EVENT))
                .onSubscribe((liveClient, event) -> publish(onMessage, displayName(event.getUser()), "se ha suscrito al canal", ChatMessage.Type.EVENT))
                .onConnected((liveClient, event) -> announce(onMessage, "El LIVE ha iniciado"))
                .onLiveUnpaused((liveClient, event) -> announce(onMessage, "El LIVE se ha reanudado"))
                .onLivePaused((liveClient, event) -> announce(onMessage, "El LIVE se ha pausado"))
                .onLiveEnded((liveClient, event) -> announce(onMessage, "El LIVE ha finalizado"))
                .onError((liveClient, event) -> onFailure.accept(event.getException()))
                .buildAndConnect();
            client.set(connected);
        } catch (RuntimeException error) {
            client.set(null);
            onFailure.accept(error);
            throw new IllegalStateException("No se pudo iniciar TikTokLiveJava", error);
        }
    }

    @Override public synchronized void disconnect() {
        LiveClient current = client.getAndSet(null);
        if (current == null) return;
        current.disconnect();
    }

    private void announce(Consumer<ChatMessage> onMessage, String text) {
        publish(onMessage, "TikTok", text, ChatMessage.Type.EVENT);
    }

    private void publish(Consumer<ChatMessage> onMessage, String author, String text, ChatMessage.Type type) {
        onMessage.accept(new ChatMessage(author, text, Instant.now(), type));
    }

    private String displayName(User user) {
        if (user == null || user.getName() == null || user.getName().isBlank()) return "Usuario";
        return user.getName();
    }
}
