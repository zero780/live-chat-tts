package com.comext.livechattts.adapter.out.piper;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase.Voice;
import com.comext.livechattts.application.port.out.SpeechEngine;
import com.comext.livechattts.bootstrap.AppConfig.PiperRuntime;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/** Local Piper adapter that keeps the ONNX model loaded in one worker process. */
public final class PiperTtsSpeechEngine implements SpeechEngine, AutoCloseable {
    private static final String VOICE_ID = "piper:es_MX-claude-high";
    private static final Voice VOICE = new Voice(VOICE_ID, "Piper · Español México · Claude (alta)");
    private static final Duration START_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);
    private static final int RESPONSE_BUFFER_CAPACITY = 64;
    private static final String STOP_COMMAND = "EXIT";
    private static final String WORKER_STOPPED = "__PIPER_WORKER_STOPPED__";
    private final PiperRuntime runtime;
    private final Object processLock = new Object();
    private Process process;
    private BufferedWriter input;
    private BufferedReader output;
    private BlockingQueue<String> responses;
    private Thread responseReader;

    public PiperTtsSpeechEngine(PiperRuntime runtime) {
        this.runtime = runtime;
    }

    @Override public List<Voice> voices() { return List.of(VOICE); }

    /** Loads the Piper model before the first LIVE message arrives. */
    public void warmUp() throws IOException {
        synchronized (processLock) { ensureProcess(); }
    }

    @Override public void speak(String text, String voiceId, int rate, String audioOutputId) throws Exception {
        if (!VOICE_ID.equals(voiceId)) throw new IllegalArgumentException("Piper voice is not available");
        if (!"system-default".equals(audioOutputId)) throw new IllegalArgumentException("Unsupported audio output");
        String command = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "|" + rate;
        synchronized (processLock) {
            ensureProcess();
            input.write(command);
            input.newLine();
            input.flush();
            playResponse();
        }
    }

    private void playResponse() throws Exception {
        SourceDataLine audio = null;
        try {
            while (true) {
                String response = nextResponse(COMMAND_TIMEOUT);
                if (response.startsWith("AUDIO|")) {
                    String[] parts = response.split("\\|", 3);
                    if (parts.length != 3) throw new IOException("Invalid Piper audio response");
                    int sampleRate = Integer.parseInt(parts[1]);
                    byte[] pcm = Base64.getDecoder().decode(parts[2]);
                    if (audio == null) audio = openAudio(sampleRate);
                    audio.write(pcm, 0, pcm.length);
                    continue;
                }
                if (response.startsWith("ERR|")) {
                    String detail = new String(Base64.getDecoder().decode(response.substring(4)), StandardCharsets.UTF_8);
                    throw new IOException(detail.isBlank() ? "Piper could not synthesize the message" : detail);
                }
                if ("OK".equals(response)) {
                    if (audio != null) audio.drain();
                    return;
                }
                throw new IOException("Unexpected Piper worker response");
            }
        } finally {
            if (audio != null) {
                audio.stop();
                audio.close();
            }
        }
    }

    private SourceDataLine openAudio(int sampleRate) throws LineUnavailableException {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format);
        line.start();
        return line;
    }

    private String nextResponse(Duration timeout) throws IOException, TimeoutException {
        String response;
        try {
            response = responses.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            closeProcess();
            throw new IOException("Piper request interrupted", exception);
        }
        if (response == null) {
            closeProcess();
            throw new TimeoutException("Piper did not respond within " + timeout);
        }
        if (WORKER_STOPPED.equals(response)) {
            closeProcess();
            throw new IOException("Piper worker stopped unexpectedly");
        }
        return response;
    }

    private void ensureProcess() throws IOException {
        if (process != null && process.isAlive()) return;
        closeProcess();
        if (!Files.isRegularFile(runtime.workerScript())) throw new IOException("Piper worker script was not found: " + runtime.workerScript());
        if (!Files.isRegularFile(runtime.modelPath())) throw new IOException("Piper model was not found: " + runtime.modelPath());

        Process started = new ProcessBuilder(runtime.pythonCommand(), runtime.workerScript().toString(), "--model", runtime.modelPath().toString())
                .redirectErrorStream(false).start();
        Thread.ofVirtual().name("piper-error-drain").start(() -> {
            try { started.getErrorStream().transferTo(OutputStream.nullOutputStream()); }
            catch (IOException ignored) { }
        });
        process = started;
        input = new BufferedWriter(new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
        output = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8));
        responses = new ArrayBlockingQueue<>(RESPONSE_BUFFER_CAPACITY);
        BlockingQueue<String> responseQueue = responses;
        BufferedReader workerOutput = output;
        responseReader = Thread.ofVirtual().name("piper-response-reader").start(() -> {
            try {
                String response;
                while ((response = workerOutput.readLine()) != null) responseQueue.put(response);
            } catch (IOException ignored) {
                // Process shutdown closes the stream and is expected here.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                responseQueue.offer(WORKER_STOPPED);
            }
        });
        try {
            String ready = nextResponse(START_TIMEOUT);
            if (!"READY".equals(ready)) throw new IOException("Unexpected Piper worker startup response");
        } catch (IOException | TimeoutException exception) {
            closeProcess();
            throw new IOException("Piper could not start", exception);
        }
    }

    private void closeProcess() {
        if (input != null) {
            try { input.write(STOP_COMMAND); input.newLine(); input.flush(); }
            catch (IOException ignored) { }
            try { input.close(); } catch (IOException ignored) { }
        }
        if (output != null) {
            try { output.close(); } catch (IOException ignored) { }
        }
        if (responseReader != null) responseReader.interrupt();
        if (process != null && process.isAlive()) {
            try { if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
        }
        process = null;
        input = null;
        output = null;
        responses = null;
        responseReader = null;
    }

    @Override public void close() { synchronized (processLock) { closeProcess(); } }
}
