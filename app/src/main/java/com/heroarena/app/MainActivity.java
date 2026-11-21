package com.heroarena.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * Hero Arena: the game is one HTML page in assets/index.html, shown full screen in a WebView.
 * The only native work is the picture chooser: "Open camera" starts the camera app,
 * "Choose a photo" opens the gallery picker.
 */
public class MainActivity extends Activity {
    private static final int REQUEST_PICTURE = 71;

    private WebView web;
    private ValueCallback<Uri[]> pending;
    private Uri cameraUri;
    private long lastBack;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0D1C17);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);              // saved fighters live in localStorage
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return choosePicture(callback, params.isCaptureEnabled());
            }
        });

        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    /** Opens the camera when the page asked for it, otherwise a picker that also offers the camera. */
    private boolean choosePicture(ValueCallback<Uri[]> callback, boolean wantsCamera) {
        if (pending != null) {
            pending.onReceiveValue(null);
        }
        pending = callback;

        Intent camera = null;
        cameraUri = null;
        try {
            // The camera app writes the photo into the phone's picture library; no storage permission is
            // needed on Android 10 and later. On older versions this throws and only the picker is offered.
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "hero-arena-" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (cameraUri != null) {
                camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (Exception e) {
            cameraUri = null;
            camera = null;
        }

        Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        Intent chooser = Intent.createChooser(picker, "Choose a picture");
        if (camera != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[] { camera });
        }

        try {
            startActivityForResult(wantsCamera && camera != null ? camera : chooser, REQUEST_PICTURE);
            return true;
        } catch (Exception first) {
            try {
                startActivityForResult(chooser, REQUEST_PICTURE);
                return true;
            } catch (Exception second) {
                discardCameraEntry();
                pending.onReceiveValue(null);
                pending = null;
                Toast.makeText(this, "No camera or gallery app was found.", Toast.LENGTH_LONG).show();
                return true;
            }
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_PICTURE || pending == null) {
            return;
        }
        Uri[] picked = null;
        if (result == RESULT_OK) {
            if (data != null && data.getData() != null) {
                picked = new Uri[] { data.getData() };
            } else if (cameraUri != null) {
                picked = new Uri[] { cameraUri };
            }
        }
        if (picked == null || !picked[0].equals(cameraUri)) {
            discardCameraEntry();   // the camera was not used, so remove the empty library entry
        }
        pending.onReceiveValue(picked);
        pending = null;
        cameraUri = null;
    }

    private void discardCameraEntry() {
        if (cameraUri == null) {
            return;
        }
        try {
            getContentResolver().delete(cameraUri, null, null);
        } catch (Exception ignored) {
            // nothing to clean up
        }
        cameraUri = null;
    }

    @Override
    public void onBackPressed() {
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) {
            super.onBackPressed();
            return;
        }
        lastBack = now;
        Toast.makeText(this, "Press back again to close Hero Arena.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
