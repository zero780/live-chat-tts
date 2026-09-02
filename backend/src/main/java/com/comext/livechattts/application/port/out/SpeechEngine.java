package com.comext.livechattts.application.port.out;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase.Voice;
import java.util.List;

public interface SpeechEngine {
    List<Voice> voices();
    void speak(String text, String voiceId, int rate, String audioOutputId) throws Exception;
}
