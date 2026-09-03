package com.comext.livechattts.application.port.out;

/** Plays a short local audio cue after a qualifying event has been spoken. */
public interface AudioCuePlayer extends AutoCloseable {
    void play() throws Exception;
    @Override default void close() { }
}
