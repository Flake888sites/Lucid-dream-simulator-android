package render;

import static android.opengl.GLES20.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OpenGL ES 2.0 renderer: one world shader (dream warp + fog) and one tiny 2D shader for the HUD. */
public final class Gfx {
    private static final float TEXT_SIZE = 20f;       // in UI units (the UI is laid out on a ~720 unit high screen)
    private static final int DISC_SEGS = 32;

    private static int world, uMV, uP, uTime, uSleep, uAmt, uFog, uFogD, uCam;
    private static int ui, uScreen, uColor, uUseTex, uTex;
    private static int quadVbo;
    private static FloatBuffer quadBuf, discBuf;
    private static float scale = 1f;                  // physical pixels per UI unit, so text stays sharp
    private static final Map<String, float[]> T = new HashMap<>();   // text -> {texture id, width px, height px}
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final String WORLD_VS = """
        attribute vec3 aPos;
        attribute vec3 aCol;
        uniform mat4 uMV;
        uniform mat4 uP;
        uniform float time;
        uniform float sleep;
        uniform float amt;
        uniform vec2 cam;
        varying vec3 col;
        varying float dist;
        void main() {
            vec4 v = vec4(aPos, 1.0);
            float st = sleep;
            // Nothing bends close to the player, so the ground can never rise over you; the dream grows with distance.
            float near = smoothstep(10.0, 70.0, length(v.xz - cam));
            float k = amt * near;
            float w = sin(v.x * 0.21 + time * 1.1) * cos(v.z * 0.19 - time * 0.8);
            vec3 c = aCol;
            if (st > 0.5 && st < 1.5) {
                // LIGHT SLEEP: hills drift up into floating islands, things lean, colours turn iridescent.
                float isl = smoothstep(0.35, 0.95, sin(v.x * 0.031 + 1.7 + time * 0.2) * sin(v.z * 0.027 + 0.3) + 0.5 * sin(v.x * 0.017 - v.z * 0.021 + 2.0 - time * 0.15));
                v.y += isl * (3.5 + 1.5 * sin(time * 0.7 + v.x * 0.05)) * k + w * 0.2 * k;
                float lean = sin(time * 0.4 + v.x * 0.02) * 0.02 * max(v.y, 0.0) * near;
                v.x += lean * 3.0;
                v.z += lean * 2.0;
                c = mix(c, c.gbr, isl * 0.6 * amt) + 0.05 * amt * vec3(sin(v.y * 0.5 + time), sin(v.y * 0.5 + time + 2.0), sin(v.y * 0.5 + time + 4.0));
            } else if (st > 1.5 && st < 2.5) {
                // DEEP SLEEP: the far world folds inside out, towers twist, colours flash to their opposites.
                float r = length(v.xz);
                float fold = smoothstep(0.15, 0.85, 0.5 + 0.5 * sin(time * 0.25 + r * 0.03));
                v.y = mix(v.y, 3.0 - v.y * 0.7, fold * k * 0.6);
                float tw = v.y * 0.015 * sin(time * 0.3) * k;
                float cs = cos(tw);
                float sn = sin(tw);
                v.xz = vec2(cs * v.x - sn * v.z, sn * v.x + cs * v.z);
                v.x += w * 0.3 * k;
                float neg = smoothstep(0.85, 1.0, sin(time * 0.35 + r * 0.02));
                c = mix(c, vec3(1.0) - c, neg * amt * 0.7);
            } else if (st > 2.5) {
                // REM: a kaleidoscope. Petal ripples in the distance, a slow swirl, rainbow bands.
                float ang = atan(v.z, v.x);
                float r = length(v.xz);
                v.y += (sin(time * 3.0 - r * 0.15) * 0.35 + abs(sin(ang * 3.0 + time * 0.4)) * 1.5 * sin(time * 0.9 + r * 0.05)) * k;
                float sw = sin(time * 0.5) * 0.0012 * r * near;
                float cs = cos(sw);
                float sn = sin(sw);
                v.xz = vec2(cs * v.x - sn * v.z, sn * v.x + cs * v.z);
                float hue = time * 0.5 + r * 0.02 + ang;
                c = mix(c, 0.5 + 0.5 * vec3(sin(hue), sin(hue + 2.094), sin(hue + 4.188)), 0.45 * amt);
                c = mix(c, floor(c * 6.0 + 0.5) / 6.0, min(1.0, amt * 1.5));
            }
            float pulse = 1.0 + 0.1 * amt * sin(time * 2.0 + v.y * 0.3 + v.x * 0.05);
            col = c * pulse;
            vec4 e = uMV * v;
            dist = length(e.xyz);
            gl_Position = uP * e;
        }
        """;
    private static final String WORLD_FS = """
        precision mediump float;
        uniform vec3 fog;
        uniform float fogd;
        varying vec3 col;
        varying float dist;
        void main() {
            float f = clamp(1.0 - exp(-pow(dist * fogd, 2.0)), 0.0, 1.0);
            gl_FragColor = vec4(mix(col, fog, f), 1.0);
        }
        """;
    private static final String UI_VS = """
        attribute vec2 aPos;
        attribute vec2 aUv;
        uniform vec2 uScreen;
        varying vec2 uv;
        void main() {
            uv = aUv;
            gl_Position = vec4(aPos.x / uScreen.x * 2.0 - 1.0, 1.0 - aPos.y / uScreen.y * 2.0, 0.0, 1.0);
        }
        """;
    private static final String UI_FS = """
        precision mediump float;
        uniform vec4 uColor;
        uniform int uUseTex;
        uniform sampler2D uTex;
        varying vec2 uv;
        void main() {
            // text textures are white glyphs: only their alpha matters
            if (uUseTex == 1) gl_FragColor = vec4(uColor.rgb, uColor.a * texture2D(uTex, uv).a);
            else gl_FragColor = uColor;
        }
        """;

    private static int compile(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        int[] ok = new int[1];
        glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("Shader compile failed: " + glGetShaderInfoLog(s));
        return s;
    }

    private static int link(String vs, String fs, String attr0, String attr1) {
        int p = glCreateProgram();
        int v = compile(GL_VERTEX_SHADER, vs), f = compile(GL_FRAGMENT_SHADER, fs);
        glAttachShader(p, v); glAttachShader(p, f);
        glBindAttribLocation(p, 0, attr0);
        glBindAttribLocation(p, 1, attr1);
        glLinkProgram(p);
        int[] ok = new int[1];
        glGetProgramiv(p, GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("Program link failed: " + glGetProgramInfoLog(p));
        glDeleteShader(v); glDeleteShader(f);
        return p;
    }

    private static FloatBuffer floats(int n) {
        return ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    /** Call every time the GL context is (re)created. Everything from an earlier context is gone, so it is just forgotten. */
    public static void init() {
        world = link(WORLD_VS, WORLD_FS, "aPos", "aCol");
        uMV = glGetUniformLocation(world, "uMV"); uP = glGetUniformLocation(world, "uP");
        uTime = glGetUniformLocation(world, "time"); uSleep = glGetUniformLocation(world, "sleep");
        uAmt = glGetUniformLocation(world, "amt"); uFog = glGetUniformLocation(world, "fog"); uFogD = glGetUniformLocation(world, "fogd"); uCam = glGetUniformLocation(world, "cam");

        ui = link(UI_VS, UI_FS, "aPos", "aUv");
        uScreen = glGetUniformLocation(ui, "uScreen"); uColor = glGetUniformLocation(ui, "uColor");
        uUseTex = glGetUniformLocation(ui, "uUseTex"); uTex = glGetUniformLocation(ui, "uTex");

        T.clear();
        quadBuf = floats(16);
        discBuf = floats((DISC_SEGS + 2) * 4);
        int[] id = new int[1];
        glGenBuffers(1, id, 0);
        quadVbo = id[0];
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferData(GL_ARRAY_BUFFER, (DISC_SEGS + 2) * 16, null, GL_STREAM_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /** Physical pixels per UI unit (screen height / UI height). Text is rasterised at this size. */
    public static void setScale(float s) {
        s = Math.max(0.5f, s);
        if (Math.abs(s - scale) < 0.01f) return;
        scale = s;
        clearTextures();
    }

    private static void clearTextures() {
        if (!T.isEmpty()) {
            int[] ids = new int[T.size()]; int i = 0;
            for (float[] o : T.values()) ids[i++] = (int) o[0];
            glDeleteTextures(ids.length, ids, 0);
            T.clear();
        }
    }

    public static void use(float time, int sleep, float amt, float[] fog, float fogd, float[] mv, float[] proj, float camX, float camZ) {
        glUseProgram(world);
        glUniformMatrix4fv(uMV, 1, false, mv, 0);
        glUniformMatrix4fv(uP, 1, false, proj, 0);
        glUniform1f(uTime, time); glUniform1f(uSleep, sleep); glUniform1f(uAmt, amt);
        glUniform3f(uFog, fog[0], fog[1], fog[2]); glUniform1f(uFogD, fogd); glUniform2f(uCam, camX, camZ);
    }

    public static void begin2D(int w, int h) {
        glUseProgram(ui);
        glUniform2f(uScreen, w, h);
        glUniform1i(uTex, 0);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void end2D() {
        glUseProgram(0);
        glEnable(GL_DEPTH_TEST); glDisable(GL_BLEND);
    }

    private static void draw(FloatBuffer b, int floats, int mode, int vertices) {
        b.position(0);
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, floats * 4, b);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 16, 8);
        glEnableVertexAttribArray(1);
        glDrawArrays(mode, 0, vertices);
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private static void quad(float x, float y, float w, float h) {
        quadBuf.clear();
        quadBuf.put(new float[]{
            x, y, 0, 0,
            x + w, y, 1, 0,
            x, y + h, 0, 1,
            x + w, y + h, 1, 1});
        draw(quadBuf, 16, GL_TRIANGLE_STRIP, 4);
    }

    public static void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        glUniform1i(uUseTex, 0);
        glUniform4f(uColor, r, g, b, a);
        quad(x, y, w, h);
    }

    /** Filled circle (used for the joystick and the jump button). */
    public static void disc(float cx, float cy, float rad, float r, float g, float b, float a) {
        glUniform1i(uUseTex, 0);
        glUniform4f(uColor, r, g, b, a);
        discBuf.clear();
        discBuf.put(cx).put(cy).put(0).put(0);
        for (int i = 0; i <= DISC_SEGS; i++) {
            double ang = i * 2 * Math.PI / DISC_SEGS;
            discBuf.put(cx + (float) Math.cos(ang) * rad).put(cy + (float) Math.sin(ang) * rad).put(0).put(0);
        }
        draw(discBuf, (DISC_SEGS + 2) * 4, GL_TRIANGLE_FAN, DISC_SEGS + 2);
    }

    private static float[] tex(String s) {
        float[] t = T.get(s);
        if (t != null) return t;
        if (T.size() > 300) clearTextures();
        PAINT.setTypeface(Typeface.DEFAULT_BOLD);
        PAINT.setTextSize(TEXT_SIZE * scale);
        PAINT.setColor(0xFFFFFFFF);
        Paint.FontMetrics fm = PAINT.getFontMetrics();
        int w = Math.max(1, (int) Math.ceil(PAINT.measureText(s))) + 2;
        int h = (int) Math.ceil(fm.descent - fm.ascent) + 2;
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        cv.drawText(s, 1, 1 - fm.ascent, PAINT);
        int[] id = new int[1];
        glGenTextures(1, id, 0);
        glBindTexture(GL_TEXTURE_2D, id[0]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, bm, 0);
        bm.recycle();
        t = new float[]{id[0], w, h};
        T.put(s, t);
        return t;
    }

    /** Text width in UI units. */
    public static int textW(String s) { return (int) Math.ceil(tex(s)[1] / scale); }

    public static void text(String s, float x, float y, float r, float g, float b) { text(s, x, y, r, g, b, 1f); }

    public static void text(String s, float x, float y, float r, float g, float b, float a) {
        if (s.isEmpty()) return;
        float[] t = tex(s);
        glBindTexture(GL_TEXTURE_2D, (int) t[0]);
        glUniform1i(uUseTex, 1);
        glUniform4f(uColor, r, g, b, a);
        quad(x, y, t[1] / scale, t[2] / scale);
    }

    public static List<String> wrap(String s, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            if (line.length() + w.length() + 1 > max && line.length() > 0) { out.add(line.toString()); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
