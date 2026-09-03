package com.comext.livechattts.bootstrap;

import com.comext.livechattts.adapter.in.http.LocalHttpServer;
import com.comext.livechattts.adapter.out.file.FileSettingsStore;
import com.comext.livechattts.adapter.out.live.LocalTestLiveChatClient;
import com.comext.livechattts.adapter.out.live.TikTokLiveJavaClient;
import com.comext.livechattts.adapter.out.piper.PiperTtsSpeechEngine;
import com.comext.livechattts.adapter.out.windows.WindowsDefaultAudioOutput;
import com.comext.livechattts.adapter.out.windows.WindowsSapiSpeechEngine;
import com.comext.livechattts.application.port.out.LiveChatClient;
import com.comext.livechattts.application.port.out.SpeechEngine;
import com.comext.livechattts.application.service.LiveConnectionService;
import com.comext.livechattts.application.service.RuntimeDiagnostics;
import com.comext.livechattts.application.service.SpeechQueueService;
import com.comext.livechattts.application.service.SpeechSettingsService;
import com.comext.livechattts.application.service.StatusService;
import com.comext.livechattts.domain.LiveSource;
import java.util.concurrent.CountDownLatch;

public final class Application {
    private Application() { }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnvironment();
        SpeechEngine speech = speechEngine(config);
        var settings = new SpeechSettingsService(speech, new WindowsDefaultAudioOutput(), new FileSettingsStore(config.appDataDirectory()));
        var diagnostics = new RuntimeDiagnostics();
        var queue = new SpeechQueueService(config.queueCapacity(), speech, settings, diagnostics);
        var localLive = new LocalTestLiveChatClient();
        localLive.setTestConsumer(queue::submit);
        LiveChatClient liveClient = config.liveSource() == LiveSource.TIKTOK_LIVE_JAVA ? new TikTokLiveJavaClient() : localLive;
        var connection = new LiveConnectionService(liveClient, queue, diagnostics);
        var server = new LocalHttpServer(config.port(), connection, settings, new StatusService(connection, queue, diagnostics), localLive, config.localApiToken());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.close(); queue.close(); }, "shutdown"));
        server.start();
        System.out.printf("Live Chat TTS local iniciado en http://127.0.0.1:%d/api/status%n", config.port());
        System.out.printf("Origen configurado: %s%n", config.liveSource());
        System.out.printf("Motor de voz configurado: %s%n", config.ttsEngine());
        System.out.printf("Datos y ajustes no sensibles: %s%n", config.appDataDirectory());
        if (args.length == 1 && "--self-test".equals(args[0])) {
            if (config.liveSource() != LiveSource.LOCAL_TEST) throw new IllegalArgumentException("El self-test requiere LIVE_SOURCE=LOCAL_TEST");
            try { SelfTest.run(config.port()); } finally { server.close(); queue.close(); }
            return;
        }
        new CountDownLatch(1).await();
    }

    private static SpeechEngine speechEngine(AppConfig config) throws Exception {
        return switch (config.ttsEngine()) {
            case SAPI -> new WindowsSapiSpeechEngine();
            case PIPER -> {
                PiperTtsSpeechEngine piper = new PiperTtsSpeechEngine(config.piper());
                piper.warmUp();
                yield piper;
            }
        };
    }
}
