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
    try(InputStream stream=getAssets().open("splash_novo.jpg")){
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
  private final java.util.concurrent.atomic.AtomicInteger activeJobs = new java.util.concurrent.atomic.AtomicInteger(0);
  private volatile boolean exitingByChoice = false;
  private void runProtected(String label, Runnable work) {
    activeJobs.incrementAndGet();
    runOnUiThread(() -> {
      try { android.content.Intent intent = new android.content.Intent(this, ProcessingService.class);
        intent.putExtra("label", label);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
      } catch (Exception e) { Log.w("TubTok", "Não foi possível ativar proteção em segundo plano", e); }
    });
    pool.execute(() -> {
      try { work.run(); }
      finally { if (activeJobs.decrementAndGet() == 0) {
        runOnUiThread(() -> { try { stopService(new android.content.Intent(this, ProcessingService.class)); } catch(Exception ignored) {} });
      } }
    });
  }
  private void confirmExit() {
    if (activeJobs.get() <= 0) { finish(); return; }
    new android.app.AlertDialog.Builder(this)
      .setTitle("Processamento em andamento")
      .setMessage("O TubTok está trabalhando. Deseja realmente fechar o aplicativo? O download, a conversão, a transcrição ou a tradução em andamento será interrompido.")
      .setNegativeButton("Continuar trabalhando", (dialog, which) -> dialog.dismiss())
      .setPositiveButton("Fechar e cancelar", (dialog, which) -> {
        exitingByChoice=true;
        pool.shutdownNow();
        stopService(new android.content.Intent(this, ProcessingService.class));
        finishAndRemoveTask();
      }).show();
  }
  @Override public void onBackPressed() { confirmExit(); }

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
        // A versão empacotada pode envelhecer; tenta atualizar sem bloquear a inicialização.
        splashMessage("Verificando atualização do yt-dlp...");
        try {
          YoutubeDL.UpdateStatus update = YoutubeDL.getInstance().updateYoutubeDL(
              getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
          Log.i("TubTok", "Atualização automática yt-dlp: " + update);
        } catch (Exception updateError) {
          Log.w("TubTok", "Atualização yt-dlp indisponível; usando versão instalada", updateError);
        }
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
    @JavascriptInterface public String versionInfo(){try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);return info.versionName+"|"+info.versionCode;}catch(Exception e){return "2.12|32";}}
    @JavascriptInterface public void copy(String text){runOnUiThread(()->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("Transcrição",text));Toast.makeText(MainActivity.this,"Texto copiado",Toast.LENGTH_SHORT).show();});}
    @JavascriptInterface public void updateEngine(){runProtected("Atualizando motor", () -> MainActivity.this.updateEngine(true));}
    @JavascriptInterface public void inspectMedia(String url){runProtected("Consultando mídias", () -> inspectMediaNative(url));}
    @JavascriptInterface public void download(String url,String mode,String lang){runProtected(mode.equals("video") ? "Baixando vídeo" : "Transcrevendo vídeo", () -> downloadVideo(url,mode,lang));}
    @JavascriptInterface public boolean modelReady(){File f=new File(new File(getFilesDir(),"models"),"ggml-base.bin");return f.length()>100_000_000L;}
    @JavascriptInterface public void transcribeLocal(String uri,String lang){runProtected("Transcrevendo áudio", () -> transcribePickedAudio(uri,lang));}
    @JavascriptInterface public void translateTranscript(String text,String language){runProtected("Traduzindo texto", () -> MainActivity.this.translateTranscript(text,language));}
    @JavascriptInterface public void prepareModel(){runProtected("Preparando Whisper", () -> {try{ensureModel();send("model_ready","Whisper pronto.");}catch(Exception e){send("error","Falha ao preparar Whisper: "+e.getMessage());}});}
  }
  // API oficial verificada no código-fonte da biblioteca 0.18.1.
  private void updateEngine(boolean manual){
    if(!initialized){send("warning","Motor ainda inicializando.");return;}
    try{
      send("progress","Atualizando motor yt-dlp pelo canal nightly...");
      YoutubeDL.UpdateStatus result=YoutubeDL.getInstance().updateYoutubeDL(
          getApplicationContext(), YoutubeDL.UpdateChannel._NIGHTLY);
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
  private void inspectMediaNative(String url){
    try{
      org.json.JSONObject result=new org.json.JSONObject();result.put("url",url);
      org.json.JSONArray items=new org.json.JSONArray();
      if(url.matches("(?i)^https?://([^/]+\\.)?threads\\.(com|net)/.*")){
        java.util.List<ThreadsExtractor.Media> found=ThreadsExtractor.resolve(url);
        for(ThreadsExtractor.Media media:found){org.json.JSONObject item=new org.json.JSONObject();item.put("url",media.url);item.put("type",media.extension.equals("mp4")?"video":"image");item.put("thumb",media.extension.equals("mp4")?"":media.url);items.put(item);}
      }else if(url.matches("(?i)^https?://(www\\.)?instagram\\.com/.*")){
        result.put("notice","O Instagram pode bloquear a consulta anônima. Tentando consultar metadados públicos do link.");
        java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36");
        int status=c.getResponseCode();result.put("http",status);
        if(status!=200)throw new java.io.IOException("Instagram HTTP "+status+" "+c.getResponseMessage());
        String html;try(java.io.InputStream in=c.getInputStream();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1 && out.size()<4000000)out.write(data,0,Math.min(n,4000000-out.size()));html=out.toString("UTF-8");}result.put("finalUrl",c.getURL().toString());result.put("htmlLength",html.length());
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("<meta[^>]+(?:property|name)=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        if(m.find()){org.json.JSONObject item=new org.json.JSONObject();item.put("url",url);item.put("type","post");item.put("thumb",m.group(1).replace("&amp;","&"));items.put(item);}
        if(items.length()==0)result.put("notice","HTTP "+status+"; URL final="+c.getURL()+"; HTML="+html.length()+" caracteres. Nenhuma tag og:image reconhecida. O servidor pode ter entregue uma página de login ou conteúdo sem metadados. Não significa perfil vazio.");
        else result.put("notice","Prévia pública encontrada. A listagem completa de perfis e Stories não está disponível sem acesso autorizado.");
      }else throw new java.io.IOException("Plataforma não suportada para prévia.");
      result.put("items",items);send("media_result",result.toString());
    }catch(Exception e){Log.e("TubTok","Consulta de mídias falhou",e);String details=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());send("media_error",details);}
  }
  private void downloadVideo(String url,String mode,String lang){
    if(!initialized){send("error","Motor ainda não foi inicializado.");return;}
    if(!url.matches("(?i)^https?://[^\\s]+")){send("error","Informe um link válido.");return;}
    File dir=new File(getCacheDir(),"videos-"+System.currentTimeMillis());dir.mkdirs();
    try{
      send("progress","Consultando mídia da plataforma...");
      // Links curtos vt.tiktok.com são aceitos diretamente pelo extrator yt-dlp.
      // O redirecionamento não corrige bloqueios de fingerprint/TLS do TikTok.
      if(url.matches("(?i)^https?://(www\\.)?instagram\\.com/[^/?#]+/?([?#].*)?$"))
        throw new Exception("PROFILE_LIST_UNAVAILABLE");
      if(mode.equals("video") && url.matches("(?i)^https?://([^/]+\\.)?threads\\.(com|net)/.*")){
        send("progress","Consultando publicação pública do Threads...");
        java.util.List<ThreadsExtractor.Media> media=ThreadsExtractor.resolve(url);
        int count=0;
        for(ThreadsExtractor.Media item:media){
          File target=new File(dir,"threads_"+(++count)+"."+item.extension);
          ThreadsExtractor.download(item,target);
          String saved=saveToDownloads(target,"TubTok_Threads_"+System.currentTimeMillis()+"_"+count+"."+item.extension);
          send("video_saved",saved);
        }
        send("done",count+" arquivo(s) do Threads salvo(s) em Downloads/TubTok.");
        return;
      }
      if(mode.equals("video")){
        YoutubeDLRequest req=new YoutubeDLRequest(url);
        req.addOption("-f","best[ext=mp4]/best");
        req.addOption("--no-mtime");
        req.addOption("--no-playlist");req.addOption("--no-part");
        req.addOption("-o",new File(dir,"video.%(ext)s").getAbsolutePath());
        YoutubeDL.getInstance().execute(req, "video-"+System.currentTimeMillis(), (progress, eta, line)->{if(progress>=0)send("progress",String.format(java.util.Locale.US,"Baixando vídeo: %.0f%%",progress));return kotlin.Unit.INSTANCE;});
        File video=null;for(File f:Objects.requireNonNull(dir.listFiles()))if(f.isFile()&&(f.getName().endsWith(".mp4")||f.getName().endsWith(".webm")||f.getName().endsWith(".mkv")||f.getName().endsWith(".jpg")||f.getName().endsWith(".jpeg")||f.getName().endsWith(".png")||f.getName().endsWith(".webp"))){video=f;break;}
        if(video==null)throw new Exception("Nenhuma mídia compatível encontrada após download.");
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
      Log.e("TubTok","Falha ao obter mídia; mensagem completa: "+raw,ex);
      String low=raw.toLowerCase(java.util.Locale.ROOT);
      String friendly;
      if(low.contains("profile_list_unavailable")) {
        friendly="A listagem completa de perfis do Instagram não está disponível. Cole o link direto de um Reel, publicação ou Story acessível.";
      } else if(low.contains("login required") || low.contains("login_required") || low.contains("private account")) {
        friendly="Este conteúdo do Instagram exige login ou não está disponível publicamente. Não é possível afirmar que o perfil esteja vazio.";
      } else if((low.contains("impersonat") || low.contains("status code 0")) && url.contains("tiktok.com") ||
         low.contains("unable to extract webpage video data") || low.contains("video not available")) {
        friendly="O TikTok não liberou este vídeo para o motor de download no Android. " +
          "O site pode exigir identificação de navegador não disponível nesta versão. " +
          "Atualize o yt-dlp nas configurações e tente novamente. Se persistir, " +
          "não é um problema do Whisper nem do FFmpeg.";
      } else if(low.contains("403") || low.contains("forbidden")) {
        friendly="A plataforma recusou o acesso ao vídeo (HTTP 403). Atualize o yt-dlp e tente novamente.";
      } else if(low.contains("unsupported url")) {
        friendly=url.contains("threads.com")||url.contains("threads.net") ? "O extrator de Threads ainda não está integrado ao APK. Este link não pode ser baixado nesta versão." : "O link não é reconhecido pelo yt-dlp. Confira o endereço do vídeo.";
      } else if(low.contains("unable to download") || low.contains("timed out") || low.contains("network")) {
        friendly="Não foi possível receber o arquivo. Confira a conexão e tente novamente.";
      } else {
        // Nunca exibir o stderr inteiro: WARNINGS não são diagnóstico final.
        friendly="Não foi possível obter a mídia. Diagnóstico: "+raw.substring(0,Math.min(raw.length(),350));
      }
      send("error",friendly+"\n\nERRO TÉCNICO: "+ex.getClass().getName()+": "+raw);
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
    File wav=AudioConverter.convert(audio,new File(dir,"whisper_16k.wav"),(pct,positionMs,durationMs)->{
      String elapsed=String.format(java.util.Locale.getDefault(),"%d:%02d",positionMs/60000,(positionMs/1000)%60);
      String total=durationMs>0?String.format(java.util.Locale.getDefault(),"%d:%02d",durationMs/60000,(durationMs/1000)%60):"?";
      send("conversion_progress",pct+"|"+elapsed+"|"+total);
    });
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
    android.content.ContentValues v=new android.content.ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,name.matches("(?i).*\\.(jpg|jpeg)")?"image/jpeg":name.matches("(?i).*\\.png")?"image/png":name.matches("(?i).*\\.webp")?"image/webp":name.endsWith(".srt")||name.endsWith(".txt")?"text/plain":"video/mp4");
    if(android.os.Build.VERSION.SDK_INT>=29)v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/TubTok");
    Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new Exception("Não foi possível criar arquivo em Downloads");
    try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(uri)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return name;
  }
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICKER&&pendingFiles!=null){
    Uri chosen=result==RESULT_OK&&data!=null?data.getData():null;
    pendingFiles.onReceiveValue(chosen!=null?new Uri[]{chosen}:null);
    if(chosen!=null)send("picked",chosen.toString());
    pendingFiles=null;}}
  @Override protected void onDestroy(){if(exitingByChoice || activeJobs.get()==0)pool.shutdownNow();super.onDestroy();}
}
