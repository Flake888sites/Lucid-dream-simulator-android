package world;

import image.Features;
import java.security.MessageDigest;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class World {
    public static final int CS = 16;
    public static final float CELL = 2f;
    private static final float[] WATER = {0.1f, 0.3f, 0.6f};
    private static final float[] GREEN = {0.35f, 0.62f, 0.28f};
    public final Features f;
    public final float amp, waterY, pTree, pRock, pCrystal, pStruct;
    /** Objects (trees, rocks...) that creatures removed; chunk generation skips them. */
    public final Set<Long> removed = ConcurrentHashMap.newKeySet();
    /** Trees and grass multiplier (1 = default). Changing it needs the chunks regenerated. */
    public volatile float vegetation = 1f;
    private final Noise n1, n2, n3, n4;
    private final int oct;

    public World(Features f) {
        this.f = f;
        n1 = new Noise(f.seed);
        n2 = new Noise(f.seed * 31 + 7);
        n3 = new Noise(f.seed * 131 + 11);   // biome map: plains vs highlands
        n4 = new Noise(f.seed * 977 + 5);    // groves inside the plains
        amp = 4 + f.brightness * 26;                                  // brightness -> relief
        float br = Math.max(0f, Math.min(1f, f.brightness));
        waterY = amp * (-0.24f + 0.10f * (0.5f - br));                // only deep valleys fill: small lakes, not oceans
        oct = 3 + (int) (f.edge * 3);                                 // edges -> fine detail
        pTree = 0.02f + f.edge * 0.25f * (1 - f.brightness * 0.5f);
        pRock = 0.03f + f.edge * 0.05f;
        pCrystal = 0.01f + f.saturation * 0.08f + f.symmetry * 0.05f;
        pStruct = 0.004f + f.symmetry * 0.03f + f.edge * 0.02f;
    }

    private float fx(float x) { return f.symmetry > 0.8f ? Math.abs(x) : x; }   // symmetric image -> mirrored world

    private static float smooth(float a, float b, float t) {
        float u = Math.max(0f, Math.min(1f, (t - a) / (b - a)));
        return u * u * (3 - 2 * u);
    }

    /** 0 = open plains, 1 = rugged highlands. */
    public float ruggedness(float x, float z) {
        float b = n3.fbm(fx(x) * 0.0045f + 11.3f, z * 0.0045f - 7.1f, 2);
        return smooth(-0.05f - f.edge * 0.08f, 0.20f - f.edge * 0.08f, b);
    }

    /** Multiplier on tree probability: plains are almost bare, highlands wooded, groves dot the plains. */
    private float treeDensity(float x, float z, float rug) {
        float g = smooth(0.15f, 0.5f, n4.perlin(fx(x) * 0.02f + 3.1f, z * 0.02f + 9.7f));
        return 0.02f + 0.55f * rug + 0.7f * g;
    }

    public float height(float x, float z) {
        float xx = fx(x);
        float rug = ruggedness(x, z);
        float hill = n1.fbm(xx * 0.012f, z * 0.012f, oct) * amp + n2.perlin(xx * 0.05f, z * 0.05f) * amp * 0.15f * (f.edge + 0.2f);
        float plain = amp * 0.04f + n2.perlin(xx * 0.03f, z * 0.03f) * amp * 0.03f;   // gently rolling meadow
        return plain + (hill - plain) * rug;
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    public static long cellId(int gi, int gj) { return ((long) gi << 32) ^ (gj & 0xffffffffL); }

    public Chunk gen(int cx, int cz) {
        Chunk c = new Chunk(cx, cz); c.owner = this;
        Mesh m = c.mesh;
        Random r = new Random(f.seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL));
        float ox = cx * CS * CELL, oz = cz * CS * CELL;
        float[][] H = new float[CS + 1][CS + 1];
        for (int i = 0; i <= CS; i++) for (int j = 0; j <= CS; j++) H[i][j] = height(ox + i * CELL, oz + j * CELL);
        float[][] p = f.palette;
        float[] grass = mix(p[1], GREEN, 0.45f);
        float veg = vegetation;
        for (int i = 0; i < CS; i++) for (int j = 0; j < CS; j++) {
            float x = ox + i * CELL, z = oz + j * CELL;
            float h00 = H[i][j], h10 = H[i + 1][j], h01 = H[i][j + 1], h11 = H[i + 1][j + 1];
            float t = Math.min(1, Math.max(0, ((h00 + h11) * 0.5f + amp) / (2 * amp)));
            float[] col = mix(p[0], p[1], t);
            float k = 0.85f + r.nextFloat() * 0.3f;
            m.tri(x, h00, z, x + CELL, h10, z, x, h01, z + CELL, col, k);
            m.tri(x + CELL, h10, z, x + CELL, h11, z + CELL, x, h01, z + CELL, col, k);
            float avg = (h00 + h10 + h01 + h11) / 4;
            if (avg < waterY) {
                c.water++;
                float[] wc = mix(p[2], WATER, 0.5f);
                m.tri(x, waterY, z, x + CELL, waterY, z, x, waterY, z + CELL, wc, 1f);
                m.tri(x + CELL, waterY, z, x + CELL, waterY, z + CELL, x, waterY, z + CELL, wc, 1f);
                continue;
            }
            float roll = r.nextFloat(), s = 0.8f + r.nextFloat() * 1.2f;
            float px = x + r.nextFloat() * CELL, pz = z + r.nextFloat() * CELL, py = height(px, pz);
            float tuft = r.nextFloat();
            float rug = ruggedness(px, pz);
            float pt = Math.min(0.35f, pTree * treeDensity(px, pz, rug) * veg);
            long id = cellId(cx * CS + i, cz * CS + j);
            boolean gone = removed.contains(id);
            if (roll < pt) {
                if (!gone) {
                    float ts = 0.7f + (s - 0.8f) * 0.45f;     // trees stay modest in size
                    m.prism(px, py, pz, 0.2f * ts, 2.0f * ts, 4, 0, mix(p[2], new float[]{0.3f, 0.2f, 0.1f}, 0.6f), 1f);
                    m.cone(px, py + 1.6f * ts, pz, 1.15f * ts, 2.9f * ts, 5, 0, p[3], 1f);
                    c.trees++; c.objs.add(new Chunk.Obj(Chunk.TREE, px, py, pz, s, id));
                }
            } else if (roll < pt + pRock) {
                if (!gone) {
                    m.blob(px, py + 0.3f * s, pz, 0.8f * s, 0.6f * s, 5, p[2], 1f);
                    c.rocks++; c.objs.add(new Chunk.Obj(Chunk.ROCK, px, py, pz, s, id));
                }
            } else if (roll < pt + pRock + pCrystal) {
                int sides = 4 + r.nextInt(2);
                if (!gone) {
                    m.blob(px, py + 1.5f * s, pz, 0.4f * s, 1.8f * s, sides, p[3], 1.35f);
                    c.crystals++; c.objs.add(new Chunk.Obj(Chunk.CRYSTAL, px, py, pz, s, id));
                }
            } else if (roll < pt + pRock + pCrystal + pStruct) {
                float hh = 4 + r.nextFloat() * 8;
                if (!gone) {
                    m.prism(px, py, pz, 1.2f * s, hh, 4, 0.785f, p[1], 1f);
                    m.cone(px, py + hh, pz, 1.5f * s, 2f * s, 4, 0.785f, p[0], 1.2f);
                    c.structs++; c.objs.add(new Chunk.Obj(Chunk.STRUCT, px, py, pz, s, id));
                }
            } else if (tuft < 0.11f * (1 - rug) * veg) {
                m.cone(px, py, pz, 0.16f * s, 0.4f * s, 3, 0, grass, 1f);                 // grass tuft
            } else if (tuft > 1f - 0.012f * (1 - rug) * veg) {
                m.blob(px, py + 0.3f, pz, 0.14f, 0.24f, 4, p[3], 1.3f);                    // meadow flower
            }
        }
        int ns = r.nextInt(3);
        for (int i = 0; i < ns; i++) {
            float x = ox + r.nextFloat() * CS * CELL, z = oz + r.nextFloat() * CS * CELL;
            long sd = r.nextLong();
            if (height(x, z) > waterY) c.spawns.add(new Chunk.Spawn(x, z, sd));
        }
        return c;
    }

    /** Hash of the 3x3 chunks around origin: proves world reproducibility. */
    public String hash() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) {
            Chunk c = gen(cx, cz);
            for (int i = 0; i < c.mesh.n; i++) {
                int b = Float.floatToIntBits(c.mesh.d[i]);
                md.update((byte) (b >>> 24)); md.update((byte) (b >>> 16)); md.update((byte) (b >>> 8)); md.update((byte) b);
            }
        }
        return Features.hex(md.digest());
    }
}
