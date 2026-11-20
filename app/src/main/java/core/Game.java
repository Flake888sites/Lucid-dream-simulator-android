package core;

import static android.opengl.GLES20.*;

import audio.Synth;
import creature.Creature;
import hub.Dialogue;
import image.Features;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import render.Gfx;
import render.GpuMesh;
import render.Mat4;
import world.*;

/**
 * The whole game, without any Android UI classes: the GL view calls surfaceCreated / surfaceChanged / drawFrame on the GL thread
 * and feeds touches and actions through the thread-safe post* methods.
 */
public final class Game {
    /** What the game needs from the app around it. */
    public interface Host {
        File dataDir();
        void pickImage();   // open the system image picker; the app later calls post(() -> game.setFeatures(...))
        void quit();
    }

    public static final int ACT_MENU = 1, ACT_MIND = 2, ACT_INTERACT = 3, ACT_HUB = 4, ACT_LOAD = 5;
    static final int B_JUMP = 1, B_RUN = 2, B_USE = 3, B_MIND = 4, B_HUB = 5, B_LOAD = 6, B_MENU = 7, B_CLOSE = 8;
    static final int R_NONE = 0, R_JOY = 1, R_LOOK = 2, R_BTN = 3, R_UI = 4;
    static final float JOY_R = 90f;
    static final String[] SLEEP = {"Wakefulness", "Light Sleep", "Deep Sleep", "REM"};
    static final float[][] PORTALS = {{-12, -12}, {12, -12}, {-12, 12}, {12, 12}};
    static final double DT = 1 / 60.0;

    /** A touch button drawn on top of the game. */
    static final class Btn {
        final int code; final String label; final float x, y, w, h; final boolean round;
        Btn(int code, String label, float x, float y, float w, float h, boolean round) {
            this.code = code; this.label = label; this.x = x; this.y = y; this.w = w; this.h = h; this.round = round;
        }
        boolean hit(float px, float py) { return px >= x - 8 && px <= x + w + 8 && py >= y - 8 && py <= y + h + 8; }
    }

    final Host host;
    final Settings cfg;
    int R;                              // chunk view radius

    // ---- screen: the UI is laid out in "UI units": 720 high (or 1280 wide on squarer screens)
    int W = 1280, H = 720, fbW = 1280, fbH = 720;

    // ---- input (written from the GL thread only; the post* methods hand events over safely)
    final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<float[]> touchQ = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<Integer> actionQ = new ConcurrentLinkedQueue<>();
    final ArrayDeque<Integer> actions = new ArrayDeque<>();
    final int[] role = new int[16], btnOf = new int[16];
    final float[] lastX = new float[16], lastY = new float[16];
    float moveF, moveR, mdx, mdy;
    boolean jumpHeld, runOn;
    int joyId = -1; float joyX0, joyY0, joyX, joyY;
    boolean tapPending; float tapX, tapY;
    int mouseX, mouseY; boolean clicked;
    Btn[] btns = new Btn[0];
    volatile boolean pauseRequested;

    // ---- state
    public volatile boolean loading;
    public volatile String error = "";
    final MenuPanel menu = new MenuPanel();
    final MindPanel mindPanel = new MindPanel();
    int fps, fpsN; long fpsT0 = System.nanoTime();
    float viewFx, viewFz, cullCos = -1f;
    Features feat; World world; volatile Synth synth; Creature guide;
    final GpuMesh[] hub = new GpuMesh[4];
    boolean inHub = true, paused, talking, mindOpen;
    volatile String worldHash = "...", audioHash = "...";
    String node = "root";
    float px, py, pz, vy, yaw, pitch, trees, waterR, light, dream;
    int sleep, hubState;
    double time, acc; long last;
    final Map<Long, Chunk> chunks = new HashMap<>();
    final Map<Long, GpuMesh> gpu = new HashMap<>();
    final Set<Long> pending = new HashSet<>();
    final Map<Long, List<Creature>> cmap = new HashMap<>();
    final List<Creature> all = new ArrayList<>();
    final ConcurrentLinkedQueue<Chunk> done = new ConcurrentLinkedQueue<>();
    final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "gen"); t.setDaemon(true); t.setPriority(Thread.NORM_PRIORITY - 2); return t;
    });
    final Creature.Env env = new Creature.Env();
    final List<Build> builds = new ArrayList<>();
    final Map<Long, int[]> dirty = new HashMap<>();
    Creature sel;
    String notice = "";
    float noticeT;
    final Creature.Hooks hooks = new Creature.Hooks() {
        @Override public Chunk.Obj nearestObj(float x, float z, int kind, float range) {
            float cs = World.CS * World.CELL;
            int cx = (int) Math.floor(x / cs), cz = (int) Math.floor(z / cs), span = (int) Math.ceil(range / cs);
            Chunk.Obj best = null; float bd = range * range;
            for (int i = -span; i <= span; i++) for (int j = -span; j <= span; j++) {
                Chunk c = chunks.get(key(cx + i, cz + j));
                if (c == null) continue;
                for (Chunk.Obj o : c.objs) {
                    if (o.kind() != kind || world.removed.contains(o.id())) continue;
                    float d = (o.x() - x) * (o.x() - x) + (o.z() - z) * (o.z() - z);
                    if (d < bd) { bd = d; best = o; }
                }
            }
            return best;
        }
        @Override public void destroyObj(Chunk.Obj o) {
            if (!world.removed.add(o.id())) return;
            float cs = World.CS * World.CELL;
            int cx = (int) Math.floor(o.x() / cs), cz = (int) Math.floor(o.z() / cs);
            dirty.put(key(cx, cz), new int[]{cx, cz});
        }
        @Override public void addBuild(Build b) {
            builds.add(b);
            if (builds.size() > 300) builds.remove(0);
        }
        @Override public void removeBuild(Build b) { builds.remove(b); }
        @Override public Build nearestBuild(float x, float z, float range, Object exclude) {
            Build best = null; float bd = range * range;
            for (Build b : builds) {
                if (exclude != null && b.owner == exclude) continue;
                float d = (b.x - x) * (b.x - x) + (b.z - z) * (b.z - z);
                if (d < bd) { bd = d; best = b; }
            }
            return best;
        }
    };
    final Mesh cm = new Mesh();
    GpuMesh cmg;
    final float[] fog = {.1f, .1f, .15f};
    float fogD = 0.012f;
    float[] proj = Mat4.identity(), mv = Mat4.identity();

    public Game(Host host) {
        this.host = host;
        cfg = Settings.load(host.dataDir());
        R = cfg.radius;
    }

    static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }
    static float cl(float v) { return Math.max(0f, Math.min(1f, v)); }

    // ------------------------------------------------------------------ calls from any thread

    /** Runs something on the GL thread, before the next frame. */
    public void post(Runnable r) { tasks.add(r); }
    public void postAction(int act) { actionQ.add(act); }
    /** action: 0 = finger down, 1 = move, 2 = up. x/y in view pixels. */
    public void postTouch(int action, int id, float x, float y) { touchQ.add(new float[]{action, id, x, y}); }
    /** The app went to the background: stop the music and open the menu when it comes back. */
    public void onAppPause() {
        pauseRequested = true;
        Synth s = synth; if (s != null) s.stop();
    }
    public void onAppResume() { Synth s = synth; if (s != null) s.start(); }
    public void setLoading(boolean b) { loading = b; }
    public void setError(String e) { error = e; }

    // ------------------------------------------------------------------ GL thread lifecycle

    public void surfaceCreated() {
        Gfx.init();                         // also called again after Android destroys the GL context
        glEnable(GL_DEPTH_TEST);
        boolean again = cmg != null;
        cmg = new GpuMesh(true);
        if (again) {                        // old buffers are gone with the old context: forget them and rebuild
            gpu.clear(); chunks.clear(); pending.clear(); cmap.clear(); all.clear(); done.clear(); dirty.clear();
            if (feat != null) for (int s = 0; s < 4; s++) { hub[s] = new GpuMesh(false); hub[s].upload(buildHub(s)); }
        }
        last = 0;
    }

    public void surfaceChanged(int w, int h) {
        fbW = Math.max(1, w); fbH = Math.max(1, h);
        float aspect = (float) fbW / fbH;
        if (aspect >= 1280f / 720f) { H = 720; W = Math.round(720 * aspect); }
        else { W = 1280; H = Math.round(1280 / aspect); }
        Gfx.setScale((float) fbH / H);
        glViewport(0, 0, fbW, fbH);
    }

    /** Builds the world for a new image. GL thread only (use post). */
    public void setFeatures(Features nf) {
        loading = false; error = "";
        gpu.values().forEach(GpuMesh::close);
        gpu.clear();
        chunks.clear(); pending.clear(); cmap.clear(); all.clear(); done.clear(); builds.clear(); dirty.clear();
        mindOpen = false; sel = null; env.hooks = hooks; paused = false; talking = false;
        feat = nf; world = new World(nf); world.vegetation = cfg.vegetation / 100f;
        worldHash = "..."; audioHash = "...";
        pool.submit(() -> {                 // the reproducibility stamps cost a moment of CPU: keep them off the GL thread
            try {
                World w = new World(nf);
                worldHash = w.hash(); audioHash = Synth.hash(nf);
            } catch (Exception e) { worldHash = audioHash = "?"; }
        });
        Synth old = synth; if (old != null) old.stop();
        Synth ns = new Synth(nf); ns.volume = cfg.volume / 100f; ns.start();
        synth = ns;
        for (int s = 0; s < 4; s++) {
            if (hub[s] != null) hub[s].close();
            hub[s] = new GpuMesh(false);
            hub[s].upload(buildHub(s));
        }
        guide = new Creature(nf.seed ^ 0x5EEDL, nf.palette); guide.frozen = true; guide.z = -4;
        goHub();
    }

    /** A made-up "image" so the game can be tried without picking a picture. */
    public static Features randomDream(long seed) throws Exception {
        final int n = 64;
        final int[] px = new int[n * n];
        java.util.Random r = new java.util.Random(seed);
        float[][] blobs = new float[7][6];
        for (float[] b : blobs) { b[0] = r.nextInt(n); b[1] = r.nextInt(n); b[2] = 6 + r.nextInt(22); b[3] = r.nextFloat(); b[4] = r.nextFloat(); b[5] = r.nextFloat(); }
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            float cr = 0.1f, cg = 0.1f, cb = 0.15f;
            for (float[] b : blobs) {
                float d = (float) Math.hypot(x - b[0], y - b[1]);
                float k = Math.max(0f, 1f - d / b[2]);
                cr += b[3] * k; cg += b[4] * k; cb += b[5] * k;
            }
            px[y * n + x] = 0xFF000000 | ((int) (cl(cr) * 255) << 16) | ((int) (cl(cg) * 255) << 8) | (int) (cl(cb) * 255);
        }
        return Features.load(new Features.Source() {
            @Override public int width() { return n; }
            @Override public int height() { return n; }
            @Override public int[] rows(int y0, int y1) { return java.util.Arrays.copyOfRange(px, y0 * n, y1 * n); }
        }, seed);
    }

    Mesh buildHub(int s) {
        Mesh m = new Mesh(); float[][] p = feat.palette; float[] kk = {1f, .8f, .55f, 1.2f};
        for (int i = 0; i < 20; i++) for (int j = 0; j < 20; j++) {
            float x = -20 + i * 2, z = -20 + j * 2; float[] c = ((i + j) & 1) == 0 ? p[0] : p[1];
            if (s == 3) c = new float[]{c[1], c[2], c[0]};
            m.tri(x, 0, z, x + 2, 0, z, x, 0, z + 2, c, kk[s]); m.tri(x + 2, 0, z, x + 2, 0, z + 2, x, 0, z + 2, c, kk[s]);
        }
        for (int i = 0; i < 4; i++) {
            m.prism(PORTALS[i][0], 0, PORTALS[i][1], 1.2f, 6f, 4, 0.785f, p[i], 1f);
            m.blob(PORTALS[i][0], 7.2f, PORTALS[i][1], 0.8f, 0.8f, 6, p[i], 1.4f);
        }
        return m;
    }

    void goHub() { mindOpen = false; sel = null; inHub = true; px = 0; pz = 14; py = 1.7f; vy = 0; yaw = 0; pitch = 0; synth.sleep = hubState; }
    void enterWorld(int s) { inHub = false; sleep = s; px = 0; pz = 0; py = world.height(0, 0) + 3; vy = 0; synth.sleep = s; }

    // ------------------------------------------------------------------ frame

    public void drawFrame() {
        for (Runnable r; (r = tasks.poll()) != null; ) r.run();
        long now = System.nanoTime();
        if (last == 0) last = now;
        acc += (now - last) / 1e9; last = now;
        if (acc > 0.1) acc = 0.1;
        if (pauseRequested) {
            pauseRequested = false;
            if (feat != null && !talking) { paused = true; menu.page = 0; }
        }
        for (Integer a; (a = actionQ.poll()) != null; ) actions.add(a);
        for (float[] t; (t = touchQ.poll()) != null; ) touch((int) t[0], (int) t[1], t[2], t[3]);
        clicked = tapPending; mouseX = (int) tapX; mouseY = (int) tapY; tapPending = false;

        fpsN++;
        if (now - fpsT0 >= 1_000_000_000L) { fps = fpsN; fpsN = 0; fpsT0 = now; }

        if (feat == null) { title(); return; }
        input();
        while (acc >= DT) { update((float) DT); acc -= DT; }
        render();
    }

    boolean button(String label, float x, float y, float w, float h, float r, float g, float b) {
        boolean hit = mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= y - 4 && mouseY <= y + h + 4;
        float l = hit && clicked ? 0.25f : 0f;
        Gfx.rect(x, y, w, h, Math.min(1f, r + l), Math.min(1f, g + l), Math.min(1f, b + l), 0.94f);
        Gfx.text(label, x + (w - Gfx.textW(label)) / 2f, y + (h - 24) / 2f + 1, 1, 1, 1);
        return hit && clicked;
    }

    void centered(String s, float y, float r, float g, float b) { Gfx.text(s, (W - Gfx.textW(s)) / 2f, y, r, g, b); }

    /** Start screen, shown until an image is chosen. */
    void title() {
        for (Integer a; (a = actions.poll()) != null; ) if (a == ACT_MENU) host.quit();
        glViewport(0, 0, fbW, fbH);
        glClearColor(0.05f, 0.04f, 0.12f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        Gfx.begin2D(W, H);
        float y = H * 0.2f;
        centered("L U C I D   D R E A M", y, 1f, 0.95f, 0.7f);
        centered("Pick any picture. It becomes the dream you walk through.", y + 44, 0.8f, 0.8f, 1f);
        float bw = 440, bh = 60, x = (W - bw) / 2f; y += 120;
        if (loading) centered("Reading the image...", y + 20, 1f, 0.9f, 0.6f);
        else {
            if (button("Choose an image", x, y, bw, bh, 0.2f, 0.35f, 0.55f)) host.pickImage();
            if (button("Random dream (no image)", x, y + bh + 18, bw, bh, 0.25f, 0.25f, 0.5f)) {
                try { setFeatures(randomDream(System.nanoTime())); } catch (Exception e) { error = "Could not start: " + e; }
            }
            if (!error.isEmpty()) centered(error, y + 2 * bh + 50, 1f, 0.5f, 0.5f);
        }
        Gfx.end2D();
    }

    // ------------------------------------------------------------------ touch

    Btn hitBtn(float vx, float vy) {
        for (Btn b : btns) if (b.hit(vx, vy)) return b;
        return null;
    }

    void pressButton(int code, boolean down) {
        switch (code) {
            case B_JUMP -> jumpHeld = down;
            case B_RUN -> { if (down) runOn = !runOn; }
            case B_USE -> { if (down) actions.add(ACT_INTERACT); }
            case B_MIND, B_CLOSE -> { if (down) actions.add(ACT_MIND); }
            case B_HUB -> { if (down) actions.add(ACT_HUB); }
            case B_LOAD -> { if (down) actions.add(ACT_LOAD); }
            case B_MENU -> { if (down) actions.add(ACT_MENU); }
            default -> { }
        }
    }

    void touch(int action, int id, float sx, float sy) {
        float vx = sx * W / fbW, vy = sy * H / fbH;
        if (feat == null) {                 // title screen: every touch is a click
            if (action == 0) { tapPending = true; tapX = vx; tapY = vy; }
            return;
        }
        if (id < 0 || id >= role.length) return;
        if (action == 0) {
            if (paused || talking) { tapPending = true; tapX = vx; tapY = vy; role[id] = R_UI; return; }
            Btn b = hitBtn(vx, vy);
            if (b != null) { role[id] = R_BTN; btnOf[id] = b.code; pressButton(b.code, true); return; }
            if (mindOpen) { tapPending = true; tapX = vx; tapY = vy; role[id] = R_UI; return; }
            if (vx < W * 0.5f && joyId < 0) {
                role[id] = R_JOY; joyId = id; joyX0 = joyX = vx; joyY0 = joyY = vy; moveF = moveR = 0;
            } else {
                role[id] = R_LOOK; lastX[id] = sx; lastY[id] = sy;
            }
        } else if (action == 1) {
            if (role[id] == R_JOY) {
                joyX = vx; joyY = vy;
                moveR = Math.max(-1f, Math.min(1f, (joyX - joyX0) / JOY_R));
                moveF = Math.max(-1f, Math.min(1f, -(joyY - joyY0) / JOY_R));
            } else if (role[id] == R_LOOK) {
                mdx += px - lastX[id]; mdy += py - lastY[id]; lastX[id] = sx; lastY[id] = sy;
            }
        } else {
            if (role[id] == R_JOY) { joyId = -1; moveF = moveR = 0; }
            else if (role[id] == R_BTN) pressButton(btnOf[id], false);
            role[id] = R_NONE;
        }
    }

    /** The buttons for the current situation (also used for hit testing). */
    void layoutButtons() {
        List<Btn> l = new ArrayList<>(8);
        float m = 28;
        if (mindOpen) {
            l.add(new Btn(B_CLOSE, "Close", m, 74, 130, 54, false));     // the mind window covers the right side: only a Close button
        } else if (!paused && !talking) {
            l.add(new Btn(B_MENU, "Menu", W - m - 100, m, 100, 54, false));
            l.add(new Btn(B_JUMP, "Jump", W - m - 130, H - m - 130, 130, 130, true));
            l.add(new Btn(B_RUN, runOn ? "Run ON" : "Run", W - m - 130 - 150, H - m - 76, 130, 64, false));
            if (inHub) {
                l.add(new Btn(B_USE, "Use", W - m - 130 - 150, H - m - 76 - 90, 130, 64, false));
                l.add(new Btn(B_LOAD, "Image", W - m - 100 - 116, m, 100, 54, false));
            } else {
                l.add(new Btn(B_MIND, "Mind", W - m - 130 - 150, H - m - 76 - 90, 130, 64, false));
                l.add(new Btn(B_HUB, "Hub", W - m - 100 - 116, m, 100, 54, false));
            }
        }
        btns = l.toArray(new Btn[0]);
    }

    void drawControls() {
        for (Btn b : btns) {
            boolean on = (b.code == B_JUMP && jumpHeld) || (b.code == B_RUN && runOn);
            float a = on ? 0.55f : 0.32f;
            if (b.round) {
                Gfx.disc(b.x + b.w / 2f, b.y + b.h / 2f, b.w / 2f, 0.5f, 0.55f, 1f, a);
            } else {
                Gfx.rect(b.x, b.y, b.w, b.h, 0.3f, 0.35f, 0.7f, a);
            }
            Gfx.text(b.label, b.x + (b.w - Gfx.textW(b.label)) / 2f, b.y + (b.h - 24) / 2f + 1, 1, 1, 1, 0.95f);
        }
        if (joyId >= 0) {
            float dx = joyX - joyX0, dy = joyY - joyY0, d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > JOY_R) { dx *= JOY_R / d; dy *= JOY_R / d; }
            Gfx.disc(joyX0, joyY0, JOY_R, 1f, 1f, 1f, 0.12f);
            Gfx.disc(joyX0 + dx, joyY0 + dy, 38, 0.8f, 0.85f, 1f, 0.45f);
        } else if (!paused && !talking && !mindOpen) {
            Gfx.text("touch here to move", 70, H - 70, 1f, 1f, 1f, 0.35f);
            Gfx.text("drag here to look", W - 560, H - 70, 1f, 1f, 1f, 0.35f);
        }
    }

    // ------------------------------------------------------------------ logic

    void input() {
        float dx = mdx, dy = mdy; mdx = mdy = 0;
        if (!paused && !talking && !mindOpen) {
            float sn = 0.14f * (720f / fbH) * cfg.sens / 100f;
            yaw += dx * sn; pitch = Math.max(-89, Math.min(89, pitch + dy * sn));
        }
        for (Integer ev; (ev = actions.poll()) != null; ) {
            int k = ev;
            if (k == ACT_MENU) {
                if (mindOpen) closeMind(); else if (talking) talking = false;
                else if (paused && menu.page == 1) menu.page = 0;
                else { paused = !paused; menu.page = 0; }
            }
            else if (paused || talking) continue;
            else if (k == ACT_MIND && !inHub) { if (mindOpen) closeMind(); else openMind(); }
            else if (mindOpen) continue;
            else if (k == ACT_INTERACT && inHub) interact();
            else if (k == ACT_HUB && !inHub) goHub();
            else if (k == ACT_LOAD && inHub) host.pickImage();
        }
    }

    /** Re-applies settings after a change in the menu, then saves them. */
    void applySettings() {
        R = cfg.radius;
        Synth s = synth;
        if (s != null) s.volume = cfg.volume / 100f;
        float v = cfg.vegetation / 100f;
        if (world != null && Math.abs(world.vegetation - v) > 1e-3f) {
            world.vegetation = v;
            for (Chunk c : chunks.values()) dirty.put(key(c.cx, c.cz), new int[]{c.cx, c.cz});   // regrow the loaded chunks
        }
        cfg.save();
    }

    /** Cheap horizontal view-cone test so chunks, creatures and buildings behind you are not drawn. */
    boolean inView(float x, float z, float slack) {
        float dx = x - px, dz = z - pz, d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < slack + 12f) return true;
        return (dx * viewFx + dz * viewFz) / d > cullCos - slack / d;
    }

    /** Picks the creature you are facing (like pressing Q on the PC version). */
    void openMind() {
        float rad = (float) Math.toRadians(yaw), fx = (float) Math.sin(rad), fz = -(float) Math.cos(rad);
        Creature best = null; float bs = 1e9f;
        for (Creature c : all) {
            float dx = c.x - px, dz = c.z - pz, d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d > 35 || d < 0.01f) continue;
            float dot = (dx * fx + dz * fz) / d;
            float score = d * (2.2f - dot) + (dot < 0.3f ? 25f : 0f);
            if (score < bs) { bs = score; best = c; }
        }
        if (best == null || dist(best.x, best.z) > 35) { notice = "No creature close enough. Walk up to one and tap Mind."; noticeT = 3f; return; }
        sel = best; mindOpen = true; mindPanel.tab = 0;
    }

    void closeMind() { mindOpen = false; sel = null; }

    float dist(float x, float z) { return (float) Math.sqrt((px - x) * (px - x) + (pz - z) * (pz - z)); }

    void interact() {
        if (dist(guide.x, guide.z) < 4) { talking = true; node = "root"; return; }
        for (int i = 0; i < 4; i++) if (dist(PORTALS[i][0], PORTALS[i][1]) < 3) { enterWorld(i); return; }
    }

    void pump() {
        for (int n = 0; n < 2; n++) {
            Chunk c = done.poll(); if (c == null) return;
            long k = key(c.cx, c.cz); pending.remove(k);
            if (c.owner != world) continue;
            GpuMesh g = new GpuMesh(false);
            g.upload(c.mesh);
            c.mesh = null;
            boolean regen = chunks.containsKey(k);
            GpuMesh old = gpu.put(k, g); if (old != null) old.close();
            chunks.put(k, c);
            if (regen) continue;
            List<Creature> l = new ArrayList<>();
            for (Chunk.Spawn s : c.spawns) {
                Creature cr = new Creature(s.seed(), feat.palette); cr.x = s.x(); cr.z = s.z(); cr.y = world.height(s.x(), s.z()); l.add(cr); all.add(cr);
            }
            cmap.put(k, l);
        }
    }

    void update(float dt) {
        if (paused || talking) return;
        time += dt;
        if (noticeT > 0) noticeT -= dt;
        if (mindOpen && (sel == null || !all.contains(sel))) closeMind();
        boolean ctl = !mindOpen;
        float rad = (float) Math.toRadians(yaw);
        float fx = (float) Math.sin(rad), fz = -(float) Math.cos(rad), rx = (float) Math.cos(rad), rz = (float) Math.sin(rad);
        float mx = 0, mz = 0;
        if (ctl) {
            mx = fx * moveF + rx * moveR; mz = fz * moveF + rz * moveR;
            float len = (float) Math.sqrt(mx * mx + mz * mz);
            if (len > 1f) { mx /= len; mz /= len; }
        }
        float sp = (runOn ? 12 : 6) * dt;
        px += mx * sp; pz += mz * sp;
        if (inHub) { px = Math.max(-19, Math.min(19, px)); pz = Math.max(-19, Math.min(19, pz)); }
        float ground = inHub ? 0 : world.height(px, pz);
        float grav = 25f, jump = 9f;
        if (!inHub) {
            if (sleep == 1) { grav = 7f; jump = 7f; }                                                 // light sleep: floaty moon-gravity
            else if (sleep == 2) { jump = 15f; }                                                      // deep sleep: enormous leaps
            else if (sleep == 3) { grav = 25f * (0.3f + 1.1f * Math.abs((float) Math.sin(time * 0.7))); jump = 12f; }   // REM: gravity breathes
        }
        if (ctl && py <= ground + 1.71f && jumpHeld) vy = jump;
        vy -= grav * dt; py += vy * dt;
        if (mindOpen && sel != null) {
            float ddx = sel.x - px, ddz = sel.z - pz, hd = (float) Math.sqrt(ddx * ddx + ddz * ddz);
            float wantYaw = (float) Math.toDegrees(Math.atan2(ddx, -ddz)) - 22f;
            float dy = wantYaw - yaw; dy -= 360f * Math.round(dy / 360f);
            yaw += dy * Math.min(1f, dt * 4f);
            float wantPitch = Math.max(-60f, Math.min(60f, (float) Math.toDegrees(Math.atan2(py - (sel.y + 0.6f), Math.max(hd, 0.5f)))));
            pitch += (wantPitch - pitch) * Math.min(1f, dt * 4f);
        }
        if (py < ground + 1.7f) { py = ground + 1.7f; vy = 0; }

        if (inHub) {
            float best = 10;
            for (int i = 0; i < 4; i++) { float d = dist(PORTALS[i][0], PORTALS[i][1]); if (d < best) { best = d; hubState = i; } }
            guide.yaw = (float) Math.atan2(px - guide.x, -(pz - guide.z));
            synth.sleep = hubState; synth.energy = 0.15f; synth.dark = 0.1f; synth.water = 0f;
            trees = 0; light = 0.8f; dream = 0.5f + 0.5f * (float) Math.sin(time * 0.05);
            return;
        }
        pump();
        float cs = World.CS * World.CELL;
        int cx = (int) Math.floor(px / cs), cz = (int) Math.floor(pz / cs);
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            final int gx = cx + dx, gz = cz + dz; long k = key(gx, gz);
            if (!chunks.containsKey(k) && !pending.contains(k)) {
                pending.add(k); final World w = world;
                pool.submit(() -> done.add(w.gen(gx, gz)));
            }
        }
        for (Iterator<Map.Entry<Long, int[]>> it = dirty.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, int[]> en = it.next();
            if (pending.contains(en.getKey())) continue;
            if (chunks.containsKey(en.getKey())) {
                final int qx = en.getValue()[0], qz = en.getValue()[1]; final World w = world;
                pending.add(en.getKey());
                pool.submit(() -> done.add(w.gen(qx, qz)));
            }
            it.remove();
        }
        for (Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Chunk> en = it.next(); Chunk c = en.getValue();
            if (Math.abs(c.cx - cx) > R + 1 || Math.abs(c.cz - cz) > R + 1) {
                GpuMesh g = gpu.remove(en.getKey()); if (g != null) g.close();
                List<Creature> l = cmap.remove(en.getKey()); if (l != null) all.removeAll(l);
                it.remove();
            }
        }
        Chunk cur = chunks.get(key(cx, cz));
        if (cur != null) {
            trees += (Math.min(1, cur.trees / 45f) - trees) * 0.02f;
            waterR += (cur.water / (float) (World.CS * World.CS) - waterR) * 0.02f;
        }
        dream = 0.5f + 0.5f * (float) Math.sin(time * 0.05);
        light = cl(feat.brightness * (0.7f + 0.3f * dream) + 0.25f - trees * 0.4f);
        int near = 0;
        for (Creature c : all) {
            float nx = 0, nz = 0; int nb = 0;
            for (Creature o : all) if (o != c) { float d = (o.x - c.x) * (o.x - c.x) + (o.z - c.z) * (o.z - c.z); if (d < 144) { nb++; nx += o.x; nz += o.z; } }
            env.light = light; env.trees = trees; env.dream = dream; env.sleep = sleep; env.px = px; env.pz = pz; env.hooks = hooks;
            env.neighbors = nb; env.gx = nb > 0 ? nx / nb : c.x; env.gz = nb > 0 ? nz / nb : c.z;
            c.update(sleep == 2 ? dt * 0.4f : sleep == 3 ? dt * 1.6f : dt, world, env);       // deep sleep: slow time; REM: fast time
            if (dist(c.x, c.z) < 30) near++;
        }
        synth.energy = Math.min(1f, near / 6f + (sleep == 3 ? 0.3f : 0f));
        synth.dark = cl(trees * 0.8f + (1 - light) * 0.6f);
        synth.water = cl(waterR * 2);
    }

    // ------------------------------------------------------------------ drawing

    void render() {
        layoutButtons();
        float[] a = feat.palette[2], b = feat.palette[0]; float t = (float) time;
        for (int i = 0; i < 3; i++) {
            float base = (a[i] * 0.6f + b[i] * 0.4f) * (0.4f + 0.6f * light);
            fog[i] = cl(base * (1 - trees * 0.5f) + 0.1f * (float) Math.sin(t * 0.2 + i * 2));
        }
        int st = inHub ? hubState : sleep;
        fogD = inHub ? 0.02f : 0.012f + trees * 0.012f + (st == 2 ? 0.005f : 0f);
        glViewport(0, 0, fbW, fbH);
        glClearColor(fog[0], fog[1], fog[2], 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        float fov = cfg.fov, dst = cfg.distortion / 100f;
        if (!inHub) {
            if (st == 1) fov += 3f * dst * (float) Math.sin(t * 0.4f);
            else if (st == 2) fov += (8f * (float) Math.sin(t * 0.2f) + 4f * (float) Math.sin(t * 0.55f)) * dst;
            else if (st == 3) fov += 6f * dst * (float) Math.sin(t * 1.8f);
        }
        proj = Mat4.perspective(fov, (float) fbW / fbH, 0.2f, 300f);
        float rad = (float) Math.toRadians(yaw);
        viewFx = (float) Math.sin(rad); viewFz = -(float) Math.cos(rad);
        float halfH = (float) Math.atan(Math.tan(Math.toRadians((cfg.fov + 20f) / 2)) * fbW / fbH);
        cullCos = (float) Math.cos(Math.min(halfH + 0.35f, 2.4f));
        mv = Mat4.view(pitch, yaw, px, py, pz);
        Gfx.use(t, st, (0.2f + feat.saturation * 0.8f) * dst, fog, fogD * cfg.fog / 100f, mv, proj, px, pz);
        cm.n = 0;
        if (inHub) { hub[hubState].draw(); guide.build(cm, t); }
        else {
            float chunkSize = World.CS * World.CELL;
            for (Map.Entry<Long, GpuMesh> en : gpu.entrySet()) {
                Chunk ck = chunks.get(en.getKey());
                if (ck == null || inView((ck.cx + 0.5f) * chunkSize, (ck.cz + 0.5f) * chunkSize, 24f)) en.getValue().draw();
            }
            for (Creature c : all) if (c == sel || (dist(c.x, c.z) < cfg.creatureDist && inView(c.x, c.z, 4f))) c.build(cm, t, st);
            int nb = 0;
            for (Build bd : builds) if (nb < 120 && dist(bd.x, bd.z) < cfg.creatureDist + 30 && inView(bd.x, bd.z, 6f)) { bd.build(cm, t); nb++; }
        }
        cmg.upload(cm);
        cmg.draw();

        Gfx.begin2D(W, H);
        List<Creature> talkers = inHub ? Collections.singletonList(guide) : all;
        for (Creature c : talkers) {
            if (c.sayT <= 0 || dist(c.x, c.z) > 40) continue;
            float[] s = Mat4.project(proj, mv, c.x, c.topY(), c.z, W, H);
            if (s != null) {
                float w = Gfx.textW(c.say) + 16;
                Gfx.rect(s[0] - w / 2, s[1] - 32, w, 30, 0, 0, 0, 0.6f);
                Gfx.text(c.say, s[0] - w / 2 + 8, s[1] - 28, 1, 1, 1);
            }
        }
        hud();
        if (mindOpen && sel != null) drawMind();
        if (noticeT > 0) centered(notice, H * 0.62f, 1f, 0.9f, 0.6f);
        if (!paused && !talking) drawControls();
        if (talking && !paused) dialogue();
        if (paused) {
            int before = cfg.rev;
            int act = menu.draw(cfg, inHub, W, H, mouseX, mouseY, clicked);
            if (cfg.rev != before) applySettings();
            if (act == MenuPanel.RESUME) paused = false;
            else if (act == MenuPanel.HUB) { paused = false; goHub(); }
            else if (act == MenuPanel.CHOOSE) { paused = false; host.pickImage(); }
            else if (act == MenuPanel.QUIT) host.quit();
        }
        Gfx.end2D();
    }

    void drawMind() {
        float[] s = Mat4.project(proj, mv, sel.x, sel.topY() + 0.8f, sel.z, W, H);
        if (s != null) Gfx.rect(s[0] - 6, s[1] - 6, 12, 12, 1f, 0.9f, 0.4f, 0.9f);
        mindPanel.draw(sel, W, H, mouseX, mouseY, clicked && !paused, (float) time);
    }

    void hud() {
        float m = 28;
        if (!mindOpen) {
            Gfx.text("image " + shortHash(feat.hash, 12) + "  world " + shortHash(worldHash, 8) + "  audio " + shortHash(audioHash, 8), m, 8, 1, 1, 1, 0.6f);
            Gfx.text((inHub ? "Hub - " : "Dream - ") + SLEEP[inHub ? hubState : sleep], m, 32, 1, 1, 0.7f);
        } else {
            Gfx.text("Dream - " + SLEEP[sleep], m, 32, 1, 1, 0.7f);
        }
        Gfx.rect(W / 2f - 2, H / 2f - 2, 4, 4, 1, 1, 1, 0.7f);
        if (cfg.showFps) Gfx.text(fps + " FPS", W / 2f - 30, 8, 1f, 1f, 0.5f);
        if (inHub && !talking && !paused) {
            String hint = null;
            if (dist(guide.x, guide.z) < 4) hint = "Tap Use to talk to the Guide";
            for (int i = 0; i < 4; i++) if (dist(PORTALS[i][0], PORTALS[i][1]) < 3) hint = "Tap Use to enter " + SLEEP[i];
            if (hint == null) hint = "Walk to a pillar to enter a dream";
            centered(hint, H - 56, 1, 1, 1);
        }
    }

    static String shortHash(String h, int n) { return h.length() >= n ? h.substring(0, n) : h; }

    void dialogue() {
        Dialogue.Node n = Dialogue.get(node);
        List<String> ls = Gfx.wrap(n.text(), 56);
        int bw = 760, x = (W - bw) / 2, rows = n.labels().length;
        int hgt = 30 + ls.size() * 26 + rows * 56 + 14, y = Math.max(8, H - hgt - 24);
        Gfx.rect(x, y, bw, hgt, 0, 0, 0, 0.82f);
        for (int i = 0; i < ls.size(); i++) Gfx.text(ls.get(i), x + 18, y + 14 + i * 26, 1, 1, 1);
        int by = y + 24 + ls.size() * 26;
        for (int i = 0; i < rows; i++) {
            if (button(n.labels()[i], x + 18, by + i * 56, bw - 36, 46, 0.2f, 0.25f, 0.4f)) {
                String tg = n.targets()[i];
                if ("END".equals(tg)) talking = false; else node = tg;
                break;
            }
        }
    }
}
