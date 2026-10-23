package com.mediadownloader.app;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.AudioFormat;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Converts Android-decodable audio containers to 16kHz mono PCM16 WAV.
 * Uses MediaExtractor/MediaCodec (no external ffmpeg executables).
 * Intermediate PCM is streamed to disk to avoid holding long videos in RAM.
 */
public final class AudioConverter {
  private AudioConverter() {}
  public static File convert(File input, File wav) throws Exception {
    MediaExtractor extractor=new MediaExtractor();
    MediaCodec codec=null;
    File pcm=new File(wav.getParentFile(),"decoded.pcm");
    try {
      extractor.setDataSource(input.getAbsolutePath());
      MediaFormat format=null;
      for(int i=0;i<extractor.getTrackCount();i++){
        MediaFormat f=extractor.getTrackFormat(i);
        String mime=f.getString(MediaFormat.KEY_MIME);
        if(mime!=null&&mime.startsWith("audio/")){format=f;extractor.selectTrack(i);break;}
      }
      if(format==null)throw new IOException("Nenhuma trilha de áudio reconhecida: "+input.getName());
      String mime=format.getString(MediaFormat.KEY_MIME);
      codec=MediaCodec.createDecoderByType(mime);
      codec.configure(format,null,null,0);
      codec.start();
      MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
      boolean inputDone=false,outputDone=false;
      int sampleRate=format.containsKey(MediaFormat.KEY_SAMPLE_RATE)?format.getInteger(MediaFormat.KEY_SAMPLE_RATE):44100;
      int channels=format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?format.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1;
      int encoding=AudioFormat.ENCODING_PCM_16BIT;
      long lastProgress=System.currentTimeMillis();
      try(OutputStream out=new BufferedOutputStream(new FileOutputStream(pcm))){
        while(!outputDone){
          if(!inputDone){
            int index=codec.dequeueInputBuffer(10000);
            if(index>=0){
              ByteBuffer buffer=codec.getInputBuffer(index);
              if(buffer==null)throw new IOException("Buffer de entrada indisponível");
              buffer.clear();
              int size=extractor.readSampleData(buffer,0);
              if(size<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
              else{codec.queueInputBuffer(index,0,size,extractor.getSampleTime(),0);extractor.advance();}
            }
          }
          int index=codec.dequeueOutputBuffer(info,10000);
          if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
            MediaFormat f=codec.getOutputFormat();
            if(f.containsKey(MediaFormat.KEY_SAMPLE_RATE))sampleRate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            if(f.containsKey(MediaFormat.KEY_CHANNEL_COUNT))channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            encoding=f.containsKey(MediaFormat.KEY_PCM_ENCODING)?f.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
          }else if(index>=0){
            if(info.size>0){
              ByteBuffer b=codec.getOutputBuffer(index);
              if(b==null)throw new IOException("Buffer de saída indisponível");
              b.position(info.offset);b.limit(info.offset+info.size);
              if(encoding==AudioFormat.ENCODING_PCM_16BIT){
                byte[] chunk=new byte[Math.min(65536,info.size)];
                while(b.hasRemaining()){int n=Math.min(b.remaining(),chunk.length);b.get(chunk,0,n);out.write(chunk,0,n);}
              }else if(encoding==AudioFormat.ENCODING_PCM_FLOAT){
                b.order(ByteOrder.LITTLE_ENDIAN);
                while(b.remaining()>=4){float v=b.getFloat();write16(out,(short)(Math.max(-1,Math.min(1,v))*32767));}
              }else throw new IOException("Formato PCM não suportado pelo conversor: "+encoding);
            }
            outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
            codec.releaseOutputBuffer(index,false);
          }
          if(System.currentTimeMillis()-lastProgress>120000)throw new IOException("Tempo limite ao decodificar áudio");
          if(index>=0)lastProgress=System.currentTimeMillis();
        }
      }
      if(sampleRate<=0||channels<=0||channels>8)throw new IOException("Formato de áudio inválido: "+sampleRate+"Hz, "+channels+" canais");
      resampleToWav(pcm,wav,sampleRate,channels);
      return wav;
    }finally{
      if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}
      extractor.release();
      if(pcm.exists())pcm.delete();
    }
  }
  private static void resampleToWav(File pcm,File wav,int rate,int channels)throws IOException{
    long frames=pcm.length()/(2L*channels);
    if(frames==0)throw new IOException("Áudio decodificado vazio");
    long outFrames=(frames*16000L)/rate;
    if(outFrames<=0||outFrames>0x7fffffffL/2-36)throw new IOException("Áudio excede o tamanho WAV permitido");
    try(RandomAccessFile in=new RandomAccessFile(pcm,"r");OutputStream out=new BufferedOutputStream(new FileOutputStream(wav))){
      writeWavHeader(out,(int)(outFrames*2));
      long prevIndex=-1;
      float previous=0,next=0;
      for(long i=0;i<outFrames;i++){
        double pos=i*(double)rate/16000.0;
        long idx=Math.min((long)pos,frames-1);
        if(idx!=prevIndex){
          previous=readMono(in,idx,channels);
          next=readMono(in,Math.min(idx+1,frames-1),channels);
          prevIndex=idx;
        }
        double frac=pos-idx;
        int sample=Math.round((float)(previous+(next-previous)*frac));
        write16(out,(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,sample)));
      }
    }
  }
  private static float readMono(RandomAccessFile in,long frame,int channels)throws IOException{
    in.seek(frame*channels*2L);
    int sum=0;
    for(int c=0;c<channels;c++){
      int lo=in.readUnsignedByte(),hi=in.readUnsignedByte();
      sum+=(short)((hi<<8)|lo);
    }
    return sum/(float)channels;
  }
  private static void write16(OutputStream out,short v)throws IOException{out.write(v&255);out.write((v>>>8)&255);}
  private static void write32(OutputStream out,int v)throws IOException{out.write(v&255);out.write((v>>>8)&255);out.write((v>>>16)&255);out.write((v>>>24)&255);}
  private static void writeWavHeader(OutputStream out,int bytes)throws IOException{
    out.write(new byte[]{'R','I','F','F'});write32(out,bytes+36);
    out.write(new byte[]{'W','A','V','E','f','m','t',' '});write32(out,16);
    write16(out,(short)1);write16(out,(short)1);write32(out,16000);
    write32(out,32000);write16(out,(short)2);write16(out,(short)16);
    out.write(new byte[]{'d','a','t','a'});write32(out,bytes);
  }
}
