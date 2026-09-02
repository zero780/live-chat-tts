package com.comext.livechattts.adapter.out.windows;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase.Voice;
import com.comext.livechattts.application.port.out.SpeechEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Windows-only adapter. It invokes the local SAPI COM object through PowerShell. */
public final class WindowsSapiSpeechEngine implements SpeechEngine {
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);

    @Override public List<Voice> voices() {
        String script = "[Console]::OutputEncoding=[Text.UTF8Encoding]::new();"
                + "$paths=@('HKLM:\\SOFTWARE\\Microsoft\\Speech\\Voices\\Tokens\\*','HKCU:\\SOFTWARE\\Microsoft\\Speech\\Voices\\Tokens\\*');"
                + "$seen=@{}; Get-ItemProperty $paths -ErrorAction SilentlyContinue | ForEach-Object { $id=$_.PSChildName; $name=$_.'(default)'; if($name -and -not $seen.ContainsKey($id)){ $seen[$id]=$true; ($id+'|'+$name).Replace([Environment]::NewLine,' ') } }";
        try {
            Process process = powerShell(script).start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) return List.of();
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(line -> line.contains("|") && !line.startsWith("#<"))
                    .map(line -> line.split("\\|", 2)).map(parts -> new Voice(parts[0], parts[1])).toList();
        } catch (Exception exception) { return List.of(); }
    }

    @Override public void speak(String text, String voiceId, int rate, String audioOutputId) throws Exception {
        if (!"system-default".equals(audioOutputId)) throw new IllegalArgumentException("Salida de audio no soportada");
        String text64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        String voice64 = Base64.getEncoder().encodeToString(voiceId.getBytes(StandardCharsets.UTF_8));
        String script = "[Console]::OutputEncoding=[Text.UTF8Encoding]::new();"
                + "$t=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('" + text64 + "'));"
                + "$id=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('" + voice64 + "'));"
                + "$v=New-Object -ComObject SAPI.SpVoice;"
                + "$voice=$v.GetVoices()|Where-Object {$_.Id -eq $id -or $_.Id.EndsWith('\\'+$id)}|Select-Object -First 1;"
                + "if($null -eq $voice){$voice=$v.GetVoices()|Select-Object -First 1;}"
                + "if($null -eq $voice){throw 'No hay voces SAPI disponibles'};"
                + "$v.Voice=$voice;$v.Rate=" + rate + ";[void]$v.Speak($t);";
        Process process = powerShell(script).start();
        if (!process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IOException("SAPI excedió el tiempo máximo");
        }
        if (process.exitValue() != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            throw new IOException(output.isBlank() ? "SAPI no pudo reproducir el mensaje" : "SAPI no pudo reproducir el mensaje: " + output.replaceAll("\\s+", " "));
        }
    }

    private ProcessBuilder powerShell(String script) {
        return new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true);
    }
}
