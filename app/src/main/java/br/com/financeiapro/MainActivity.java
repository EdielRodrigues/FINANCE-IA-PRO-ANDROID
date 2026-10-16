package br.com.financeiapro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.FrameLayout;
import android.view.View;
import android.view.WindowManager;

public class MainActivity extends Activity {
 private WebView web;
 private ValueCallback<Uri[]> fileCallback;
 private static final int PICK_FILE=42;
 private static final String HOME="https://edielrodrigues.github.io/FINANCES/";
 @Override public void onCreate(Bundle state){super.onCreate(state);
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  web=new WebView(this);setContentView(web);
  WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
  settings.setAllowFileAccess(false);settings.setMediaPlaybackRequiresUserGesture(false);
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
    Uri uri=request.getUrl();String host=uri.getHost();
    if(host!=null && (host.equals("edielrodrigues.github.io") || host.equals("accounts.google.com"))){return false;}
    try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}return true;
   }
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams params){
    if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;
    try{startActivityForResult(params.createIntent(),PICK_FILE);}catch(Exception ex){fileCallback=null;cb.onReceiveValue(null);return false;}return true;
   }
   @Override public void onPermissionRequest(PermissionRequest request){
    runOnUiThread(()->{if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.CAMERA)!=getPackageManager().PERMISSION_GRANTED){request.deny();requestPermissions(new String[]{Manifest.permission.CAMERA},55);}else{request.grant(request.getResources());}});
   }
  });
  if(state!=null)web.restoreState(state);else web.loadUrl(HOME);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICK_FILE&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}}
 @Override protected void onSaveInstanceState(Bundle state){web.saveState(state);super.onSaveInstanceState(state);}
 @Override public void onBackPressed(){
  // Primeiro fecha qualquer janela interna usando o controle do próprio site.
  web.evaluateJavascript("(function(){var m=document.getElementById('modal');if(m&&!m.classList.contains('hidden')){var x=document.getElementById('closeModal');if(x){x.click();return 'modal'}}return 'root'})()", result->{
   if("\"modal\"".equals(result))return;
   if(web.canGoBack()&&!web.getUrl().equals(HOME)){web.goBack();return;}
   new AlertDialog.Builder(this).setTitle("Fechar aplicativo?").setMessage("Tem certeza que deseja fechar o aplicativo?")
    .setNegativeButton("Cancelar",(d,w)->d.dismiss()).setPositiveButton("Fechar aplicativo",(d,w)->finish()).show();
  });
 }
 @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
}
