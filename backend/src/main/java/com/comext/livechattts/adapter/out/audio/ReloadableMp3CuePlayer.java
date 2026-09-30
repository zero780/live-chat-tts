package com.comext.livechattts.adapter.out.audio;

import com.comext.livechattts.application.port.out.AudioCuePlayer;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Stores and atomically swaps the local MP3 used as the gift alert. */
public final class ReloadableMp3CuePlayer implements AudioCuePlayer {
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private final Object lock = new Object();
    private final Path customFile;
    private final Path customNameFile;
    private CachedMp3CuePlayer current;
    private boolean closed;

    private ReloadableMp3CuePlayer(Path customFile, CachedMp3CuePlayer current) {
        this.customFile = customFile;
        this.customNameFile = customFile.resolveSibling("gift-alert-name.txt");
        this.current = current;
    }

    public static ReloadableMp3CuePlayer create(Path appDataDirectory, String fallbackResource) throws Exception {
        Path customFile = appDataDirectory.resolve("gift-alert.mp3");
        CachedMp3CuePlayer initial = Files.isRegularFile(customFile)
                ? CachedMp3CuePlayer.fromFile(customFile)
                : CachedMp3CuePlayer.fromResource(fallbackResource);
        return new ReloadableMp3CuePlayer(customFile, initial);
    }

    public void replace(byte[] contents, String originalName) throws Exception {
        Objects.requireNonNull(contents, "contents");
        String safeName = safeFileName(originalName);
        if (contents.length == 0 || contents.length > MAX_FILE_BYTES) throw new IllegalArgumentException("El MP3 debe pesar entre 1 byte y 10 MB");
        Files.createDirectories(customFile.getParent());
        Path temporary = customFile.resolveSibling(customFile.getFileName() + ".tmp");
        try {
            Files.write(temporary, contents);
            CachedMp3CuePlayer candidate = CachedMp3CuePlayer.fromFile(temporary);
            moveReplacing(temporary, customFile);
            Files.writeString(customNameFile, safeName, StandardCharsets.UTF_8);
            synchronized (lock) {
                if (closed) { candidate.close(); return; }
                CachedMp3CuePlayer previous = current;
                current = candidate;
                previous.close();
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public String selectedFileName() {
        if (!Files.isRegularFile(customFile)) return "";
        try { return safeFileName(Files.readString(customNameFile, StandardCharsets.UTF_8).trim()); }
        catch (Exception ignored) { return "gift-alert.mp3"; }
    }

    @Override public void play() throws Exception {
        synchronized (lock) {
            if (!closed) current.play();
        }
    }

    @Override public void close() {
        synchronized (lock) {
            closed = true;
            current.close();
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static String safeFileName(String name) {
        String value = name == null ? "" : Path.of(name).getFileName().toString().trim();
        if (!value.toLowerCase(java.util.Locale.ROOT).endsWith(".mp3") || value.length() > 120) throw new IllegalArgumentException("Solo se permiten archivos MP3");
        return value;
    }
}
