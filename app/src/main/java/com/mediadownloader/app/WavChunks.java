package com.mediadownloader.app;

import java.io.*;
/** Recorta WAV PCM16 mono 16 kHz sem carregar o áudio inteiro em memória. */
public final class WavChunks {
  private WavChunks(){}
  public static void extract(File source,File target,long start,long end)throws IOException{
    long length=end-start;
    if(start<0 || length<=0 || (start&1)!=0 || (length&1)!=0 || length>Integer.MAX_VALUE-44L)
      throw new IOException("Intervalo PCM inválido.");
    try(RandomAccessFile input=new RandomAccessFile(source,"r");
        DataOutputStream output=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(target)))){
      if(input.length()<44+end)throw new EOFException("WAV menor que o trecho solicitado.");
      output.writeBytes("RIFF");le32(output,(int)(36+length));output.writeBytes("WAVE");
      output.writeBytes("fmt ");le32(output,16);le16(output,1);le16(output,1);
      le32(output,16000);le32(output,32000);le16(output,2);le16(output,16);
      output.writeBytes("data");le32(output,(int)length);
      input.seek(44+start);
      byte[] buf=new byte[65536];long remaining=length;
      while(remaining>0){int n=input.read(buf,0,(int)Math.min(buf.length,remaining));
        if(n<0)throw new EOFException("PCM incompleto.");output.write(buf,0,n);remaining-=n;}
    }
  }
  private static void le16(DataOutputStream out,int n)throws IOException{out.writeByte(n);out.writeByte(n>>>8);}
  private static void le32(DataOutputStream out,int n)throws IOException{le16(out,n);le16(out,n>>>16);}
}
