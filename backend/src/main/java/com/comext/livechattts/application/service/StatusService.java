package com.comext.livechattts.application.service;

import com.comext.livechattts.application.port.in.ConnectionUseCase;
import com.comext.livechattts.application.port.in.StatusUseCase;

public final class StatusService implements StatusUseCase {
    private final ConnectionUseCase connection;
    private final SpeechQueueService queue;
    private final RuntimeDiagnostics diagnostics;
    public StatusService(ConnectionUseCase connection, SpeechQueueService queue, RuntimeDiagnostics diagnostics) { this.connection = connection; this.queue = queue; this.diagnostics = diagnostics; }
    @Override public RuntimeStatus status() { RuntimeDiagnostics.Snapshot diagnostic = diagnostics.snapshot(); return new RuntimeStatus(connection.snapshot(), queue.isSpeaking(), queue.depth(), queue.accepted(), queue.dropped(), queue.rejected(), diagnostic.lastError(), diagnostic.lastErrorAt(), queue.messages()); }
}
