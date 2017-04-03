package net.itempire.carthing;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class AboutActivity extends Activity {

    WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        android.app.ActionBar actionBar = getActionBar();
        if(actionBar!=null)
            actionBar.hide();
        webView = (WebView) findViewById(R.id.webAbout);
        webView.loadUrl("http://www.carsthing.com/#updates");
    }
}
