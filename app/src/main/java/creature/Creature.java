package creature;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import world.Build;
import world.Chunk;
import world.Mesh;
import world.World;

public final class Creature {
    public static final int WANDER = 0, IGNORE = 1, FOLLOW = 2, FLEE = 3, GROUP = 4, GATHER = 5, BUILD = 6, DESTROY = 7, DANCE = 8;
    public static final String[] STATE_NAMES = {"wandering", "ignoring you", "following you", "fleeing", "gathering with others",
            "collecting materials", "building", "destroying", "dancing"};
    private static final int BIPED = 0, QUAD = 1, SERPENT = 2, JELLY = 3, SPIDER = 4, BIRD = 5, STALKER = 6, LUMP = 7;
    private static final String[] SPECIES = {"walker", "strider", "coil", "drifter", "skitter", "flapper", "reacher", "lump"};

    /** Services the world gives to creatures (implemented by the game). */
    public interface Hooks {
        Chunk.Obj nearestObj(float x, float z, int kind, float range);
        void destroyObj(Chunk.Obj o);
        void addBuild(Build b);
        void removeBuild(Build b);
        /** Nearest build within range whose owner is not {@code exclude} (null = any owner). */
        Build nearestBuild(float x, float z, float range, Object exclude);
    }

    public static final class Env {
        public float light, trees, dream, px, pz, gx, gz;
        public int sleep, neighbors;
        public Hooks hooks;
    }

    // ---- body parts -------------------------------------------------------------------------------------------
    private static final int P_BODY = 0, P_LEG = 1, P_WING = 2, P_CHAIN = 3, P_SPIKE = 4, P_ORBIT = 5;
    private static final class Part {
        int kind, n; float ax, ay, az, ex, ey, ez, r0, r1, ph, amp, side; float[] col; boolean eye;
    }
    private final List<Part> parts = new ArrayList<>();
    private static final float[] WHITE = {1, 1, 1};

    public final long seed; public final float size, speed;
    public final String name, species;
    public final Mind mind;
    public float x, y, z, yaw, sayT; public int state; public String say = ""; public boolean frozen;
    public float fear, curio, aggr, social;
    public float bf, bc, ba, bs;
    public int wood, stone, crystal, built;
    private final int plan;
    private float top, timer, wander, phase, gait, gaitGoal, work, taskCool, senseT, homeX, homeZ, goalX, goalZ;
    private final Random talk, ai;
    private final String[] syl; private final float[][] trans;
    private final float[][] pal;
    private boolean moving;
    private Chunk.Obj targetObj; private Build targetBuild, myBuild; private int buildKind, gatherKind;
    private final Mind.Ctx ctx = new Mind.Ctx();
    private float cs, sn;

    public Creature(long seed, float[][] pal) {
        this.seed = seed; this.pal = pal;
        Random g = new Random(seed);
        size = 0.6f + g.nextFloat() * 1.6f;
        speed = 1.5f + g.nextFloat() * 3f;
        bf = g.nextFloat(); bc = g.nextFloat(); ba = g.nextFloat(); bs = g.nextFloat();
        fear = bf; curio = bc; aggr = ba; social = bs;
        phase = (seed & 1023) * 0.01f;
        plan = g.nextInt(8);
        species = SPECIES[plan];
        makeBody(g);

        // Markov vocabulary
        String cons = "ptkbdgmnslrvzh", vow = "aeiouy";
        int ns = 6 + g.nextInt(4);
        syl = new String[ns]; trans = new float[ns][ns];
        for (int i = 0; i < ns; i++) {
            syl[i] = "" + cons.charAt(g.nextInt(cons.length())) + vow.charAt(g.nextInt(vow.length())) + (g.nextInt(3) == 0 ? "" + cons.charAt(g.nextInt(cons.length())) : "");
            for (int j = 0; j < ns; j++) trans[i][j] = g.nextFloat() * g.nextFloat() + 0.02f;
        }
        talk = new Random(seed * 0x9E37L + 7); ai = new Random(seed + 99);
        timer = ai.nextFloat() * 2;
        Random nr = new Random(seed ^ 0xABCDEFL);
        String[] names = new String[Mind.N];
        for (int i = 0; i < names.length; i++) names[i] = buildWord(nr, nr.nextInt(ns), 2);
        String nm = buildWord(nr, nr.nextInt(ns), 2);
        name = Character.toUpperCase(nm.charAt(0)) + nm.substring(1);
        mind = new Mind(g, names);
        wood = g.nextInt(4); stone = g.nextInt(2); crystal = 0;
        taskCool = g.nextFloat() * 6f;
    }

    // ================================================================== body generation
    private float[] pick(Random g) {
        float[] pc = pal[g.nextInt(4)], c = new float[3];
        for (int i = 0; i < 3; i++) c[i] = Math.min(1f, pc[i] * (0.8f + 0.4f * g.nextFloat()));
        return c;
    }

    private Part add(int kind, float ax, float ay, float az, float ex, float ey, float ez, float[] col) {
        Part p = new Part();
        p.kind = kind; p.ax = ax; p.ay = ay; p.az = az; p.ex = ex; p.ey = ey; p.ez = ez; p.col = col;
        parts.add(p);
        if (kind == P_BODY) top = Math.max(top, ay + ey);
        else top = Math.max(top, Math.max(ay, ay + ey));
        return p;
    }

    private void leg(float ax, float ay, float az, float ex, float ey, float ez, float r0, float r1, float ph, float[] col) {
        Part p = add(P_LEG, ax, ay, az, ex, ey, ez, col);
        p.r0 = r0; p.r1 = r1; p.ph = ph;
    }

    private void chain(Random g, float ax, float ay, float az, float ex, float ey, float ez, int n, float r0, float r1, float amp, float[] col) {
        Part p = new Part();
        p.kind = P_CHAIN; p.n = n; p.ax = ax; p.ay = ay; p.az = az; p.ex = ex; p.ey = ey; p.ez = ez; p.r0 = r0; p.r1 = r1; p.amp = amp; p.col = col;
        p.ph = g.nextFloat() * 6.28f;
        parts.add(p);
        top = Math.max(top, Math.max(ay, ay + ey * n));
    }

    private void spike(float ax, float ay, float az, float ex, float ey, float ez, float r, float[] col) {
        Part p = add(P_SPIKE, ax, ay, az, ex, ey, ez, col);
        p.r0 = r;
    }

    private void eyes(Random g, int n, float hx, float hy, float hz, float hr) {
        float er = hr * (0.16f + 0.1f * g.nextFloat());
        for (int k = 0; k < n; k++) {
            float off = (k - (n - 1) / 2f) * hr * (n > 2 ? 0.45f : 0.7f);
            float row = (n > 3 && k % 2 == 1) ? hr * 0.3f : 0f;
            Part e = add(P_BODY, hx + off, hy + hr * 0.25f + row, hz + hr * 0.78f, er, er, er, WHITE);
            e.eye = true;
        }
    }

    private void horns(Random g, float hx, float hy, float hz, float hr, float[] col) {
        if (g.nextInt(2) == 0) return;
        float len = hr * (0.8f + g.nextFloat() * 1.4f), lean = g.nextFloat() * 0.8f;
        for (int s = -1; s <= 1; s += 2) spike(hx + s * hr * 0.5f, hy + hr * 0.8f, hz, s * hr * lean, len, -hr * 0.2f * g.nextFloat(), hr * 0.22f, col);
    }

    private void makeBody(Random g) {
        float s = size;
        float[] c1 = pick(g), c2 = pick(g), c3 = pick(g);
        switch (plan) {
            case BIPED -> {
                float tr = s * (0.22f + 0.12f * g.nextFloat()), th = s * (0.35f + 0.2f * g.nextFloat()), legL = s * (0.4f + 0.3f * g.nextFloat());
                float torsoY = legL + th * 0.8f;
                add(P_BODY, 0, torsoY, 0, tr, th, tr * 0.8f, c1);
                float hr = s * (0.15f + 0.15f * g.nextFloat()), hy = torsoY + th + hr * 0.6f;
                add(P_BODY, 0, hy, 0.03f * s, hr, hr * (0.8f + 0.4f * g.nextFloat()), hr, c2);
                eyes(g, 1 + g.nextInt(3), 0, hy, 0.03f * s, hr);
                horns(g, 0, hy, 0, hr, c3);
                for (int side = -1; side <= 1; side += 2) {
                    leg(side * tr * 0.6f, legL, 0, 0, -legL, 0, s * 0.07f, s * 0.05f, side > 0 ? 0f : 3.14f, c2);
                    leg(side * tr * 1.05f, torsoY + th * 0.6f, 0, side * s * 0.12f, -s * 0.4f, 0, s * 0.05f, s * 0.04f, side > 0 ? 3.14f : 0f, c2);
                }
                if (g.nextInt(2) == 0) chain(g, 0, legL + th * 0.3f, -tr * 0.8f, 0, -s * 0.03f, -s * 0.16f, 4, s * 0.07f, s * 0.015f, s * 0.12f, c3);
            }
            case QUAD -> {
                float bl = s * (0.4f + 0.25f * g.nextFloat()), bh = s * (0.2f + 0.15f * g.nextFloat()), bw = s * (0.18f + 0.12f * g.nextFloat());
                float legL = s * (0.35f + 0.3f * g.nextFloat()), by = legL + bh * 0.6f;
                add(P_BODY, 0, by, 0, bw, bh, bl, c1);
                float hr = s * (0.15f + 0.12f * g.nextFloat()), hz = bl * 0.9f + hr * 0.6f, hy = by + bh * (0.4f + g.nextFloat() * 0.8f);
                add(P_BODY, 0, hy, hz, hr * 0.9f, hr, hr * 1.1f, c2);
                eyes(g, 1 + g.nextInt(3), 0, hy, hz, hr);
                horns(g, 0, hy, hz, hr, c3);
                for (int fz = -1; fz <= 1; fz += 2) for (int side = -1; side <= 1; side += 2)
                    leg(side * bw * 0.8f, legL, fz * bl * 0.6f, 0, -legL, 0, s * 0.07f, s * 0.05f, (fz > 0) == (side > 0) ? 0f : 3.14f, c2);
                chain(g, 0, by, -bl, 0, s * 0.02f, -s * 0.18f, 3 + g.nextInt(3), s * 0.06f, s * 0.015f, s * 0.1f, c3);
            }
            case SERPENT -> {
                int n = 7 + g.nextInt(4);
                float seg = s * (0.28f + 0.1f * g.nextFloat()), r0 = s * (0.2f + 0.1f * g.nextFloat());
                float baseY = r0 * 0.9f + (g.nextInt(3) == 0 ? s * 0.5f : 0f);
                add(P_BODY, 0, baseY + r0 * 0.2f, s * 0.1f, r0 * 1.05f, r0, r0 * 1.3f, c2);
                eyes(g, 1 + g.nextInt(3), 0, baseY + r0 * 0.2f, s * 0.1f, r0);
                horns(g, 0, baseY + r0 * 0.2f, s * 0.1f, r0, c3);
                chain(g, 0, baseY, 0, 0, 0, -seg, n, r0, r0 * 0.12f, s * 0.35f, c1);
            }
            case JELLY -> {
                float br = s * (0.35f + 0.2f * g.nextFloat()), cy = s * (0.9f + 0.3f * g.nextFloat());
                add(P_BODY, 0, cy, 0, br, br * (0.6f + 0.5f * g.nextFloat()), br, c1);
                add(P_BODY, 0, cy + br * 0.35f, 0, br * 0.7f, br * 0.6f, br * 0.7f, c2);
                eyes(g, 1 + g.nextInt(4), 0, cy, 0, br);
                int nt = 3 + g.nextInt(4);
                for (int k = 0; k < nt; k++) {
                    double a = 2 * Math.PI * k / nt;
                    chain(g, (float) Math.cos(a) * br * 0.6f, cy - br * 0.3f, (float) Math.sin(a) * br * 0.6f, 0, -s * 0.22f, 0,
                            4 + g.nextInt(3), s * 0.06f, s * 0.01f, s * 0.2f, c2);
                }
                int no = g.nextInt(3);
                for (int k = 0; k < no; k++) {
                    Part o = add(P_ORBIT, 0, cy + br * 0.2f, 0, br * 1.8f, br * 0.4f, 0, c3);
                    o.r0 = s * 0.09f; o.ph = k * 2.1f;
                }
            }
            case SPIDER -> {
                float br = s * (0.25f + 0.15f * g.nextFloat()), legL = s * (0.5f + 0.4f * g.nextFloat());
                float by = legL * 0.7f;
                add(P_BODY, 0, by, 0, br, br * 0.55f, br * 1.2f, c1);
                add(P_BODY, 0, by + br * 0.1f, br * 1.1f, br * 0.55f, br * 0.5f, br * 0.55f, c2);
                eyes(g, 2 + g.nextInt(3), 0, by + br * 0.1f, br * 1.1f, br * 0.55f);
                add(P_BODY, 0, by + br * 0.05f, -br * 1.3f, br * 0.8f, br * 0.7f, br, g.nextInt(2) == 0 ? c1 : c3);
                int pairs = 3 + g.nextInt(2);
                for (int k = 0; k < pairs; k++) {
                    float lz = (k - (pairs - 1) / 2f) * br * 0.7f;
                    for (int side = -1; side <= 1; side += 2)
                        leg(side * br * 0.8f, by, lz, side * legL * 0.8f, -by, lz * 0.8f, s * 0.04f, s * 0.025f, ((k + (side > 0 ? 1 : 0)) % 2) * 3.14f, c2);
                }
            }
            case BIRD -> {
                float bl = s * (0.28f + 0.1f * g.nextFloat()), bh = s * 0.2f, legL = s * (0.3f + 0.25f * g.nextFloat()), by = legL + bh;
                add(P_BODY, 0, by, 0, bh, bh * 1.1f, bl, c1);
                float hr = s * 0.14f;
                add(P_BODY, 0, by + bh * 0.9f, bl * 0.9f, hr, hr, hr, c2);
                eyes(g, 1 + g.nextInt(2), 0, by + bh * 0.9f, bl * 0.9f, hr);
                spike(0, by + bh * 0.85f, bl * 0.9f + hr * 0.8f, 0, 0, s * 0.22f, hr * 0.45f, c3);
                for (int side = -1; side <= 1; side += 2) {
                    leg(side * bh * 0.5f, legL, 0, 0, -legL, 0, s * 0.04f, s * 0.025f, side > 0 ? 0f : 3.14f, c3);
                    Part w = add(P_WING, side * bh * 0.8f, by + bh * 0.4f, 0, side * s * (0.7f + 0.4f * g.nextFloat()), s * 0.1f, 0, c2);
                    w.amp = 0.8f; w.side = side; w.r0 = s * 0.05f;
                }
                for (int k = 0; k < 3; k++) spike(0, by, -bl, (k - 1) * s * 0.12f, s * 0.04f, -s * 0.35f, s * 0.05f, c3);
            }
            case STALKER -> {
                float legL = s * (0.9f + 0.5f * g.nextFloat()), bh = s * 0.2f, bw = s * 0.2f, by = legL + bh;
                add(P_BODY, 0, by, 0, bw, bh, bw * 1.4f, c1);
                for (int fz = -1; fz <= 1; fz += 2) for (int side = -1; side <= 1; side += 2)
                    if (g.nextInt(3) != 0 || fz > 0) leg(side * bw * 0.6f, legL, fz * bw * 0.8f, 0, -legL, 0, s * 0.04f, s * 0.025f, (fz > 0) == (side > 0) ? 0f : 3.14f, c2);
                int n = 3 + g.nextInt(3);
                float ny = by + bh * 0.6f, nz = bw * 0.9f, stepY = s * 0.3f, stepZ = s * 0.04f;
                chain(g, 0, ny, nz, 0, stepY, stepZ, n, s * 0.07f, s * 0.05f, 0f, c1);
                float hr = s * (0.12f + 0.08f * g.nextFloat()), hy = ny + stepY * n, hz = nz + stepZ * n;
                add(P_BODY, 0, hy, hz, hr, hr, hr * 1.3f, c2);
                eyes(g, 1 + g.nextInt(2), 0, hy, hz, hr);
                horns(g, 0, hy, hz, hr, c3);
            }
            default -> {
                int nb = 3 + g.nextInt(3);
                float R = s * (0.35f + 0.2f * g.nextFloat());
                for (int k = 0; k < nb; k++)
                    add(P_BODY, (g.nextFloat() - 0.5f) * R * 1.2f, R * (0.5f + g.nextFloat()), (g.nextFloat() - 0.5f) * R * 1.2f,
                            R * (0.5f + 0.4f * g.nextFloat()), R * (0.45f + 0.4f * g.nextFloat()), R * (0.5f + 0.4f * g.nextFloat()), k % 2 == 0 ? c1 : c2);
                eyes(g, 2 + g.nextInt(4), 0, R * 1.0f, R * 0.1f, R * 0.9f);
                int nl = g.nextInt(4);
                for (int k = 0; k < nl; k++) {
                    double a = 2 * Math.PI * k / nl;
                    leg((float) Math.cos(a) * R * 0.5f, R * 0.5f, (float) Math.sin(a) * R * 0.5f, 0, -R * 0.5f, 0, R * 0.12f, R * 0.09f, (k % 2) * 3.14f, c2);
                }
                int sp = 2 + g.nextInt(5);
                for (int k = 0; k < sp; k++)
                    spike((g.nextFloat() - 0.5f) * R, R * 1.5f, (g.nextFloat() - 0.5f) * R, (g.nextFloat() - 0.5f) * R * 0.6f, R * (0.4f + 0.5f * g.nextFloat()), (g.nextFloat() - 0.5f) * R * 0.6f, R * 0.12f, c3);
            }
        }
    }

    // ================================================================== language
    private String buildWord(Random rr, int s, int len) {
        StringBuilder sb = new StringBuilder(syl[s]);
        for (int k = 1; k < len; k++) {
            float tot = 0; for (float v : trans[s]) tot += v;
            float r = rr.nextFloat() * tot; int j = 0;
            while (j < syl.length - 1 && (r -= trans[s][j]) > 0) j++;
            s = j; sb.append(syl[s]);
        }
        return sb.toString();
    }

    private String word() {
        int len = 2 + talk.nextInt(2), s = talk.nextInt(syl.length);
        return buildWord(talk, s, len);
    }

    private void speak() {
        StringBuilder sb = new StringBuilder();
        int w = 1 + talk.nextInt(3);
        for (int i = 0; i < w; i++) { if (i > 0) sb.append(' '); sb.append(word()); }
        sb.append(fear > 0.65f ? "!" : aggr > 0.65f ? "!!" : curio > 0.6f ? "?" : "...");
        say = sb.toString(); sayT = 3.5f;
        ctx.spoke = true;
    }

    private static float cl(float v) { return Math.max(0f, Math.min(1f, v)); }

    // ================================================================== editing helpers (mind editor)
    public void nudge(int trait, float d) {
        switch (trait) {
            case 0 -> { bf = cl(bf + d); fear = cl(fear + d); }
            case 1 -> { bc = cl(bc + d); curio = cl(curio + d); }
            case 2 -> { ba = cl(ba + d); aggr = cl(aggr + d); }
            default -> { bs = cl(bs + d); social = cl(social + d); }
        }
        timer = 0;
    }

    public void gift(int kind) {
        if (kind == 0) wood++; else if (kind == 1) stone++; else crystal++;
        mind.push("Someone left me " + (kind == 0 ? "a piece of wood" : kind == 1 ? "a stone" : "a crystal") + ". A gift?");
        taskCool = 0; timer = 0;
    }

    public String activity() {
        return switch (state) {
            case BUILD -> myBuild != null ? "building a " + Build.NAMES[myBuild.kind] : "heading to a building site";
            case GATHER -> "collecting " + (gatherKind == Chunk.TREE ? "wood" : gatherKind == Chunk.ROCK ? "stone" : "crystals");
            default -> STATE_NAMES[state];
        };
    }

    // ================================================================== simulation
    private void sense(World w, Env e) {
        float d = 4f;
        ctx.water = w.height(x + d, z) < w.waterY || w.height(x - d, z) < w.waterY || w.height(x, z + d) < w.waterY || w.height(x, z - d) < w.waterY;
        ctx.trees = e.trees > 0.25f;
        if (e.hooks != null) {
            ctx.crystals = e.hooks.nearestObj(x, z, Chunk.CRYSTAL, 10f) != null;
            ctx.builds = e.hooks.nearestBuild(x, z, 14f, null) != null;
        }
    }

    public void update(float dt, World w, Env e) {
        if (frozen) return;
        float dx = e.px - x, dz = e.pz - z, dist = (float) Math.sqrt(dx * dx + dz * dz);
        float[] kn = mind.know;
        int bel = mind.dreamerBelief;
        float tf = bf + (1 - e.light) * 0.3f * (kn[Mind.DARK] > 0.5f ? 0.4f : 1.2f) + e.trees * 0.2f + (e.sleep == 2 ? 0.2f : 0f) + e.dream * 0.1f
                - e.neighbors * 0.03f + (bel == 2 ? 0.25f : bel == 1 ? -0.2f : bel == 3 ? -0.05f : 0f) + (kn[Mind.DREAMER] < 0.3f ? 0.12f : 0f)
                + (e.sleep > 0 && kn[Mind.SLEEP] < 0.3f ? 0.12f : 0f);
        float tc = bc + e.light * 0.2f + (e.sleep == 3 ? 0.3f : 0f) - (e.sleep == 2 ? 0.15f : 0f) + (bel == 3 || bel == 4 ? 0.2f : 0f);
        float ta = ba + (e.sleep == 3 ? 0.15f : 0f) + (1 - e.light) * 0.15f + (bel == 4 ? 0.1f : 0f);
        float ts = bs * (0.4f + 0.6f * kn[Mind.OTHERS]) + Math.min(e.neighbors, 4) * 0.1f;
        float k = Math.min(1, dt * 0.5f);
        fear += (cl(tf) - fear) * k; curio += (cl(tc) - curio) * k; aggr += (cl(ta) - aggr) * k; social += (cl(ts) - social) * k;
        timer -= dt; sayT -= dt; taskCool -= dt; senseT -= dt;
        if (senseT <= 0) { senseT = 0.8f + ai.nextFloat() * 0.4f; sense(w, e); }

        ctx.light = e.light; ctx.dreamerDist = dist; ctx.fear = fear; ctx.curio = curio; ctx.aggr = aggr; ctx.social = social;
        ctx.neighbors = e.neighbors; ctx.sleep = e.sleep; ctx.state = state; ctx.wood = wood; ctx.stone = stone; ctx.crystal = crystal;
        mind.update(dt, ctx);

        if (timer <= 0 || (dist < 4 && state != FLEE && fear > 0.5f)) { cancelTask(); decide(dist, e, w); }
        if (dist < 14 && sayT <= 0 && ai.nextFloat() < dt * 0.15f * (0.3f + 0.7f * kn[Mind.SPEECH])) speak();

        moving = false;
        float tx = x, tz = z, sp = 0;
        switch (state) {
            case FOLLOW -> { if (dist > 3) { tx = e.px; tz = e.pz; sp = speed; } }
            case FLEE -> { tx = x - dx; tz = z - dz; sp = speed * 1.6f; }
            case GROUP -> { if (e.neighbors > 0) { tx = e.gx; tz = e.gz; sp = speed * 0.7f; } }
            case WANDER -> { tx = x + (float) Math.sin(wander); tz = z - (float) Math.cos(wander); sp = speed * 0.4f; }
            case GATHER -> {
                if (targetObj == null || w.removed.contains(targetObj.id())) endTask(0f);
                else {
                    float ox = targetObj.x() - x, oz = targetObj.z() - z, od = (float) Math.sqrt(ox * ox + oz * oz);
                    if (od > 1.8f) { tx = targetObj.x(); tz = targetObj.z(); sp = speed * 0.9f; }
                    else {
                        facing(ox, oz); work += dt;
                        if (work > 1.8f) collect(e);
                    }
                }
            }
            case BUILD -> {
                float ox = goalX - x, oz = goalZ - z, od = (float) Math.sqrt(ox * ox + oz * oz);
                if (myBuild == null) {
                    if (od > 1.5f) { tx = goalX; tz = goalZ; sp = speed * 0.9f; }
                    else if (e.hooks != null) startBuilding(w, e);
                    else endTask(5f);
                } else {
                    myBuild.progress += dt / myBuild.duration();
                    yaw += dt * 1.5f;
                    if (myBuild.progress >= 1f) {
                        myBuild.progress = 1f; built++;
                        mind.push("It is finished. My own " + Build.NAMES[myBuild.kind] + ".");
                        mind.know[Mind.BUILD] = Math.max(mind.know[Mind.BUILD], 0.8f);
                        myBuild = null;
                        endTask(8f + ai.nextFloat() * 10f);
                    }
                }
            }
            case DESTROY -> {
                if (targetBuild != null) {
                    if (e.hooks.nearestBuild(targetBuild.x, targetBuild.z, 0.5f, null) != targetBuild) endTask(6f);
                    else {
                        float ox = targetBuild.x - x, oz = targetBuild.z - z, od = (float) Math.sqrt(ox * ox + oz * oz);
                        if (od > 3.2f) { tx = targetBuild.x; tz = targetBuild.z; sp = speed * 1.1f; }
                        else {
                            facing(ox, oz); work += dt;
                            if (work > 2.2f) {
                                e.hooks.removeBuild(targetBuild);
                                if (targetBuild.owner instanceof Creature o && o != this) {
                                    o.mind.push("Someone destroyed my " + Build.NAMES[targetBuild.kind] + "!");
                                    o.aggr = cl(o.aggr + 0.25f); o.fear = cl(o.fear + 0.15f);
                                }
                                mind.push("It fell. Quiet, now.");
                                endTask(10f + ai.nextFloat() * 10f);
                            }
                        }
                    }
                } else if (targetObj != null) {
                    if (w.removed.contains(targetObj.id())) endTask(4f);
                    else {
                        float ox = targetObj.x() - x, oz = targetObj.z() - z, od = (float) Math.sqrt(ox * ox + oz * oz);
                        if (od > 1.8f) { tx = targetObj.x(); tz = targetObj.z(); sp = speed * 1.1f; }
                        else {
                            facing(ox, oz); work += dt;
                            if (work > 1.4f) {
                                e.hooks.destroyObj(targetObj);
                                mind.push("Crack. It is gone. Why do I feel lighter?");
                                endTask(8f + ai.nextFloat() * 8f);
                            }
                        }
                    }
                } else endTask(0f);
            }
            case DANCE -> yaw += dt * 3.2f;
            default -> { }
        }
        if (sp > 0) {
            float ang = (float) Math.atan2(tx - x, -(tz - z));
            stepMove(ang, sp, dt, w);
        }
        gaitGoal = moving ? 1f : (state == DANCE ? 0.6f : 0f);
        gait += (gaitGoal - gait) * Math.min(1f, dt * 6f);
        y = Math.max(w.height(x, z), w.waterY);
    }

    private void facing(float ox, float oz) { yaw = (float) Math.atan2(ox, -oz); }

    private void stepMove(float ang, float sp, float dt, World w) {
        float nx = x + (float) (Math.sin(ang) * sp * dt), nz = z - (float) (Math.cos(ang) * sp * dt);
        if (mind.know[Mind.WATER] >= 0.5f && w.height(nx, nz) < w.waterY - 0.05f && w.height(x, z) >= w.waterY - 0.05f) {
            wander += 1.7f;                       // it refuses to wade in: turns aside
            if (timer > 0.5f && state == WANDER) timer = 0.4f;
            return;
        }
        yaw = ang; x = nx; z = nz; moving = true;
    }

    private void cancelTask() {
        targetObj = null; targetBuild = null; work = 0f;
        if (myBuild != null && myBuild.progress < 1f) myBuild = null;   // abandons the site; the half-built shell stays behind
    }

    private void endTask(float cooldown) {
        cancelTask();
        state = WANDER; timer = 0f; taskCool = Math.max(taskCool, cooldown);
    }

    private void collect(Env e) {
        if (targetObj == null || e.hooks == null) { endTask(0f); return; }
        e.hooks.destroyObj(targetObj);
        String what;
        switch (targetObj.kind()) {
            case Chunk.TREE -> { wood++; what = "wood"; }
            case Chunk.ROCK -> { stone++; what = "a stone"; }
            default -> { crystal++; what = "a crystal"; }
        }
        mind.push("Got " + what + ". I carry " + wood + " wood, " + stone + " stone, " + crystal + " crystal.");
        endTask(0f);
        taskCool = 3f + ai.nextFloat() * 5f;     // pause between harvests so forests are not stripped instantly
    }

    private int pickBuild() {
        if (crystal >= 1 && stone >= 1) return Build.MONUMENT;
        if (wood >= 3 && stone >= 1) return Build.TOWER;
        if (wood >= 2) return Build.HUT;
        if (stone >= 2) return Build.WALL;
        return -1;
    }

    private void startBuilding(World w, Env e) {
        int kind = buildKind;
        switch (kind) {
            case Build.MONUMENT -> { crystal--; stone--; }
            case Build.TOWER -> { wood -= 3; stone--; }
            case Build.HUT -> wood -= 2;
            default -> stone -= 2;
        }
        float[] a = pal[(int) (seed & 3)], b = pal[(int) ((seed >> 2) & 3)];
        float[] c1 = {Math.min(1f, a[0] * 0.9f + 0.1f), Math.min(1f, a[1] * 0.9f + 0.1f), Math.min(1f, a[2] * 0.9f + 0.1f)};
        float[] c2 = {Math.min(1f, b[0] * 1.1f), Math.min(1f, b[1] * 1.1f), Math.min(1f, b[2] * 1.1f)};
        myBuild = new Build(kind, goalX, w.height(goalX, goalZ), goalZ, ai.nextFloat() * 3f, c1, c2, this);
        e.hooks.addBuild(myBuild);
        mind.push("Here. This is where my " + Build.NAMES[kind] + " will stand.");
    }

    private void decide(float dist, Env e, World w) {
        float[] kn = mind.know;
        int bel = mind.dreamerBelief;
        float[] s = new float[9];
        s[FLEE] = fear * (dist < 10 ? 1.3f : 0.2f);
        s[FOLLOW] = curio * (dist < 30 ? 1f : 0.2f) + aggr * (dist < 15 ? 0.6f : 0f) - fear * 0.5f + (bel == 3 || bel == 5 ? 0.3f : 0f);
        s[GROUP] = e.neighbors > 0 ? social * 1.1f : 0f;
        s[IGNORE] = 0.35f + (1 - curio) * 0.25f;
        s[WANDER] = 0.4f;
        boolean canBuild = kn[Mind.BUILD] >= 0.5f && e.hooks != null && taskCool <= 0;
        if (canBuild) {
            boolean afford = pickBuild() >= 0;
            s[BUILD] = afford ? 0.95f : 0f;
            s[GATHER] = afford ? 0.2f : 0.85f;
        }
        if (kn[Mind.DESTROY] >= 0.5f && e.hooks != null && taskCool <= 0 && aggr > 0.45f) s[DESTROY] = aggr * 0.9f;
        if (kn[Mind.MUSIC] >= 0.5f) s[DANCE] = 0.25f + (e.sleep == 3 ? 0.5f : 0f) + curio * 0.2f;
        int b = 0; float bv = -9;
        for (int i = 0; i < s.length; i++) { float v = s[i] + ai.nextFloat() * 0.25f; if (v > bv) { bv = v; b = i; } }
        timer = 1.5f + ai.nextFloat() * 2f; wander = ai.nextFloat() * 6.2832f;
        if (b == GATHER && !startGather(e)) b = WANDER;
        else if (b == BUILD && !startBuild(w, e)) b = WANDER;
        else if (b == DESTROY && !startDestroy(e)) b = WANDER;
        else if (b == DANCE) timer = 3f + ai.nextFloat() * 3f;
        if (b != state) { state = b; speak(); }
    }

    private boolean startGather(Env e) {
        int[] order = wood < 3 ? new int[]{Chunk.TREE, Chunk.ROCK, Chunk.CRYSTAL}
                : stone < 1 ? new int[]{Chunk.ROCK, Chunk.CRYSTAL, Chunk.TREE} : new int[]{Chunk.CRYSTAL, Chunk.TREE, Chunk.ROCK};
        for (int kind : order) {
            if (kind == Chunk.TREE && mind.know[Mind.TREES] < 0.5f) continue;
            if (kind == Chunk.CRYSTAL && mind.know[Mind.CRYSTALS] < 0.5f) continue;
            Chunk.Obj o = e.hooks.nearestObj(x, z, kind, 36f);
            if (o != null) { targetObj = o; gatherKind = kind; work = 0f; timer = 14f; return true; }
        }
        taskCool = 4f;
        return false;
    }

    private boolean startBuild(World w, Env e) {
        buildKind = pickBuild();
        if (buildKind < 0) return false;
        if (homeX == 0 && homeZ == 0) { homeX = x; homeZ = z; }
        for (int t = 0; t < 8; t++) {
            float a = ai.nextFloat() * 6.2832f, r = 3.5f + ai.nextFloat() * 9f;
            float sx = homeX + (float) Math.cos(a) * r, sz = homeZ + (float) Math.sin(a) * r;
            if (w.height(sx, sz) > w.waterY + 0.6f && e.hooks.nearestBuild(sx, sz, 6f, null) == null) {
                goalX = sx; goalZ = sz; myBuild = null; work = 0f; timer = 30f; return true;
            }
        }
        taskCool = 5f;
        return false;
    }

    private boolean startDestroy(Env e) {
        Build b = e.hooks.nearestBuild(x, z, 30f, this);
        if (b != null && ai.nextInt(3) != 0) { targetBuild = b; work = 0f; timer = 14f; return true; }
        Chunk.Obj o = e.hooks.nearestObj(x, z, Chunk.TREE, 25f);
        if (o != null) { targetObj = o; work = 0f; timer = 12f; return true; }
        taskCool = 5f;
        return false;
    }

    public float topY() { return y + top + 0.5f; }

    // ================================================================== rendering
    private float wx(float lx, float lz) { return x + lx * cs + lz * sn; }
    private float wz(float lx, float lz) { return z + lx * sn - lz * cs; }

    public void build(Mesh m, float t) { build(m, t, 0); }

    public void build(Mesh m, float t, int sl) {
        cs = (float) Math.cos(yaw); sn = (float) Math.sin(yaw);
        float hover = plan == JELLY ? size * (0.5f + 0.15f * (float) Math.sin(t * 1.6f + phase))
                : plan == BIRD ? size * 0.12f * (1f + (float) Math.sin(t * 2f + phase)) : 0f;
        float stretch = 1f, pulse = 1f;
        if (sl == 1) hover += size * 0.6f * (0.5f + 0.5f * (float) Math.sin(t * 0.8f + phase));      // light sleep: drifting
        else if (sl == 2) stretch = 1f + 0.6f * (float) Math.sin(t * 0.5f + phase);                    // deep sleep: stretching
        else if (sl == 3) pulse = 1f + 0.25f * (float) Math.sin(t * 4f + phase);                       // REM: throbbing
        float walk = t * (2.5f + speed * 1.2f) + phase;
        float bob = (plan == BIPED || plan == BIRD) ? Math.abs((float) Math.sin(walk)) * size * 0.08f * gait
                : plan == LUMP ? Math.abs((float) Math.sin(walk * 0.7f)) * size * 0.3f * gait : 0f;
        float oy = y + hover + bob;
        for (Part p : parts) {
            switch (p.kind) {
                case P_BODY -> m.spheroid(wx(p.ax, p.az), oy + p.ay * stretch, wz(p.ax, p.az), p.ex * pulse, p.ey * pulse * stretch, p.ez * pulse, yaw,
                        p.eye ? 4 : 6, p.eye ? 2 : 3, p.col, p.eye ? 1.25f : 1f);
                case P_LEG -> {
                    float a = (float) Math.sin(walk + p.ph) * gait * 0.7f, ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                    float ey = p.ey * stretch;
                    float dx = p.ex, dy = ey * ca - p.ez * sa, dz = ey * sa + p.ez * ca;
                    m.tube(wx(p.ax, p.az), oy + p.ay * stretch, wz(p.ax, p.az), wx(p.ax + dx, p.az + dz), oy + p.ay * stretch + dy, wz(p.ax + dx, p.az + dz),
                            p.r0, p.r1, 4, p.col, 1f);
                }
                case P_WING -> {
                    float fl = (float) Math.sin(t * (6f + gait * 5f) + p.ph) * p.amp * (plan == BIRD ? 1f : 0.3f), aa = fl * p.side;
                    float dx = p.ex * (float) Math.cos(aa) - p.ey * (float) Math.sin(aa), dy = p.ex * (float) Math.sin(aa) + p.ey * (float) Math.cos(aa);
                    float ax = wx(p.ax, p.az), ay = oy + p.ay * stretch, az = wz(p.ax, p.az);
                    float bx = wx(p.ax + dx, p.az), by = ay + dy, bz = wz(p.ax + dx, p.az);
                    float cxw = wx(p.ax + dx * 0.2f, p.az - Math.abs(p.ex) * 0.7f), czw = wz(p.ax + dx * 0.2f, p.az - Math.abs(p.ex) * 0.7f);
                    m.tube(ax, ay, az, bx, by, bz, p.r0, p.r0 * 0.4f, 4, p.col, 1f);
                    m.tri(ax, ay, az, bx, by, bz, cxw, ay + dy * 0.2f, czw, p.col, 1.1f);
                }
                case P_CHAIN -> {
                    float amp = p.amp * (0.5f + 0.5f * gait);
                    float qx = p.ax, qy = p.ay * stretch, qz = p.az;
                    for (int i = 0; i < p.n; i++) {
                        float f0 = (float) i / p.n, f1 = (float) (i + 1) / p.n;
                        float nx = p.ax + p.ex * (i + 1) + (float) Math.sin(t * 1.8f + p.ph + (i + 1) * 0.8f) * amp * f1;
                        float ny = (p.ay + p.ey * (i + 1)) * stretch + (float) Math.cos(t * 1.3f + p.ph + (i + 1) * 0.7f) * amp * 0.5f * f1;
                        float nz = p.az + p.ez * (i + 1);
                        m.tube(wx(qx, qz), oy + qy, wz(qx, qz), wx(nx, nz), oy + ny, wz(nx, nz),
                                p.r0 + (p.r1 - p.r0) * f0, p.r0 + (p.r1 - p.r0) * f1, 5, p.col, 1f);
                        qx = nx; qy = ny; qz = nz;
                    }
                }
                case P_SPIKE -> m.tube(wx(p.ax, p.az), oy + p.ay * stretch, wz(p.ax, p.az), wx(p.ax + p.ex, p.az + p.ez), oy + (p.ay + p.ey) * stretch,
                        wz(p.ax + p.ex, p.az + p.ez), p.r0, 0.01f, 4, p.col, 1.1f);
                default -> {
                    float lx = p.ax + (float) Math.cos(t * 1.5f + p.ph) * p.ex, lz = p.az + (float) Math.sin(t * 1.5f + p.ph) * p.ex;
                    m.spheroid(wx(lx, lz), oy + (p.ay + (float) Math.sin(t * 2f + p.ph) * p.ey) * stretch, wz(lx, lz), p.r0, p.r0, p.r0, yaw, 5, 2, p.col, 1.3f);
                }
            }
        }
    }
}
