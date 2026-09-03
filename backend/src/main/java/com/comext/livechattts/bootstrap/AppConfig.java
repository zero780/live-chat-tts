package com.comext.livechattts.bootstrap;

import com.comext.livechattts.domain.Platform;
import com.comext.livechattts.domain.LiveSource;
import com.comext.livechattts.domain.TtsEngine;
import java.nio.file.Path;

public record AppConfig(Platform platform, LiveSource liveSource, TtsEngine ttsEngine, int port, int queueCapacity, String localApiToken, Path appDataDirectory, PiperRuntime piper) {
    public static AppConfig fromEnvironment() {
        Platform platform = Platform.fromEnvironment(System.getenv("APP_PLATFORM"));
        if (platform != Platform.WINDOWS) throw new IllegalStateException("Esta primera versión solo implementa WINDOWS");
        TtsEngine ttsEngine = TtsEngine.fromEnvironment(System.getenv("TTS_ENGINE"));
        return new AppConfig(platform, LiveSource.fromEnvironment(System.getenv("LIVE_SOURCE")), ttsEngine, integer("APP_PORT", 8787, 1024, 65535), integer("SPEECH_QUEUE_CAPACITY", 200, 10, 2000), System.getenv("LOCAL_API_TOKEN"), dataDirectory(), PiperRuntime.fromEnvironment());
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

    public record PiperRuntime(Path executable, Path modelPath) {
        public static PiperRuntime fromEnvironment() {
            Path executable = path("PIPER_EXECUTABLE", Path.of("backend", "piper-native", "piper", "piper.exe"));
            Path model = path("PIPER_MODEL", Path.of("backend", "piper", "models", "es_MX-claude-high.onnx"));
            return new PiperRuntime(executable, model);
        }

        private static String value(String name, String fallback) {
            String configured = System.getenv(name);
            return configured == null || configured.isBlank() ? fallback : configured.trim();
        }

        private static Path path(String name, Path fallback) {
            String configured = System.getenv(name);
            return (configured == null || configured.isBlank() ? fallback : Path.of(configured)).toAbsolutePath().normalize();
        }
    }
}
