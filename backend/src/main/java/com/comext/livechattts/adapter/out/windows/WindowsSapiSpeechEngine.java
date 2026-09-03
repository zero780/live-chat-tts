package com.comext.livechattts.adapter.out.windows;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase.Voice;
import com.comext.livechattts.application.port.out.SpeechEngine;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Windows-only adapter. It keeps one low-priority PowerShell/SAPI worker alive for all utterances. */
public final class WindowsSapiSpeechEngine implements SpeechEngine, AutoCloseable {
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);
    private static final String STOP_COMMAND = "EXIT";
    private static final String WORKER_STOPPED = "__SAPI_WORKER_STOPPED__";
    private final Object processLock = new Object();
    private BlockingQueue<String> responses;
    private Thread responseReader;
    private Process speechProcess;
    private BufferedWriter speechInput;
    private BufferedReader speechOutput;

    @Override public List<Voice> voices() {
        String script = "[Console]::OutputEncoding=[Text.UTF8Encoding]::new();"
                + "$paths=@('HKLM:\\SOFTWARE\\Microsoft\\Speech\\Voices\\Tokens\\*','HKCU:\\SOFTWARE\\Microsoft\\Speech\\Voices\\Tokens\\*');"
                + "$seen=@{}; Get-ItemProperty $paths -ErrorAction SilentlyContinue | ForEach-Object { $id=$_.PSChildName; $name=$_.'(default)'; if($name -and -not $seen.ContainsKey($id)){ $seen[$id]=$true; ($id+'|'+$name).Replace([Environment]::NewLine,' ') } }";
        try {
            Process process = powerShell(script).start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); return List.of(); }
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(line -> line.contains("|") && !line.startsWith("#<"))
                    .map(line -> line.split("\\|", 2)).map(parts -> new Voice(parts[0], parts[1])).toList();
        } catch (Exception exception) { return List.of(); }
    }

    @Override public void speak(String text, String voiceId, int rate, String audioOutputId) throws Exception {
        if (!"system-default".equals(audioOutputId)) throw new IllegalArgumentException("Unsupported audio output");
        String command = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "|"
                + Base64.getEncoder().encodeToString(voiceId.getBytes(StandardCharsets.UTF_8)) + "|" + rate;
        synchronized (processLock) {
            ensureProcess();
            speechInput.write(command);
            speechInput.newLine();
            speechInput.flush();
            String response;
            try {
                response = responses.poll(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                closeProcess();
                throw new IOException("SAPI request interrupted", exception);
            }
            if (response == null) {
                closeProcess();
                throw new TimeoutException("SAPI did not respond within " + COMMAND_TIMEOUT);
            }
            if (WORKER_STOPPED.equals(response)) {
                closeProcess();
                throw new IOException("SAPI worker stopped unexpectedly");
            }
            if (response.startsWith("ERR|")) {
                String detail = new String(Base64.getDecoder().decode(response.substring(4)), StandardCharsets.UTF_8);
                throw new IOException(detail.isBlank() ? "SAPI could not speak the message" : detail);
            }
            if (!"OK".equals(response)) throw new IOException("Unexpected SAPI worker response");
        }
    }

    private void ensureProcess() throws IOException {
        if (speechProcess != null && speechProcess.isAlive()) return;
        closeProcess();
        String script = "[Console]::OutputEncoding=[Text.UTF8Encoding]::new();"
                + "$ErrorActionPreference='Stop';"
                + "try{[Diagnostics.Process]::GetCurrentProcess().PriorityClass='BelowNormal'}catch{};"
                + "$v=New-Object -ComObject SAPI.SpVoice;"
                + "while($null -ne ($line=[Console]::In.ReadLine())){"
                + "if($line -eq 'EXIT'){break};"
                + "$parts=$line.Split('|',3);"
                + "try{"
                + "$t=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($parts[0]));"
                + "$id=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($parts[1]));"
                + "$voice=$v.GetVoices()|Where-Object {$_.Id -eq $id -or $_.Id.EndsWith('\\'+$id)}|Select-Object -First 1;"
                + "if($null -eq $voice){$voice=$v.GetVoices()|Select-Object -First 1};"
                + "if($null -eq $voice){throw 'No SAPI voices available'};"
                + "$v.Voice=$voice;$v.Rate=[int]$parts[2];[void]$v.Speak($t);"
                + "[Console]::WriteLine('OK');"
                + "}catch{[Console]::WriteLine('ERR|'+[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($_.Exception.Message)))};"
                + "[Console]::Out.Flush()};";
        String encodedScript = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encodedScript)
                .redirectErrorStream(false).start();
        Thread.ofVirtual().name("sapi-error-drain").start(() -> {
            try { process.getErrorStream().transferTo(OutputStream.nullOutputStream()); }
            catch (IOException ignored) { }
        });
        speechProcess = process;
        speechInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        speechOutput = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        responses = new LinkedBlockingQueue<>();
        BlockingQueue<String> responseQueue = responses;
        BufferedReader output = speechOutput;
        responseReader = Thread.ofVirtual().name("sapi-response-reader").start(() -> {
            try {
                String response;
                while ((response = output.readLine()) != null) responseQueue.offer(response);
            } catch (IOException ignored) {
                // Process shutdown closes the stream and is expected here.
            } finally {
                responseQueue.offer(WORKER_STOPPED);
            }
        });
    }

    private ProcessBuilder powerShell(String script) {
        return new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true);
    }

    private void closeProcess() {
        if (speechInput != null) {
            try { speechInput.write(STOP_COMMAND); speechInput.newLine(); speechInput.flush(); }
            catch (IOException ignored) { }
            try { speechInput.close(); } catch (IOException ignored) { }
        }
        if (speechOutput != null) {
            try { speechOutput.close(); } catch (IOException ignored) { }
        }
        if (responseReader != null) responseReader.interrupt();
        if (speechProcess != null && speechProcess.isAlive()) {
            try { if (!speechProcess.waitFor(1, TimeUnit.SECONDS)) speechProcess.destroyForcibly(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); speechProcess.destroyForcibly(); }
        }
        speechProcess = null;
        speechInput = null;
        speechOutput = null;
        responses = null;
        responseReader = null;
    }

    @Override public void close() { synchronized (processLock) { closeProcess(); } }
}
