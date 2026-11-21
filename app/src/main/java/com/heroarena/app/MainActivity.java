package com.heroarena.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.util.Arrays;
import java.util.Locale;

/**
 * Hero Arena: the game is one HTML page in assets/index.html, shown full screen in a WebView.
 * The page is served from https://appassets.androidplatform.net so it counts as a secure page,
 * which lets it use the live camera (with the fighter outline drawn over it).
 * The native camera app and gallery picker stay as a fallback.
 */
public class MainActivity extends Activity {
    private static final int REQUEST_PICTURE = 71;
    private static final int REQUEST_CAMERA_PERMISSION = 72;
    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";

    private WebView web;
    private WebViewAssetLoader assets;
    private ValueCallback<Uri[]> pending;
    private PermissionRequest pendingCamera;
    private Uri cameraUri;
    private long lastBack;
    private volatile boolean russian = "ru".equals(Locale.getDefault().getLanguage());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        assets = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(0xFF0D1C17);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);              // saved fighters and the language live in localStorage
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);             // photos from the camera app and gallery
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);

        web.addJavascriptInterface(new Bridge(), "HeroArenaApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assets.shouldInterceptRequest(request.getUrl());
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return choosePicture(callback, params.isCaptureEnabled());
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> handleCameraRequest(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (request == pendingCamera) {
                    pendingCamera = null;
                }
            }
        });

        setContentView(web);
        web.loadUrl(START_URL);
    }

    /** The page tells the app which language it shows, so the app's own messages match. */
    private class Bridge {
        @JavascriptInterface
        public void setLang(String lang) {
            russian = "ru".equals(lang);
        }
    }

    private String say(String en, String ru) {
        return russian ? ru : en;
    }

    /** The page asked for the live camera: grant it once Android's camera permission is in place. */
    private void handleCameraRequest(PermissionRequest request) {
        boolean wantsVideo = Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        if (!wantsVideo || !"appassets.androidplatform.net".equals(request.getOrigin().getHost())) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE });
            return;
        }
        if (pendingCamera != null) {
            pendingCamera.deny();
        }
        pendingCamera = request;
        requestPermissions(new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != REQUEST_CAMERA_PERMISSION || pendingCamera == null) {
            return;
        }
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            pendingCamera.grant(new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE });
        } else {
            pendingCamera.deny();   // the page then offers the camera app instead
        }
        pendingCamera = null;
    }

    /** Opens the camera app when the page asked for it, otherwise a picker that also offers the camera. */
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
        Intent chooser = Intent.createChooser(picker, say("Choose a picture", "Выбери фото"));
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
                Toast.makeText(this, say("No camera or gallery app was found.", "Не найдено приложение камеры или галереи."), Toast.LENGTH_LONG).show();
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
            discardCameraEntry();   // the camera app was not used, so remove the empty library entry
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
        Toast.makeText(this, say("Press back again to close Hero Arena.", "Нажми «Назад» ещё раз, чтобы закрыть Hero Arena."), Toast.LENGTH_SHORT).show();
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
