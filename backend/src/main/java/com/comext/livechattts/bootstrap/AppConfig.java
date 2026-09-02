package com.comext.livechattts.bootstrap;

import com.comext.livechattts.domain.Platform;
import com.comext.livechattts.domain.LiveSource;
import java.nio.file.Path;

public record AppConfig(Platform platform, LiveSource liveSource, int port, int queueCapacity, String localApiToken, Path appDataDirectory) {
    public static AppConfig fromEnvironment() {
        Platform platform = Platform.fromEnvironment(System.getenv("APP_PLATFORM"));
        if (platform != Platform.WINDOWS) throw new IllegalStateException("Esta primera versión solo implementa WINDOWS");
        return new AppConfig(platform, LiveSource.fromEnvironment(System.getenv("LIVE_SOURCE")), integer("APP_PORT", 8787, 1024, 65535), integer("SPEECH_QUEUE_CAPACITY", 200, 10, 2000), System.getenv("LOCAL_API_TOKEN"), dataDirectory());
    }
    private static int integer(String name, int defaultValue, int min, int max) {
        String raw = System.getenv(name); if (raw == null || raw.isBlank()) return defaultValue;
        try { int value = Integer.parseInt(raw); if (value < min || value > max) throw new IllegalArgumentException(); return value; }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException(name + " debe estar entre " + min + " y " + max); }
    }
    private static Path dataDirectory() {
        String configured = System.getenv("APP_DATA_DIR");
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        String localAppData = System.getenv("LOCALAPPDATA");
        return (localAppData == null || localAppData.isBlank() ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(localAppData)).resolve("LiveChatTTS");
    }
}
