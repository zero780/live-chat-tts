package com.comext.livechattts.adapter.out.windows;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase.AudioOutput;
import com.comext.livechattts.application.port.out.AudioOutputPort;
import java.util.List;

public final class WindowsDefaultAudioOutput implements AudioOutputPort {
    private static final AudioOutput DEFAULT = new AudioOutput("system-default", "Predeterminado de Windows");
    @Override public List<AudioOutput> availableOutputs() { return List.of(DEFAULT); }
    @Override public boolean supports(String outputId) { return DEFAULT.id().equals(outputId); }
}
