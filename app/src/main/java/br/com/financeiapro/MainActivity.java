package br.com.financeiapro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.os.Environment;
import android.os.Build;
import android.provider.MediaStore;
import android.database.Cursor;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.FrameLayout;
import android.view.View;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.widget.ProgressBar;
import android.view.Gravity;

public class MainActivity extends Activity {
 private WebView web;
 private ProgressBar refreshIndicator;
 private float touchStartY;
 private boolean beganAtTop;
 private boolean refreshing=false;
 private ValueCallback<Uri[]> fileCallback;
 private static final int PICK_FILE=42;
 private static final String HOME="https://edielrodrigues.github.io/FINANCES/";
 @Override public void onCreate(Bundle state){super.onCreate(state);
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  FrameLayout root=new FrameLayout(this);
  web=new WebView(this);
  root.addView(web,new FrameLayout.LayoutParams(-1,-1));
  refreshIndicator=new ProgressBar(this);
  FrameLayout.LayoutParams progressParams=new FrameLayout.LayoutParams((int)(42*getResources().getDisplayMetrics().density),(int)(42*getResources().getDisplayMetrics().density),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  progressParams.topMargin=(int)(16*getResources().getDisplayMetrics().density);
  root.addView(refreshIndicator,progressParams);
  refreshIndicator.setVisibility(View.GONE);
  setContentView(root);
  final float threshold=140*getResources().getDisplayMetrics().density;
  web.setOnTouchListener((v,event)->{
    switch(event.getActionMasked()){
      case MotionEvent.ACTION_DOWN:
        touchStartY=event.getY();
        beganAtTop=web.getScrollY()<=0;
        break;
      case MotionEvent.ACTION_UP:
        float pulled=event.getY()-touchStartY;
        if(beganAtTop && web.getScrollY()<=0 && pulled>threshold && !refreshing){
          refreshing=true;
          refreshIndicator.setVisibility(View.VISIBLE);
          web.reload();
          return true;
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        beganAtTop=false;
        break;
    }
    return false;
  });
  WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
  settings.setAllowFileAccess(false);settings.setMediaPlaybackRequiresUserGesture(false);
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
  web.addJavascriptInterface(new Object(){
   @JavascriptInterface public void saveReport(String base64, String filename, String mime){
    if(web==null || web.getUrl()==null || !web.getUrl().startsWith(HOME))return;
    if(!("application/pdf".equals(mime)||"text/csv".equals(mime)))return;
    if(base64==null || base64.length()>26000000)return;
    try{
     byte[] bytes=Base64.decode(base64,Base64.DEFAULT);
     if(bytes.length>19000000)return;
     String ext="application/pdf".equals(mime)?".pdf":".csv";
     String clean=(filename==null?"relatorio":filename).replaceAll("[^a-zA-Z0-9._-]","_");
     if(!clean.toLowerCase().endsWith(ext))clean+=ext;
     final String fileName=clean;
     runOnUiThread(()->saveNativeFile(bytes,fileName,mime));
    }catch(Exception e){runOnUiThread(()->Toast.makeText(MainActivity.this,"Não foi possível processar o arquivo",Toast.LENGTH_LONG).show());}
   }
  },"FinanceAndroid");
  web.setDownloadListener((url,userAgent,contentDisposition,mimetype,contentLength)->{
   if(url==null || !url.startsWith("https://"))return;
   try{
    DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));
    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
    request.setTitle("Finance IA Pro - arquivo");
    request.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,"finance-ia-pro-arquivo");
    ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
    Toast.makeText(this,"Download iniciado",Toast.LENGTH_SHORT).show();
   }catch(Exception e){Toast.makeText(this,"Falha ao baixar arquivo",Toast.LENGTH_LONG).show();}
  });
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
    Uri uri=request.getUrl();String host=uri.getHost();
    if(host!=null && (host.equals("edielrodrigues.github.io") || host.equals("accounts.google.com"))){return false;}
    try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}return true;
   }
   @Override public void onPageFinished(WebView v,String url){
    refreshing=false;
    refreshIndicator.setVisibility(View.GONE);
    if(url!=null && url.startsWith(HOME))installReportDownloadHook();
   }
   @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError error){
    if(req.isForMainFrame()){refreshing=false;refreshIndicator.setVisibility(View.GONE);}
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
 private void installReportDownloadHook(){
  // Intercepta somente links blob PDF/CSV gerados pelo proprio site.
  String js="(function(){if(window.__financeAndroidBlobHook)return;window.__financeAndroidBlobHook=true;"+
   "document.addEventListener('click',async function(e){var a=e.target.closest&&e.target.closest('a[download]');"+
   "if(!a||!a.href||!a.href.startsWith('blob:'))return;"+
   "var name=a.getAttribute('download')||'relatorio.pdf';"+
   "if(!/\\.(pdf|csv)$/i.test(name))return;"+
   "e.preventDefault();e.stopImmediatePropagation();"+
   "try{var b=await(await fetch(a.href)).blob();var mime=/\\.csv$/i.test(name)?'text/csv':'application/pdf';"+
   "if(b.size>19000000){alert('Arquivo muito grande para baixar pelo aplicativo.');return;}"+
   "var r=new FileReader();r.onload=function(){var str=String(r.result);window.FinanceAndroid.saveReport(str.split(',')[1],name,mime);};r.readAsDataURL(b);"+
   "}catch(err){alert('Nao foi possivel salvar o relatorio.');}},true);})();";
  web.evaluateJavascript(js,null);
 }
 private void saveNativeFile(byte[] bytes,String filename,String mime){
  try{
   Uri uri=null;
   if(Build.VERSION.SDK_INT>=29){
    ContentValues values=new ContentValues();
    values.put(MediaStore.Downloads.DISPLAY_NAME,filename);
    values.put(MediaStore.Downloads.MIME_TYPE,mime);
    values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Finance IA Pro");
    values.put(MediaStore.Downloads.IS_PENDING,1);
    uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
    if(uri==null)throw new Exception("Falha ao criar arquivo");
    try(OutputStream stream=getContentResolver().openOutputStream(uri)){if(stream==null)throw new Exception("Falha ao abrir arquivo");stream.write(bytes);}
    values.clear();values.put(MediaStore.Downloads.IS_PENDING,0);
    getContentResolver().update(uri,values,null,null);
    final Uri saved=uri;
    new AlertDialog.Builder(this).setTitle("Relatório salvo")
     .setMessage("Arquivo salvo em Downloads/Finance IA Pro/"+filename)
     .setNegativeButton("Fechar",(d,w)->d.dismiss())
     .setPositiveButton("Compartilhar",(d,w)->{
      Intent share=new Intent(Intent.ACTION_SEND);share.setType(mime);
      share.putExtra(Intent.EXTRA_STREAM,saved);
      share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(share,"Compartilhar relatório"));
     }).show();
   }else{
    File dir=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
    if(dir==null)throw new Exception("Armazenamento indisponível");
    File output=new File(dir,filename);
    try(FileOutputStream out=new FileOutputStream(output)){out.write(bytes);}
    new AlertDialog.Builder(this).setTitle("Relatório salvo")
     .setMessage("Arquivo salvo no armazenamento do aplicativo: "+filename)
     .setPositiveButton("OK",(d,w)->d.dismiss()).show();
   }
  }catch(Exception ex){Toast.makeText(this,"Não foi possível salvar: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
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
