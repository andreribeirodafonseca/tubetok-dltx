package com.mediadownloader.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.*;
import android.content.*;
import android.net.Uri;
import android.widget.Toast;
import android.util.Log;
import android.provider.MediaStore;
import java.io.*;
import java.util.concurrent.*;
import java.util.*;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLException;

public class MainActivity extends Activity {
  private WebView web;
  private ValueCallback<Uri[]> pendingFiles;
  private static final int PICKER=44;
  private final ExecutorService pool=Executors.newSingleThreadExecutor();
  private volatile boolean initialized=false;
  private void send(String type,String msg){runOnUiThread(()->web.evaluateJavascript("window.nativeEvent("+org.json.JSONObject.quote(type)+","+org.json.JSONObject.quote(msg)+")",null));}
  @Override public void onCreate(Bundle state){super.onCreate(state);
    web=new WebView(this);setContentView(web);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
    web.addJavascriptInterface(new Bridge(),"AndroidBridge");web.setWebViewClient(new WebViewClient());
    web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params){pendingFiles=callback;Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(Intent.createChooser(i,"Escolher áudio"),PICKER);return true;}});
    web.loadUrl("file:///android_asset/index.html");
    pool.execute(()->{try{YoutubeDL.getInstance().init(getApplicationContext());initialized=true;send("ready","Motor inicializado. Atualizando yt-dlp...");updateEngine(false);}catch(Exception e){send("error","Falha ao iniciar bibliotecas: "+e.getMessage());}});
  }
  public class Bridge {
    @JavascriptInterface public void copy(String text){runOnUiThread(()->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("Transcrição",text));Toast.makeText(MainActivity.this,"Texto copiado",Toast.LENGTH_SHORT).show();});}
    @JavascriptInterface public void updateEngine(){pool.execute(()->MainActivity.this.updateEngine(true));}
    @JavascriptInterface public void download(String url,String mode,String lang){pool.execute(()->downloadVideo(url,mode,lang));}
    @JavascriptInterface public boolean modelReady(){File f=new File(new File(getFilesDir(),"models"),"ggml-base.bin");return f.length()>100_000_000L;}
    @JavascriptInterface public void transcribeLocal(String uri,String lang){pool.execute(()->transcribePickedAudio(uri,lang));}
    @JavascriptInterface public void prepareModel(){pool.execute(()->{try{ensureModel();send("model_ready","Whisper pronto.");}catch(Exception e){send("error","Falha ao preparar Whisper: "+e.getMessage());}});}
  }
  // API oficial verificada no código-fonte da biblioteca 0.18.1.
  private void updateEngine(boolean manual){
    if(!initialized){send("warning","Motor ainda inicializando.");return;}
    try{
      send("progress","Atualizando motor yt-dlp pelo canal estável...");
      YoutubeDL.UpdateStatus result=YoutubeDL.getInstance().updateYoutubeDL(
          getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
      String version=YoutubeDL.getInstance().versionName(getApplicationContext());
      if(result==YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE)
        send("ready","Motor já atualizado. Versão: "+String.valueOf(version));
      else if(result==YoutubeDL.UpdateStatus.DONE)
        send("ready","Motor atualizado com sucesso. Versão: "+String.valueOf(version));
      else send("warning","Atualização sem resultado definido. Versão: "+String.valueOf(version));
    }catch(Exception ex){
      Log.e("MediaDownloader","Falha na atualização",ex);
      String detail=String.valueOf(ex.getMessage());
      if(detail.length()>280)detail=detail.substring(0,280);
      send("warning","Falha real da atualização: "+ex.getClass().getSimpleName()+" — "+detail);
    }
  }
  private void downloadVideo(String url,String mode,String lang){
    if(!initialized){send("error","Motor ainda não foi inicializado.");return;}
    if(!url.matches("(?i)^https?://[^\\s]+")){send("error","Informe um link válido.");return;}
    File dir=new File(getCacheDir(),"videos-"+System.currentTimeMillis());dir.mkdirs();
    try{
      send("progress","Obtendo vídeo do YouTube ou TikTok...");
      if(mode.equals("video")){
        YoutubeDLRequest req=new YoutubeDLRequest(url);
        req.addOption("-f","best[ext=mp4]/best");
        req.addOption("--no-playlist");req.addOption("--no-part");
        req.addOption("-o",new File(dir,"video.%(ext)s").getAbsolutePath());
        YoutubeDL.getInstance().execute(req, "video-"+System.currentTimeMillis(), (progress, eta, line)->{if(progress>=0)send("progress",String.format(java.util.Locale.US,"Baixando vídeo: %.0f%%",progress));return kotlin.Unit.INSTANCE;});
        File video=null;for(File f:Objects.requireNonNull(dir.listFiles()))if(f.isFile()&&(f.getName().endsWith(".mp4")||f.getName().endsWith(".webm")||f.getName().endsWith(".mkv"))){video=f;break;}
        if(video==null)throw new Exception("Nenhum vídeo encontrado após download.");
        String saved=saveToDownloads(video,"MediaDownloader_"+System.currentTimeMillis()+"."+video.getName().substring(video.getName().lastIndexOf('.')+1));
        send("video_saved",saved);
      }else if(mode.equals("transcribe")||mode.equals("video_transcribe")){
        // Transcrição é independente do MP4. Baixa somente o áudio uma vez.
        send("progress","Obtendo áudio para transcrição...");
        YoutubeDLRequest audioReq=new YoutubeDLRequest(url);
        audioReq.addOption("-f","bestaudio/best");
        audioReq.addOption("-x");
        audioReq.addOption("--audio-format","mp3");
        audioReq.addOption("--no-playlist");
        audioReq.addOption("-o",new File(dir,"audio.%(ext)s").getAbsolutePath());
        YoutubeDL.getInstance().execute(audioReq);
        File audio=null;
        for(File f:Objects.requireNonNull(dir.listFiles()))
          if(f.getName().startsWith("audio.") && f.isFile()){audio=f;break;}
        if(audio==null)throw new Exception("Não foi possível extrair áudio. Confira o suporte FFmpeg.");
        runWhisper(audio,lang,dir);
      }else throw new Exception("Ação não reconhecida: "+mode);
      send("done","Processamento concluído.");
    }catch(Exception ex){
      String raw=String.valueOf(ex.getMessage());
      String friendly=raw.contains("Unable to extract webpage video data")||raw.contains("impersonat")
        ?"O TikTok recusou a extracao neste Android. Atualize o motor e tente novamente. Se persistir, a plataforma exige suporte de navegador que pode nao estar disponivel no Android."
        :raw.contains("HTTP Error 403")?"A plataforma recusou o acesso (403). Tente atualizar o motor ou outro link."
        :"Nao foi possivel baixar o video. Verifique o link e a conexao.";
      if(mode.equals("video"))send("error",friendly);
      else send("error","Falha na transcrição: "+(raw.length()>230?raw.substring(0,230):raw));
      Log.e("MediaDownloader","download: "+raw,ex);
    }
    finally{deleteDirectory(dir);}
  }
  private void transcribePickedAudio(String uri,String lang){
    File dir=new File(getCacheDir(),"audio-"+System.currentTimeMillis());dir.mkdirs();
    try{
      send("progress","Preparando arquivo de áudio...");
      Uri input=Uri.parse(uri);
      String mime=getContentResolver().getType(input);
      String ext="mp3";
      if(mime!=null){
        if(mime.contains("ogg"))ext="ogg";
        else if(mime.contains("wav"))ext="wav";
        else if(mime.contains("flac"))ext="flac";
        else if(mime.contains("mp4"))ext="m4a";
      }
      File audio=new File(dir,"local."+ext);
      try(InputStream in=getContentResolver().openInputStream(input);OutputStream out=new FileOutputStream(audio)){
        if(in==null)throw new IOException("Não foi possível abrir arquivo.");
        byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
      }
      runWhisper(audio,lang,dir);
      send("done","Transcrição concluída.");
    }catch(Exception e){Log.e("MediaDownloader","local whisper",e);send("error","Falha na transcrição local: "+e.getMessage());}
    finally{deleteDirectory(dir);}
  }
  private File ensureModel() throws Exception {
    File models=new File(getFilesDir(),"models");models.mkdirs();
    File model=new File(models,"ggml-base.bin");
    if(model.length()>100_000_000L)return model;
    File temp=new File(models,"ggml-base.bin.part");
    java.net.URL url=new java.net.URL("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin");
    java.net.HttpURLConnection conn=(java.net.HttpURLConnection)url.openConnection();
    conn.setConnectTimeout(30000);conn.setReadTimeout(60000);
    conn.setInstanceFollowRedirects(true);
    try{
      if(conn.getResponseCode()!=200)throw new IOException("HTTP "+conn.getResponseCode());
      long total=conn.getContentLengthLong(),done=0,last=-1;
      try(InputStream in=conn.getInputStream();OutputStream out=new FileOutputStream(temp)){
        byte[] b=new byte[65536];int n;
        while((n=in.read(b))!=-1){
          out.write(b,0,n);done+=n;
          long percent=total>0?done*100/total:-1;
          if(percent!=last){last=percent;send("progress",percent>=0?"Baixando modelo Whisper: "+percent+"%":"Baixando modelo Whisper: "+(done/1048576)+" MB");}
        }
      }
      if(temp.length()<100_000_000L)throw new IOException("Modelo baixado incompleto.");
      if(!temp.renameTo(model))throw new IOException("Falha ao instalar modelo.");
      return model;
    }finally{conn.disconnect();}
  }
  private void runWhisper(File audio,String lang,File dir)throws Exception{
    send("progress","Preparando Whisper local (modelo multilíngue base)...");
    File model=ensureModel();
    send("progress","Reconhecendo fala no celular. Isso pode demorar alguns minutos...");
    WhisperRunner.Result result=WhisperRunner.transcribe(this,model.getAbsolutePath(),audio.getAbsolutePath(),lang);
    String txt=result.getText()==null?"":result.getText().trim();
    if(txt.isEmpty())throw new Exception("Whisper não identificou fala neste áudio.");
    StringBuilder srt=new StringBuilder();int i=1;
    for(WhisperRunner.Segment seg:result.getSegments()){
      srt.append(i++).append("\\n").append(srtTime(seg.getStartMs())).append(" --> ").append(srtTime(seg.getEndMs())).append("\\n").append(seg.getText().trim()).append("\\n\\n");
    }
    String base="MediaDownloader_"+System.currentTimeMillis();
    File txtFile=new File(dir,"transcricao.txt"),srtFile=new File(dir,"transcricao.srt");
    try(OutputStream o=new FileOutputStream(txtFile)){o.write(txt.getBytes("UTF-8"));}
    try(OutputStream o=new FileOutputStream(srtFile)){o.write(srt.toString().getBytes("UTF-8"));}
    saveToDownloads(txtFile,base+".txt");saveToDownloads(srtFile,base+".srt");
    send("text",txt);
    send("transcript_saved","TXT e SRT salvos em Downloads/MediaDownloader");
  }
  private String srtTime(long ms){
    long h=ms/3600000,m=(ms/60000)%60,sec=(ms/1000)%60,milli=ms%1000;
    return String.format(java.util.Locale.US,"%02d:%02d:%02d,%03d",h,m,sec,milli);
  }
  private void deleteDirectory(File dir){
    File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();
  }
  private String read(File f)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return out.toString("UTF-8");}
  private String saveToDownloads(File src,String name)throws Exception{
    android.content.ContentValues v=new android.content.ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,name.endsWith(".srt")?"text/plain":name.endsWith(".txt")?"text/plain":"video/mp4");
    if(android.os.Build.VERSION.SDK_INT>=29)v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/MediaDownloader");
    Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new Exception("Não foi possível criar arquivo em Downloads");
    try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(uri)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return name;
  }
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICKER&&pendingFiles!=null){
    Uri chosen=result==RESULT_OK&&data!=null?data.getData():null;
    pendingFiles.onReceiveValue(chosen!=null?new Uri[]{chosen}:null);
    if(chosen!=null)send("picked",chosen.toString());
    pendingFiles=null;}}
  @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
