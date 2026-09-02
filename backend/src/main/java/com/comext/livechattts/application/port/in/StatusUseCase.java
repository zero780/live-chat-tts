package com.comext.livechattts.application.port.in;

import java.util.List;

public interface StatusUseCase {
    RuntimeStatus status();

    record RuntimeStatus(
            ConnectionUseCase.ConnectionSnapshot connection,
            boolean speaking,
            int queueDepth,
            long acceptedMessages,
            long droppedMessages,
            long rejectedMessages,
            String lastError,
            String lastErrorAt,
            List<SpeechMessage> messages) { }

    record SpeechMessage(long id, String author, String text, String receivedAt, String state) { }
}
