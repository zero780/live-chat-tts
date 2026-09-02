package com.comext.livechattts.application.port.in;

import java.util.List;

public interface SpeechSettingsUseCase {
    Settings settings();
    Settings update(String voiceId, int speechRate, String audioOutputId);
    List<Voice> voices();
    List<AudioOutput> audioOutputs();

    record Settings(String voiceId, int speechRate, String audioOutputId) { }
    record Voice(String id, String displayName) { }
    record AudioOutput(String id, String displayName) { }
}
