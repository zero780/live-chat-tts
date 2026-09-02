package com.comext.livechattts.application.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/** Stores a sanitized operational error without retaining chat contents or secrets. */
public final class RuntimeDiagnostics {
    private final AtomicReference<Snapshot> latest = new AtomicReference<>(new Snapshot("", ""));

    public void record(String component, Throwable error) {
        String reason = error == null || error.getMessage() == null ? "Error no especificado" : TextSanitizer.sanitize(error.getMessage());
        latest.set(new Snapshot(component + ": " + reason, Instant.now().toString()));
    }

    public Snapshot snapshot() { return latest.get(); }
    public record Snapshot(String lastError, String lastErrorAt) { }
}
