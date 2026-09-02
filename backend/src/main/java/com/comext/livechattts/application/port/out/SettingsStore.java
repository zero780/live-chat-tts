package com.comext.livechattts.application.port.out;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase.Settings;
import java.util.Optional;

public interface SettingsStore {
    Optional<Settings> load();
    void save(Settings settings);
}
