package world;

/** Something a creature built. Drawn every frame into the dynamic mesh; grows from the ground while progress goes 0 -> 1. */
public final class Build {
    public static final int HUT = 0, TOWER = 1, MONUMENT = 2, WALL = 3;
    public static final String[] NAMES = {"hut", "tower", "monument", "wall"};

    public final int kind;
    public final float x, y, z, rot;
    public final float[] c1, c2;
    /** The creature that built it (typed Object so the world package stays independent). */
    public final Object owner;
    public float progress;

    public Build(int kind, float x, float y, float z, float rot, float[] c1, float[] c2, Object owner) {
        this.kind = kind; this.x = x; this.y = y; this.z = z; this.rot = rot; this.c1 = c1; this.c2 = c2; this.owner = owner;
    }

    public float duration() { return switch (kind) { case HUT -> 6f; case TOWER -> 10f; case MONUMENT -> 9f; default -> 7f; }; }

    public void build(Mesh m, float t) {
        float p = Math.max(0.02f, Math.min(1f, progress));
        float e = p * p * (3 - 2 * p);
        float k = progress < 1f ? 0.7f : 1f;
        switch (kind) {
            case HUT -> {
                m.prism(x, y, z, 1.7f, 1.9f * e, 6, rot, c1, k);
                m.cone(x, y + 1.9f * e, z, 2.3f, 1.5f * e, 6, rot, c2, k * 1.1f);
            }
            case TOWER -> {
                float h = 0;
                for (int i = 0; i < 3; i++) {
                    float r = 1.5f - i * 0.35f, hh = 2.6f * e;
                    m.prism(x, y + h, z, r, hh, 6, rot + i * 0.3f, i % 2 == 0 ? c1 : c2, k);
                    h += hh;
                }
                m.cone(x, y + h, z, 1.0f, 1.8f * e, 6, rot, c2, k * 1.1f);
                if (progress >= 1f) m.blob(x, y + h + 2.6f + 0.2f * (float) Math.sin(t * 2), z, 0.35f, 0.5f, 4, c2, 1.5f);
            }
            case MONUMENT -> {
                m.prism(x, y, z, 2.2f, 0.5f * e, 8, rot, c1, k);
                m.prism(x, y + 0.5f * e, z, 1.4f, 0.5f * e, 8, rot, c1, k * 1.1f);
                m.blob(x, y + 1f * e + 2.2f * e, z, 0.7f, 2.2f * e, 5, c2, k * 1.45f);
                for (int i = 0; i < 4; i++) {
                    float a = rot + i * 1.5708f;
                    m.blob(x + 2.6f * (float) Math.cos(a), y + 0.9f * e, z + 2.6f * (float) Math.sin(a), 0.25f, 0.9f * e, 4, c2, k * 1.3f);
                }
            }
            default -> {
                for (int i = 0; i < 7; i++) {
                    float a = rot + i * 0.8976f;
                    float px = x + 3.2f * (float) Math.cos(a), pz = z + 3.2f * (float) Math.sin(a);
                    m.prism(px, y, pz, 0.4f, 1.4f * e, 4, a, i % 2 == 0 ? c1 : c2, k);
                    m.cone(px, y + 1.4f * e, pz, 0.5f, 0.5f * e, 4, a, c2, k);
                }
            }
        }
    }
}
