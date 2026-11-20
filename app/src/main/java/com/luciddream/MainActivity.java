package com.luciddream;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import core.Game;
import image.Features;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class MainActivity extends Activity implements Game.Host {
    private static final int REQ_PICK = 11;
    private Game game;
    private GameView view;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new Game(this);
        view = new GameView(this, game);
        setContentView(view);
        hideSystemUi();
        File last = new File(getFilesDir(), "last.img");     // the picture from last time
        if (last.isFile()) loadAsync(last, false);
    }

    @Override public File dataDir() { return getFilesDir(); }

    @Override public void quit() { runOnUiThread(this::finish); }

    @Override public void pickImage() {
        runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            try {
                startActivityForResult(Intent.createChooser(i, "Choose an image"), REQ_PICK);
            } catch (Exception e) {
                game.setError("No app found to pick an image.");
            }
        });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        importUri(data.getData());
    }

    /** Copies the picked picture into the app (so it can be reopened next time) and reads it. */
    private void importUri(Uri uri) {
        game.setLoading(true);
        game.setError("");
        new Thread(() -> {
            File tmp = new File(getCacheDir(), "picked.tmp");
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) throw new java.io.IOException("cannot open");
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            } catch (Exception e) {
                fail("Could not open that file.");
                return;
            }
            loadAsync(tmp, true);
        }, "import").start();
    }

    private void loadAsync(File f, boolean keep) {
        game.setLoading(true);
        new Thread(() -> {
            try {
                Features ft = Features.load(new AndroidImage(f), f.length());
                if (keep) {
                    File last = new File(getFilesDir(), "last.img");
                    //noinspection ResultOfMethodCallIgnored
                    last.delete();
                    if (!f.renameTo(last)) {                       // different storage volumes: copy instead
                        try (InputStream in = new java.io.FileInputStream(f); OutputStream out = new FileOutputStream(last)) {
                            byte[] buf = new byte[1 << 16];
                            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                        }
                    }
                }
                game.post(() -> game.setFeatures(ft));
            } catch (Throwable e) {
                fail("Could not read that image. Try a PNG or JPG.");
            }
        }, "load").start();
    }

    private void fail(String msg) {
        game.post(() -> { game.setLoading(false); game.setError(msg); });
    }

    @Override public void onBackPressed() { game.postAction(Game.ACT_MENU); }

    @Override protected void onResume() {
        super.onResume();
        view.onResume();
        game.onAppResume();
        hideSystemUi();
    }

    @Override protected void onPause() {
        game.onAppPause();
        view.onPause();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideSystemUi();
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
