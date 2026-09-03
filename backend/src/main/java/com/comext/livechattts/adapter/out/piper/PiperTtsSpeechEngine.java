package com.comext.livechattts.adapter.out.piper;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase.Voice;
import com.comext.livechattts.application.port.out.SpeechEngine;
import com.comext.livechattts.bootstrap.AppConfig.PiperRuntime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.*;

/** Persistent native Piper process with automatic recovery. */
public final class PiperTtsSpeechEngine implements SpeechEngine, AutoCloseable {
  private static final String ID="piper:es_MX-claude-high";
  private static final Voice VOICE=new Voice(ID,"Piper - Espanol Mexico - Claude (alta)");
  private final PiperRuntime runtime; private final Object lock=new Object(); private Process process; private Path outputDir;
  public PiperTtsSpeechEngine(PiperRuntime runtime){this.runtime=runtime;}
  @Override public List<Voice> voices(){return List.of(VOICE);}
  public void warmUp() throws IOException { synchronized(lock){ ensureProcess(); } }
  @Override public void speak(String text,String voiceId,int rate,String audioOutputId) throws Exception {
    if(!ID.equals(voiceId)) throw new IllegalArgumentException("Piper voice is not available");
    if(!"system-default".equals(audioOutputId)) throw new IllegalArgumentException("Unsupported audio output");
    synchronized(lock){ ensureProcess(); Path before=latest(); double length=Math.max(.5,Math.min(1.5,1.0-rate/20.0));
      try { process.getOutputStream().write((text+System.lineSeparator()).getBytes(StandardCharsets.UTF_8)); process.getOutputStream().flush();
        Path wav=waitForFile(before,90); play(wav); Files.deleteIfExists(wav);
      } catch(Exception e){ restart(); throw e; }
    }
  }
  private void ensureProcess() throws IOException { if(process!=null&&process.isAlive()) return; restart();
    if(!Files.isRegularFile(runtime.executable())||!Files.isRegularFile(runtime.modelPath())) throw new IOException("Piper resources not found");
    try { outputDir=Files.createDirectories(Files.createTempDirectory("livechattts-piper-"));
      ProcessBuilder b=new ProcessBuilder(runtime.executable().toString(),"--model",runtime.modelPath().toString(),"--output_dir",outputDir.toString(),"--quiet"); b.directory(runtime.executable().toAbsolutePath().getParent().toFile()); b.redirectErrorStream(true); process=b.start();
    } catch(IOException e){restart();throw e;}
  }
  private Path latest(){ try{return Files.list(outputDir).filter(p->p.toString().endsWith(".wav")).max(Comparator.comparingLong(this::mtime)).orElse(null);}catch(IOException e){return null;} }
  private long mtime(Path p){try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException e){return 0;}}
  private Path waitForFile(Path before,long seconds)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds); while(System.nanoTime()<end){Path p=latest();if(p!=null&&(before==null||!p.equals(before)&&mtime(p)>=mtime(before))&&isStableWav(p))return p;Thread.sleep(25);}throw new IOException("Piper did not produce audio");}
  private boolean isStableWav(Path p){try{long a=Files.size(p);if(a<44)return false;try(var in=Files.newInputStream(p)){byte[] h=in.readNBytes(12);if(h.length<12||h[0]!='R'||h[1]!='I'||h[2]!='F'||h[3]!='F'||h[8]!='W'||h[9]!='A'||h[10]!='V'||h[11]!='E')return false;}Thread.sleep(80);return a==Files.size(p);}catch(Exception e){return false;}}
  private void play(Path wav)throws Exception {try(AudioInputStream in=AudioSystem.getAudioInputStream(wav.toFile());SourceDataLine line=AudioSystem.getSourceDataLine(in.getFormat())){line.open(in.getFormat());line.start();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)line.write(b,0,n);line.drain();line.stop();}}
  private void restart(){if(process!=null){process.destroyForcibly();process=null;}if(outputDir!=null)try{Files.walk(outputDir).sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}outputDir=null;}
  @Override public void close(){synchronized(lock){restart();}}
}
