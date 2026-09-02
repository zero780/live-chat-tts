package com.comext.livechattts.application.port.out;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase.AudioOutput;
import java.util.List;

public interface AudioOutputPort {
    List<AudioOutput> availableOutputs();
    boolean supports(String outputId);
}
