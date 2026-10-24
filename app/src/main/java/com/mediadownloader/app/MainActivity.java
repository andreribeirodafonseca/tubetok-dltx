package com.mediadownloader.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.*;
import android.content.*;
import android.net.Uri;
import android.widget.Toast;
import android.util.Log;
import android.provider.MediaStore;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.*;
import java.util.concurrent.*;
import java.util.*;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLException;

public class MainActivity extends Activity {
  private WebView web;
  private FrameLayout screen;
  private View splash;
  private TextView splashStatus;
  private boolean webLoaded=false;
  private boolean initFinished=false;
  private long splashStart;
  private final Handler uiHandler=new Handler(Looper.getMainLooper());
  private void splashMessage(String message){runOnUiThread(()->{if(splashStatus!=null)splashStatus.setText(message);});}
  private void finishSplashIfReady(){
    if(!webLoaded||!initFinished||splash==null)return;
    long remaining=Math.max(0,950-(android.os.SystemClock.uptimeMillis()-splashStart));
    uiHandler.postDelayed(()->{if(splash!=null){screen.removeView(splash);splash=null;}},remaining);
  }
  private GradientDrawable splashBackground(){
    GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff08111f,0xff17233b});
    return d;
  }
  private View makeSplash(){
    FrameLayout frame=new FrameLayout(this);
    frame.setBackgroundColor(0xff050d1b);
    ImageView art=new ImageView(this);
    try(InputStream stream=getAssets().open("splash_cinema.jpg")){
      art.setImageBitmap(BitmapFactory.decodeStream(stream));
    }catch(Exception e){art.setImageResource(R.mipmap.ic_launcher);}
    art.setScaleType(ImageView.ScaleType.CENTER_CROP);
    frame.addView(art,new FrameLayout.LayoutParams(-1,-1));
    // Real initialization indicator; never a fake percentage baked into the image.
    LinearLayout progressArea=new LinearLayout(this);
    progressArea.setOrientation(LinearLayout.VERTICAL);
    progressArea.setGravity(Gravity.CENTER);
    progressArea.setPadding(dp(28),dp(12),dp(28),dp(12));
    ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    progress.setIndeterminate(true);
    progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xff00d6f8));
    progressArea.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
    splashStatus=new TextView(this);
    splashStatus.setText("Inicializando TubTok...");
    splashStatus.setTextColor(Color.WHITE);
    splashStatus.setTextSize(14);
    splashStatus.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams statusLp=new LinearLayout.LayoutParams(-1,-2);
    statusLp.topMargin=dp(14);
    progressArea.addView(splashStatus,statusLp);
    FrameLayout.LayoutParams areaLp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
    areaLp.bottomMargin=dp(42);
    frame.addView(progressArea,areaLp);
    return frame;
  }
  private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
  private ValueCallback<Uri[]> pendingFiles;
  private static final int PICKER=44;
  private final ExecutorService pool=Executors.newSingleThreadExecutor();
  private volatile boolean initialized=false;
  private void send(String type,String msg){runOnUiThread(()->web.evaluateJavascript("window.nativeEvent("+org.json.JSONObject.quote(type)+","+org.json.JSONObject.quote(msg)+")",null));}
  @Override public void onCreate(Bundle state){super.onCreate(state);
    splashStart=android.os.SystemClock.uptimeMillis();
    screen=new FrameLayout(this);
    web=new WebView(this);
    screen.addView(web,new FrameLayout.LayoutParams(-1,-1));
    splash=makeSplash();screen.addView(splash,new FrameLayout.LayoutParams(-1,-1));
    setContentView(screen);
    web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
    web.addJavascriptInterface(new Bridge(),"AndroidBridge");web.setWebViewClient(new WebViewClient(){
      @Override public void onPageFinished(WebView view,String url){webLoaded=true;finishSplashIfReady();}
    });
    web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params){pendingFiles=callback;Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(Intent.createChooser(i,"Escolher áudio"),PICKER);return true;}});
    web.loadUrl("file:///android_asset/index.html");
    pool.execute(()->{
      try{
        splashMessage("Verificando motor de downloads...");
        YoutubeDL.getInstance().init(getApplicationContext());
        initialized=true;
        splashMessage("Verificando modelo de transcrição...");
        File model=new File(new File(getFilesDir(),"models"),"ggml-base.bin");
        boolean modelPresent=model.isFile()&&model.length()>100_000_000L;
        splashMessage(modelPresent?"Whisper disponível neste celular":"Whisper será preparado na primeira transcrição");
        send("ready",modelPresent?"Motor pronto. Whisper disponível.":"Motor pronto. O Whisper será baixado somente quando necessário.");
      }catch(Exception e){
        Log.e("TubTok","Falha na inicialização",e);
        splashMessage("Falha ao iniciar. Verifique a mensagem no aplicativo.");
        send("error","Falha ao iniciar motor de downloads: "+e.getMessage());
      }finally{
        runOnUiThread(()->{initFinished=true;finishSplashIfReady();});
      }
    });
  }
  public class Bridge {
    @JavascriptInterface public void copy(String text){runOnUiThread(()->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("Transcrição",text));Toast.makeText(MainActivity.this,"Texto copiado",Toast.LENGTH_SHORT).show();});}
    @JavascriptInterface public void updateEngine(){pool.execute(()->MainActivity.this.updateEngine(true));}
    @JavascriptInterface public void download(String url,String mode,String lang){pool.execute(()->downloadVideo(url,mode,lang));}
    @JavascriptInterface public boolean modelReady(){File f=new File(new File(getFilesDir(),"models"),"ggml-base.bin");return f.length()>100_000_000L;}
    @JavascriptInterface public void transcribeLocal(String uri,String lang){pool.execute(()->transcribePickedAudio(uri,lang));}
    @JavascriptInterface public void translateTranscript(String text,String language){pool.execute(()->MainActivity.this.translateTranscript(text,language));}
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
      Log.e("TubTok","Falha na atualização",ex);
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
        String saved=saveToDownloads(video,"TubTok_"+System.currentTimeMillis()+"."+video.getName().substring(video.getName().lastIndexOf('.')+1));
        send("video_saved",saved);
      }else if(mode.equals("transcribe")||mode.equals("video_transcribe")){
        // Transcrição é independente do MP4. Baixa somente o áudio uma vez.
        send("progress","Obtendo áudio para transcrição...");
        YoutubeDLRequest audioReq=new YoutubeDLRequest(url);
        // Evita o pós-processador --extract-audio, que exige executáveis
        // ffmpeg/ffprobe que não estão disponíveis no PATH do Android.
        // Preferir áudio AAC/M4A já disponibilizado pela plataforma.
        audioReq.addOption("-f","bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio[ext=mp3]/bestaudio");
        audioReq.addOption("--no-playlist");
        audioReq.addOption("-o",new File(dir,"audio.%(ext)s").getAbsolutePath());
        YoutubeDL.getInstance().execute(audioReq);
        File audio=null;
        for(File f:Objects.requireNonNull(dir.listFiles()))
          if(f.getName().startsWith("audio.") && f.isFile()){audio=f;break;}
        if(audio==null)throw new Exception("O áudio não foi localizado após o download.");
        send("progress","Áudio recebido ("+audio.getName()+"). Preparando reconhecimento...");
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
      else send("error","Falha na transcrição: "+(raw.length()>350?raw.substring(0,350):raw));
      Log.e("TubTok","download: "+raw,ex);
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
    }catch(Exception e){Log.e("TubTok","local whisper",e);send("error","Falha na transcrição local: "+e.getMessage());}
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
  // Processa blocos completos em WAV PCM 16 kHz mono, com pequena sobreposicao
  // para preservar palavras nas bordas. Porcentagem so muda ao concluir bloco.
  private void runWhisper(File audio,String lang,File dir)throws Exception{
    send("stage","Preparando modelo Whisper");
    File model=ensureModel();
    send("stage","Convertendo áudio para WAV 16 kHz");
    File wav=AudioConverter.convert(audio,new File(dir,"whisper_16k.wav"));
    long dataBytes=wav.length()-44;
    if(dataBytes<=0)throw new IOException("WAV vazio após conversão.");
    final long bytesPerSecond=32000L; // PCM16, mono, 16000 Hz
    final long stepBytes=45L*bytesPerSecond;
    final long overlapBytes=1000L*32L;
    int total=(int)((dataBytes+stepBytes-1)/stepBytes);
    if(total<=0||total>100000)throw new IOException("Duração de áudio inválida.");
    send("stage","Reconhecendo fala por blocos");
    send("chunk_progress","0|"+total+"|0|0");
    long begin=System.currentTimeMillis();
    StringBuilder allText=new StringBuilder();
    StringBuilder srt=new StringBuilder();
    int subtitleIndex=1;
    for(int block=0;block<total;block++){
      if(Thread.currentThread().isInterrupted())throw new InterruptedException("Transcrição interrompida.");
      long coreStart=block*stepBytes;
      long coreEnd=Math.min(dataBytes,coreStart+stepBytes);
      long chunkStart=Math.max(0,coreStart-overlapBytes);
      long chunkEnd=Math.min(dataBytes,coreEnd+overlapBytes);
      File piece=new File(dir,"parte_"+block+".wav");
      WavChunks.extract(wav,piece,chunkStart,chunkEnd);
      WhisperRunner.Result result=null;
      Exception failure=null;
      try{
        for(int attempt=1;attempt<=2;attempt++){
          try{
            send("block_start",(block+1)+"|"+total+"|"+attempt);
            result=WhisperRunner.transcribe(this,model.getAbsolutePath(),piece.getAbsolutePath(),lang);
            failure=null;break;
          }catch(Exception ex){
            failure=ex;
            Log.w("TubTok","Bloco "+(block+1)+" tentativa "+attempt,ex);
            if(attempt<2)send("stage","Tentando novamente bloco "+(block+1)+" de "+total);
          }
        }
        if(failure!=null)throw new IOException("Falha no bloco "+(block+1)+" de "+total+": "+failure.getMessage(),failure);
        long offsetMs=chunkStart/32L;
        long coreStartMs=coreStart/32L;
        long coreEndMs=coreEnd/32L;
        // Seleciona segmentos pelo ponto medio para nao duplicar sobreposicoes.
        for(WhisperRunner.Segment seg:result.getSegments()){
          long a=offsetMs+seg.getStartMs(),b=offsetMs+seg.getEndMs();
          long mid=a+(b-a)/2;
          if(mid<coreStartMs || (mid>=coreEndMs && block<total-1))continue;
          String t=seg.getText()==null?"":seg.getText().trim();
          if(t.isEmpty())continue;
          if(allText.length()>0)allText.append(' ');
          allText.append(t);
          long clippedStart=Math.max(coreStartMs,a),clippedEnd=Math.min(coreEndMs,b);
          if(clippedEnd<=clippedStart){clippedStart=Math.max(0,a);clippedEnd=Math.max(clippedStart+1,b);}
          srt.append(subtitleIndex++).append("\n").append(srtTime(clippedStart))
            .append(" --> ").append(srtTime(clippedEnd)).append("\n")
            .append(t).append("\n\n");
        }
        int pct=(int)((block+1)*100L/total);
        long seconds=(System.currentTimeMillis()-begin)/1000L;
        send("chunk_progress",(block+1)+"|"+total+"|"+pct+"|"+seconds);
        // Texto parcial nao deve se perder visualmente se o proximo bloco falhar.
        send("partial_text",allText.toString());
      }finally{piece.delete();}
    }
    String txt=allText.toString().trim();
    if(txt.isEmpty())throw new IOException("Whisper não identificou fala neste áudio.");
    // O texto permanece na tela; nenhum arquivo TXT ou SRT é gerado.
    send("text",txt);
  }
  private void translateTranscript(String original,String selectedLanguage){
    if(original==null||original.trim().isEmpty()){send("translation_error","Não há texto para traduzir.");return;}
    com.google.mlkit.nl.translate.Translator translator=null;
    try{
      send("translation_status","Identificando idioma...");
      String source=selectedLanguage;
      if(!"pt".equals(source)&&!"en".equals(source)){
        com.google.mlkit.nl.languageid.LanguageIdentifier identifier=com.google.mlkit.nl.languageid.LanguageIdentification.getClient();
        try{source=com.google.android.gms.tasks.Tasks.await(identifier.identifyLanguage(original));}
        finally{identifier.close();}
      }
      if(!"pt".equals(source)&&!"en".equals(source)){
        send("translation_error","Tradução disponível somente entre inglês e português. Selecione o idioma original.");return;
      }
      String target="pt".equals(source)?"en":"pt";
      send("translation_status","Preparando tradução "+("pt".equals(source)?"português → inglês":"inglês → português")+"...");
      com.google.mlkit.nl.translate.TranslatorOptions options=new com.google.mlkit.nl.translate.TranslatorOptions.Builder()
        .setSourceLanguage(source).setTargetLanguage(target).build();
      translator=com.google.mlkit.nl.translate.Translation.getClient(options);
      send("translation_status","Verificando modelo de tradução (download somente se necessário)...");
      com.google.android.gms.tasks.Tasks.await(translator.downloadModelIfNeeded());
      StringBuilder output=new StringBuilder();
      // Evita enviar textos longos demais ao tradutor de uma vez; mantém a ordem.
      String remaining=original.trim();int index=0;
      while(!remaining.isEmpty()){
        int cut=Math.min(1200,remaining.length());
        if(cut<remaining.length()){
          int boundary=Math.max(remaining.lastIndexOf('\n',cut),Math.max(remaining.lastIndexOf('.' ,cut),remaining.lastIndexOf(' ',cut)));
          if(boundary>cut/2)cut=boundary+1;
        }
        String chunk=remaining.substring(0,cut).trim();remaining=remaining.substring(cut).trim();
        if(chunk.isEmpty())continue;
        send("translation_status","Traduzindo trecho "+(++index)+"...");
        if(output.length()>0)output.append("\n");
        output.append(com.google.android.gms.tasks.Tasks.await(translator.translate(chunk)));
      }
      send("translation_done",output.toString());
    }catch(Exception e){Log.e("TubTok","translation",e);send("translation_error","Falha ao traduzir: "+e.getMessage());}
    finally{if(translator!=null)translator.close();}
  }
  private String srtTime(long ms){
    long h=ms/3600000,m=(ms/60000)%60,sec=(ms/1000)%60,milli=ms%1000;
    return String.format(java.util.Locale.US,"%02d:%02d:%02d,%03d",h,m,sec,milli);
  }
  private void deleteDirectory(File dir){
    File[] files=dir.listFiles();if(files!=null)for(File f:files){if(f.isDirectory())deleteDirectory(f);else f.delete();}dir.delete();
  }
  private String read(File f)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return out.toString("UTF-8");}
  private String saveToDownloads(File src,String name)throws Exception{
    android.content.ContentValues v=new android.content.ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,name.endsWith(".srt")?"text/plain":name.endsWith(".txt")?"text/plain":"video/mp4");
    if(android.os.Build.VERSION.SDK_INT>=29)v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/TubTok");
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
