package com.mediadownloader.app;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Streams decoded audio directly into 16 kHz mono WAV; no FFmpeg or full-size PCM intermediate. */
public final class AudioConverter {
  private AudioConverter() {}
  public interface Progress { void onProgress(int percent, long audioMs, long durationMs); }
  private static final long STALL_MS=30000;
  private static final long DEQUEUE_US=10000;
  public static File convert(File input,File wav)throws Exception{return convert(input,wav,null);}
  public static File convert(File input,File wav,Progress progress)throws Exception{
    MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
    boolean success=false;
    try {
      extractor.setDataSource(input.getAbsolutePath());
      MediaFormat format=null;
      for(int i=0;i<extractor.getTrackCount();i++){
        MediaFormat candidate=extractor.getTrackFormat(i);
        String mime=candidate.getString(MediaFormat.KEY_MIME);
        if(mime!=null&&mime.startsWith("audio/")){format=candidate;extractor.selectTrack(i);break;}
      }
      if(format==null)throw new IOException("Nenhuma trilha de áudio encontrada no arquivo.");
      long durationUs=format.containsKey(MediaFormat.KEY_DURATION)?format.getLong(MediaFormat.KEY_DURATION):-1;
      int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      int channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
      int encoding=AudioFormat.ENCODING_PCM_16BIT;
      codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      codec.configure(format,null,null,0);codec.start();
      MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
      boolean inputEnd=false,outputEnd=false;
      long lastActivity=SystemClock.elapsedRealtime();long lastReport=0;
      long lastMediaUs=0;long written=0;
      Resampler resampler=new Resampler(rate,channels);
      try(RandomAccessFile output=new RandomAccessFile(wav,"rw")){
        output.setLength(0);output.write(new byte[44]);
        while(!outputEnd){
          if(Thread.currentThread().isInterrupted())throw new IOException("Conversão interrompida.");
          if(!inputEnd){
            int inIndex=codec.dequeueInputBuffer(DEQUEUE_US);
            if(inIndex>=0){
              ByteBuffer in=codec.getInputBuffer(inIndex);
              if(in==null)throw new IOException("Buffer de entrada indisponível.");
              in.clear();int count=extractor.readSampleData(in,0);
              if(count<0){codec.queueInputBuffer(inIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}
              else {long pts=extractor.getSampleTime();codec.queueInputBuffer(inIndex,0,count,Math.max(0,pts),0);extractor.advance();lastMediaUs=Math.max(lastMediaUs,pts);}
              lastActivity=SystemClock.elapsedRealtime();
            }
          }
          int outIndex=codec.dequeueOutputBuffer(info,DEQUEUE_US);
          if(outIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
            MediaFormat actual=codec.getOutputFormat();
            int newRate=actual.containsKey(MediaFormat.KEY_SAMPLE_RATE)?actual.getInteger(MediaFormat.KEY_SAMPLE_RATE):rate;
            int newChannels=actual.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT):channels;
            if(resampler.frames>0&&(rate!=newRate||channels!=newChannels))throw new IOException("Formato de áudio mudou durante a conversão.");
            rate=newRate;channels=newChannels;
            encoding=actual.containsKey(MediaFormat.KEY_PCM_ENCODING)?actual.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
            if(resampler.frames==0)resampler=new Resampler(rate,channels);
            lastActivity=SystemClock.elapsedRealtime();
          } else if(outIndex>=0){
            try{
              if(info.size>0){
                ByteBuffer data=codec.getOutputBuffer(outIndex);
                if(data==null)throw new IOException("Buffer de saída indisponível.");
                data.position(info.offset);data.limit(info.offset+info.size);data.order(ByteOrder.LITTLE_ENDIAN);
                written+=resampler.append(data,encoding,output);
                lastMediaUs=Math.max(lastMediaUs,info.presentationTimeUs);
              }
              outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
              lastActivity=SystemClock.elapsedRealtime();
            }finally{codec.releaseOutputBuffer(outIndex,false);}
          }
          long now=SystemClock.elapsedRealtime();
          if(now-lastActivity>STALL_MS)throw new IOException("Decodificador sem resposta por 30 segundos. Formato de áudio ou codec incompatível.");
          if(progress!=null&&(now-lastReport>=350||outputEnd)){
            int pct=durationUs>0?(int)Math.min(99,Math.max(0,lastMediaUs*100/durationUs)):-1;
            progress.onProgress(outputEnd?100:pct,lastMediaUs/1000,durationUs/1000);
            lastReport=now;
          }
        }
        written+=resampler.finish(output);
        if(written<=0)throw new IOException("O áudio decodificado está vazio.");
        if(written>0xffffffffL-36)throw new IOException("Áudio longo demais para o formato WAV.");
        output.seek(0);writeHeader(output,written);
      }
      success=true;return wav;
    }catch(Exception ex){Log.e("TubTok","Falha na conversão de áudio",ex);throw ex;}
    finally{
      if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}
      extractor.release();
      if(!success&&wav.exists()&&!wav.delete())Log.w("TubTok","Não foi possível excluir WAV incompleto");
    }
  }
  private static final class Resampler {
    final int rate,channels;long frames=0,nextOut=0;double previous=0;boolean hasPrevious=false;
    Resampler(int rate,int channels)throws IOException{
      if(rate<8000||rate>384000||channels<1||channels>8)throw new IOException("Formato PCM inválido: "+rate+" Hz, "+channels+" canais.");
      this.rate=rate;this.channels=channels;
    }
    long append(ByteBuffer data,int encoding,RandomAccessFile out)throws IOException{
      int bytesPerSample=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:encoding==AudioFormat.ENCODING_PCM_16BIT?2:0;
      if(bytesPerSample==0)throw new IOException("Formato PCM não suportado: "+encoding);
      int frameBytes=bytesPerSample*channels;
      if(data.remaining()%frameBytes!=0)throw new IOException("Quadro PCM incompleto.");
      long produced=0;
      while(data.remaining()>=frameBytes){
        double mono=0;
        for(int c=0;c<channels;c++){
          if(bytesPerSample==2)mono+=data.getShort();
          else mono+=Math.max(-1,Math.min(1,data.getFloat()))*32767.0;
        }
        mono/=channels;
        if(!hasPrevious){previous=mono;hasPrevious=true;}
        while(nextOut*(double)rate/16000.0<=frames){
          double position=nextOut*(double)rate/16000.0;
          double frac=Math.max(0,Math.min(1,position-(frames-1)));
          double value=frames==0?mono:previous+(mono-previous)*frac;
          int sample=(int)Math.round(Math.max(-32768,Math.min(32767,value)));
          out.write(sample&255);out.write((sample>>>8)&255);
          produced+=2;nextOut++;
        }
        previous=mono;frames++;
      }
      return produced;
    }
    long finish(RandomAccessFile out)throws IOException{
      if(!hasPrevious)return 0;
      long produced=0;
      long total=(long)Math.ceil(frames*16000.0/rate);
      while(nextOut<total){int sample=(int)Math.round(Math.max(-32768,Math.min(32767,previous)));out.write(sample&255);out.write((sample>>>8)&255);produced+=2;nextOut++;}
      return produced;
    }
  }
  private static void le16(RandomAccessFile f,int v)throws IOException{f.write(v&255);f.write((v>>>8)&255);}
  private static void le32(RandomAccessFile f,long v)throws IOException{for(int i=0;i<4;i++)f.write((int)(v>>>(i*8))&255);}
  private static void writeHeader(RandomAccessFile f,long bytes)throws IOException{
    f.writeBytes("RIFF");le32(f,bytes+36);f.writeBytes("WAVEfmt ");le32(f,16);
    le16(f,1);le16(f,1);le32(f,16000);le32(f,32000);le16(f,2);le16(f,16);
    f.writeBytes("data");le32(f,bytes);
  }
}
