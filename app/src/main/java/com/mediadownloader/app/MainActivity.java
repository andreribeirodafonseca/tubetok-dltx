package com.mediadownloader.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

public class MainActivity extends Activity {
  private ValueCallback<Uri[]> pendingFiles;
  private static final int FILE_PICKER=44;
  @Override public void onCreate(Bundle state){super.onCreate(state);
    WebView web=new WebView(this);setContentView(web);
    web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
    web.addJavascriptInterface(new Object(){
      @JavascriptInterface public void copy(String text){runOnUiThread(()->{ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("Transcrição",text));Toast.makeText(MainActivity.this,"Texto copiado",Toast.LENGTH_SHORT).show();});}
    },"AndroidBridge");
    web.setWebViewClient(new WebViewClient());
    web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){pendingFiles=callback;Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(Intent.createChooser(i,"Escolher áudio"),FILE_PICKER);return true;}});
    web.loadUrl("file:///android_asset/index.html");
  }
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==FILE_PICKER&&pendingFiles!=null){pendingFiles.onReceiveValue(result==RESULT_OK&&data!=null?new Uri[]{data.getData()}:null);pendingFiles=null;}}
}
