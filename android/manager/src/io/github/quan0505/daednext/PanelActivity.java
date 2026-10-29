package io.github.quan0505.daednext;

import android.app.*;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
import android.view.View;
import android.net.Uri;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.util.concurrent.*;

/** Local advanced panel, isolated from root commands: no JavaScript bridge. */
public final class PanelActivity extends Activity {
    private WebView web;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    static boolean local(Uri uri) {
        return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort()==2023 && uri.getUserInfo()==null;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setFitsSystemWindows(true);
        Button back=new Button(this);back.setText("‹ 返回 DaedNext");back.setAllCaps(false);back.setOnClickListener(v->finish());root.addView(back);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setGeolocationEnabled(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setSupportZoom(true);web.getSettings().setBuiltInZoomControls(true);web.getSettings().setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !local(request.getUrl());}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                if(local(request.getUrl()))return null;
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
        });
        // The bundled panel uses a root workspace, not one URL per section.
        final String route="/";
        worker.execute(()->{
            try {
                String token=new ApiClient(new ModuleClient()).panelSession();
                String html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><script>localStorage.setItem('token',"+JSONObject.quote(token)+");location.replace('http://127.0.0.1:2023/#"+route+"');</script>";
                // Use a distinct path: a hash-only navigation from the root
                // bootstrap document would never fetch the real SPA HTML.
                runOnUiThread(()->{if(!isFinishing())web.loadDataWithBaseURL("http://127.0.0.1:2023/manager-bootstrap",html,"text/html","UTF-8",null);});
            }catch(Exception e){runOnUiThread(()->{if(!isFinishing())web.loadUrl("http://127.0.0.1:2023/#"+route);});}
        });
    }
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){
        worker.shutdownNow();
        web.evaluateJavascript("localStorage.removeItem('token')",null);
        web.stopLoading();web.removeAllViews();web.destroy();super.onDestroy();
    }
}
