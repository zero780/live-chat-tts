package com.comext.livechattts.adapter.out.file;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase.Settings;
import com.comext.livechattts.application.port.out.SettingsStore;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

/** Persists non-sensitive UI settings atomically. API tokens and TikTok sessions are never stored here. */
public final class FileSettingsStore implements SettingsStore {
    private final Path settingsFile;

    public FileSettingsStore(Path appDataDirectory) {
        this.settingsFile = appDataDirectory.resolve("settings.properties");
    }

    @Override public Optional<Settings> load() {
        if (!Files.isRegularFile(settingsFile)) return Optional.empty();
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return Optional.of(new Settings(properties.getProperty("voiceId", ""), Integer.parseInt(properties.getProperty("speechRate", "0")), properties.getProperty("audioOutputId", "system-default")));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override public void save(Settings settings) {
        try {
            Files.createDirectories(settingsFile.getParent());
            Path temporary = settingsFile.resolveSibling(settingsFile.getFileName() + ".tmp");
            Properties properties = new Properties();
            properties.setProperty("voiceId", settings.voiceId());
            properties.setProperty("speechRate", Integer.toString(settings.speechRate()));
            properties.setProperty("audioOutputId", settings.audioOutputId());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { properties.store(writer, "Live Chat TTS settings"); }
            try { Files.move(temporary, settingsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudieron guardar los ajustes locales", exception);
        }
    }
}
