package com.comext.livechattts.application.port.in;

import com.comext.livechattts.domain.ConnectionState;

public interface ConnectionUseCase {
    void connect(String username);
    void disconnect();
    ConnectionSnapshot snapshot();

    record ConnectionSnapshot(ConnectionState state, String username, String detail, String avatarUrl) {
        public ConnectionSnapshot(ConnectionState state, String username, String detail) {
            this(state, username, detail, "");
        }

        public ConnectionSnapshot {
            avatarUrl = avatarUrl == null ? "" : avatarUrl.trim();
        }
    }
}
