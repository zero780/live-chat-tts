package com.comext.livechattts.application.port.in;

import com.comext.livechattts.domain.ConnectionState;

public interface ConnectionUseCase {
    void connect(String username);
    void disconnect();
    ConnectionSnapshot snapshot();

    record ConnectionSnapshot(ConnectionState state, String username, String detail) { }
}
