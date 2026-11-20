package com.luciddream;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import core.Game;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

/** OpenGL ES 2.0 surface: forwards frames to the game and turns touches into game events. */
public final class GameView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private final Game game;

    public GameView(Context ctx, Game game) {
        super(ctx);
        this.game = game;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(new Chooser());
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) { game.surfaceCreated(); }
    @Override public void onSurfaceChanged(GL10 gl, int w, int h) { game.surfaceChanged(w, h); }
    @Override public void onDrawFrame(GL10 gl) { game.drawFrame(); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                game.postTouch(0, e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) game.postTouch(1, e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                game.postTouch(2, e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < e.getPointerCount(); i++) game.postTouch(2, e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            default:
                break;
        }
        return true;
    }

    /** Wants a 24-bit depth buffer (a 16-bit one makes distant ground flicker); settles for 16 bits on phones that have no better. */
    private static final class Chooser implements EGLConfigChooser {
        @Override public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[][] wanted = {{8, 8, 8, 24}, {8, 8, 8, 16}, {5, 6, 5, 16}};
            for (int[] w : wanted) {
                int[] spec = {
                    EGL10.EGL_RED_SIZE, w[0], EGL10.EGL_GREEN_SIZE, w[1], EGL10.EGL_BLUE_SIZE, w[2],
                    EGL10.EGL_DEPTH_SIZE, w[3], EGL10.EGL_RENDERABLE_TYPE, 4 /* EGL_OPENGL_ES2_BIT */, EGL10.EGL_NONE};
                int[] n = new int[1];
                if (!egl.eglChooseConfig(display, spec, null, 0, n) || n[0] <= 0) continue;
                EGLConfig[] cfgs = new EGLConfig[n[0]];
                if (egl.eglChooseConfig(display, spec, cfgs, n[0], n) && cfgs[0] != null) return cfgs[0];
            }
            throw new IllegalArgumentException("No OpenGL ES 2.0 configuration found");
        }
    }
}
