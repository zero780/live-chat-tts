package com.comext.livechattts.application.port.out;

import com.comext.livechattts.domain.ChatMessage;
import java.util.function.Consumer;

public interface LiveChatClient {
    void connect(String username, Consumer<ChatMessage> onMessage, Consumer<Throwable> onFailure);
    void disconnect();
    default String profileImageUrl() { return ""; }
}
