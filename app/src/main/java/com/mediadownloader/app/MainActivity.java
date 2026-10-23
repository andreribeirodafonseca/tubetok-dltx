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
import com.yausername.youtubedl_android.FFmpeg;

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
    pool.execute(()->{try{YoutubeDL.getInstance().init(getApplicationContext());FFmpeg.getInstance().init(getApplicationContext());initialized=true;send("ready","Motor de download inicializado no celular.");}catch(Exception e){send("error","Falha ao iniciar bibliotecas: "+e.getMessage());}});
  }
  public class Bridge {
    @JavascriptInterface public void copy(String text){runOnUiThread(()->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("Transcrição",text));Toast.makeText(MainActivity.this,"Texto copiado",Toast.LENGTH_SHORT).show();});}
    @JavascriptInterface public void download(String url,String mode,String lang){pool.execute(()->downloadVideo(url,mode,lang));}
  }
  private void downloadVideo(String url,String mode,String lang){
    if(!initialized){send("error","Motor ainda não foi inicializado.");return;}
    if(!url.matches("(?i)^https?://[^\\s]+")){send("error","Informe um link válido.");return;}
    File dir=new File(getCacheDir(),"videos-"+System.currentTimeMillis());dir.mkdirs();
    try{
      send("progress","Obtendo vídeo do YouTube ou TikTok...");
      YoutubeDLRequest req=new YoutubeDLRequest(url);
      req.addOption("-f","bv*+ba/b");req.addOption("--merge-output-format","mp4");
      req.addOption("--no-playlist");req.addOption("--no-part");
      req.addOption("-o",new File(dir,"video.%(ext)s").getAbsolutePath());
      YoutubeDL.getInstance().execute(req, (progress, eta)->{if(progress>=0)send("progress",String.format(java.util.Locale.US,"Baixando vídeo: %.0f%%",progress));},"video-"+System.currentTimeMillis());
      File video=null;for(File f:Objects.requireNonNull(dir.listFiles()))if(f.isFile()&&(f.getName().endsWith(".mp4")||f.getName().endsWith(".webm")||f.getName().endsWith(".mkv"))){video=f;break;}
      if(video==null)throw new Exception("Nenhum vídeo encontrado após download.");
      String saved=saveToDownloads(video,"MediaDownloader_"+System.currentTimeMillis()+"."+video.getName().substring(video.getName().lastIndexOf('.')+1));
      send("saved",saved);
      if(mode.equals("video_transcribe")){
        send("progress","Vídeo salvo. Tentando obter legendas da plataforma...");
        try{
          YoutubeDLRequest sub=new YoutubeDLRequest(url);sub.addOption("--skip-download");sub.addOption("--write-subs");sub.addOption("--write-auto-subs");sub.addOption("--sub-langs",lang.equals("all")?"pt.*,en.*,es.*":lang+".*");sub.addOption("--convert-subs","srt");sub.addOption("-o",new File(dir,"legenda.%(ext)s").getAbsolutePath());YoutubeDL.getInstance().execute(sub);
          File srt=null;for(File f:Objects.requireNonNull(dir.listFiles()))if(f.getName().endsWith(".srt")){srt=f;break;}
          if(srt!=null){String content=read(srt);String txt=content.replaceAll("(?m)^\\d+\\s*$","").replaceAll("(?m)^\\d\\d:\\d\\d:\\d\\d[,.]\\d+\\s+-->.*$","").replaceAll("(?m)^\\s*$","").trim();File t=new File(dir,"transcricao.txt");try(FileOutputStream os=new FileOutputStream(t)){os.write(txt.getBytes("UTF-8"));}saveToDownloads(srt,"MediaDownloader_"+System.currentTimeMillis()+".srt");saveToDownloads(t,"MediaDownloader_"+System.currentTimeMillis()+".txt");send("text",txt);}
          else send("warning","Vídeo baixado. Não há legendas disponíveis; transcrição por Whisper ainda não integrada.");
        }catch(Exception ex){send("warning","Vídeo baixado. Legendas indisponíveis: "+ex.getMessage());}
      }
      send("done","Processamento concluído.");
    }catch(Exception ex){send("error","Falha no download: "+ex.getMessage());Log.e("MediaDownloader","download",ex);}
    finally{File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();}
  }
  private String read(File f)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return out.toString("UTF-8");}
  private String saveToDownloads(File src,String name)throws Exception{
    android.content.ContentValues v=new android.content.ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,name.endsWith(".srt")?"text/plain":name.endsWith(".txt")?"text/plain":"video/mp4");
    if(android.os.Build.VERSION.SDK_INT>=29)v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/MediaDownloader");
    Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new Exception("Não foi possível criar arquivo em Downloads");
    try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(uri)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return name;
  }
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICKER&&pendingFiles!=null){pendingFiles.onReceiveValue(result==RESULT_OK&&data!=null?new Uri[]{data.getData()}:null);pendingFiles=null;}}
  @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
