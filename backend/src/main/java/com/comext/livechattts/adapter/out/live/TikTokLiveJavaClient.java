package com.comext.livechattts.adapter.out.live;

import com.comext.livechattts.application.port.out.LiveChatClient;
import com.comext.livechattts.domain.ChatMessage;
import io.github.jwdeveloper.tiktok.TikTokLive;
import io.github.jwdeveloper.tiktok.data.models.users.User;
import io.github.jwdeveloper.tiktok.data.models.Picture;
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
    private final AtomicReference<String> profileImageUrl = new AtomicReference<>("");

    @Override public synchronized void connect(String username, Consumer<ChatMessage> onMessage, Consumer<Throwable> onFailure) {
        disconnect();
        try {
            LiveClient connected = TikTokLive.newClient(username)
                .onComment((liveClient, event) -> publish(onMessage, displayName(event.getUser()), event.getText(), ChatMessage.Type.CHAT))
                .onGift((liveClient, event) -> {
                    int quantity = Math.max(1, event.getCombo());
                    publish(onMessage, displayName(event.getUser()), "ha enviado un regalo " + event.getGift().getName() + " por " + quantity, ChatMessage.Type.GIFT, quantity);
                })
                .onFollow((liveClient, event) -> publish(onMessage, displayName(event.getUser()), "ahora sigue el canal", ChatMessage.Type.FOLLOW))
                .onSubscribe((liveClient, event) -> publish(onMessage, displayName(event.getUser()), "se ha suscrito al canal", ChatMessage.Type.SUBSCRIBE))
                .onConnected((liveClient, event) -> announce(onMessage, "El LIVE ha iniciado", ChatMessage.Type.LIVE_STARTED))
                .onLiveUnpaused((liveClient, event) -> announce(onMessage, "El LIVE se ha reanudado", ChatMessage.Type.LIVE_RESUMED))
                .onLivePaused((liveClient, event) -> announce(onMessage, "El LIVE se ha pausado", ChatMessage.Type.LIVE_PAUSED))
                .onLiveEnded((liveClient, event) -> announce(onMessage, "El LIVE ha finalizado", ChatMessage.Type.LIVE_ENDED))
                .onError((liveClient, event) -> onFailure.accept(event.getException()))
                .buildAndConnect();
            client.set(connected);
            profileImageUrl.set(profileImageUrl(connected));
        } catch (RuntimeException error) {
            client.set(null);
            onFailure.accept(error);
            throw new IllegalStateException("No se pudo iniciar TikTokLiveJava", error);
        }
    }

    @Override public synchronized void disconnect() {
        LiveClient current = client.getAndSet(null);
        profileImageUrl.set("");
        if (current == null) return;
        current.disconnect();
    }

    @Override public String profileImageUrl() { return profileImageUrl.get(); }

    private String profileImageUrl(LiveClient liveClient) {
        try {
            if (liveClient.getRoomInfo() == null) return "";
            User host = liveClient.getRoomInfo().getHost();
            Picture picture = host == null ? null : host.getPicture();
            String link = picture == null ? "" : picture.getLink();
            return link == null ? "" : link.trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private void announce(Consumer<ChatMessage> onMessage, String text, ChatMessage.Type type) {
        publish(onMessage, "TikTok", text, type);
    }

    private void publish(Consumer<ChatMessage> onMessage, String author, String text, ChatMessage.Type type) {
        onMessage.accept(new ChatMessage(author, text, Instant.now(), type));
    }

    private void publish(Consumer<ChatMessage> onMessage, String author, String text, ChatMessage.Type type, int giftQuantity) {
        onMessage.accept(new ChatMessage(author, text, Instant.now(), type, giftQuantity));
    }

    private String displayName(User user) {
        if (user == null) return "Usuario";
        String profileName = user.getProfileName();
        if (profileName != null && !profileName.isBlank()) return profileName;
        String username = user.getName();
        return username == null || username.isBlank() ? "Usuario" : username;
    }
}
