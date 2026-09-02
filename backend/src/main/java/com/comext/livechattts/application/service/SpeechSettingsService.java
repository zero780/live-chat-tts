package com.comext.livechattts.application.service;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase;
import com.comext.livechattts.application.port.out.AudioOutputPort;
import com.comext.livechattts.application.port.out.SpeechEngine;
import com.comext.livechattts.application.port.out.SettingsStore;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class SpeechSettingsService implements SpeechSettingsUseCase {
    private final SpeechEngine speechEngine;
    private final AudioOutputPort audioOutputPort;
    private final AtomicReference<Settings> settings;
    private final SettingsStore store;
    private final List<Voice> voices;

    public SpeechSettingsService(SpeechEngine speechEngine, AudioOutputPort audioOutputPort, SettingsStore store) {
        this.speechEngine = speechEngine;
        this.audioOutputPort = audioOutputPort;
        this.store = store;
        this.voices = List.copyOf(speechEngine.voices());
        String initialVoice = voices.stream().findFirst().map(Voice::id).orElse("");
        Settings fallback = new Settings(initialVoice, 0, "system-default");
        Settings saved = store.load().orElse(fallback);
        this.settings = new AtomicReference<>(isValid(saved) ? saved : fallback);
    }

    @Override public Settings settings() { return settings.get(); }
    @Override public List<Voice> voices() { return voices; }
    @Override public List<AudioOutput> audioOutputs() { return audioOutputPort.availableOutputs(); }

    @Override public Settings update(String voiceId, int speechRate, String audioOutputId) {
        if (speechRate < -10 || speechRate > 10) throw new IllegalArgumentException("speechRate debe estar entre -10 y 10");
        if (voices().stream().noneMatch(voice -> voice.id().equals(voiceId))) throw new IllegalArgumentException("Voz no instalada");
        if (!audioOutputPort.supports(audioOutputId)) throw new IllegalArgumentException("Salida de audio no disponible");
        Settings next = new Settings(voiceId, speechRate, audioOutputId);
        store.save(next);
        settings.set(next);
        return next;
    }

    private boolean isValid(Settings candidate) {
        return candidate.speechRate() >= -10 && candidate.speechRate() <= 10
                && voices.stream().anyMatch(voice -> voice.id().equals(candidate.voiceId()))
                && audioOutputPort.supports(candidate.audioOutputId());
    }
}
