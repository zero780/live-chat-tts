package com.comext.livechattts.adapter.out.audio;

import com.comext.livechattts.application.port.out.AudioCuePlayer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/** Decodes a bundled MP3 once and reuses its PCM data for all gift alerts. */
public final class CachedMp3CuePlayer implements AudioCuePlayer {
    private static final int MAX_PCM_BYTES = 16 * 1024 * 1024;
    private final AudioFormat format;
    private final byte[] pcm;
    private volatile boolean closed;
    private volatile SourceDataLine activeLine;

    private CachedMp3CuePlayer(AudioFormat format, byte[] pcm) {
        this.format = format;
        this.pcm = pcm;
    }

    public static CachedMp3CuePlayer fromResource(String resourceName) throws Exception {
        try (InputStream source = CachedMp3CuePlayer.class.getResourceAsStream(resourceName)) {
            if (source == null) throw new IOException("Gift alert resource was not found: " + resourceName);
            return decode(source);
        }
    }

    @Override public void play() throws LineUnavailableException {
        if (closed) return;
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        activeLine = line;
        try {
            line.open(format);
            line.start();
            line.write(pcm, 0, pcm.length);
            line.drain();
        } finally {
            activeLine = null;
            line.stop();
            line.close();
        }
    }

    @Override public void close() {
        closed = true;
        SourceDataLine line = activeLine;
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
        }
    }

    private static CachedMp3CuePlayer decode(InputStream source) throws Exception {
        Bitstream stream = new Bitstream(source);
        Decoder decoder = new Decoder();
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        int sampleRate = 0;
        int channels = 0;
        try {
            Header header;
            while ((header = stream.readFrame()) != null) {
                SampleBuffer decoded = (SampleBuffer) decoder.decodeFrame(header, stream);
                if (sampleRate == 0) {
                    sampleRate = decoded.getSampleFrequency();
                    channels = decoded.getChannelCount();
                } else if (sampleRate != decoded.getSampleFrequency() || channels != decoded.getChannelCount()) {
                    throw new IOException("Gift alert MP3 changes audio format between frames");
                }
                writeLittleEndianPcm(pcm, decoded.getBuffer(), decoded.getBufferLength());
                if (pcm.size() > MAX_PCM_BYTES) throw new IOException("Gift alert PCM exceeds the memory limit");
                stream.closeFrame();
            }
        } finally {
            stream.close();
        }
        if (sampleRate <= 0 || channels <= 0 || pcm.size() == 0) throw new IOException("Gift alert MP3 has no playable audio");
        return new CachedMp3CuePlayer(new AudioFormat(sampleRate, 16, channels, true, false), pcm.toByteArray());
    }

    private static void writeLittleEndianPcm(ByteArrayOutputStream target, short[] samples, int length) {
        Objects.checkFromIndexSize(0, length, samples.length);
        for (int index = 0; index < length; index++) {
            short sample = samples[index];
            target.write(sample & 0xff);
            target.write((sample >>> 8) & 0xff);
        }
    }
}
