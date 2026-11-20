package world;

import java.util.Random;

public final class Noise {
    private final int[] p = new int[512];

    public Noise(long seed) {
        Random r = new Random(seed);
        int[] a = new int[256];
        for (int i = 0; i < 256; i++) a[i] = i;
        for (int i = 255; i > 0; i--) { int j = r.nextInt(i + 1); int t = a[i]; a[i] = a[j]; a[j] = t; }
        for (int i = 0; i < 512; i++) p[i] = a[i & 255];
    }

    private static float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float grad(int h, float x, float y) {
        return switch (h & 3) { case 0 -> x + y; case 1 -> -x + y; case 2 -> x - y; default -> -x - y; };
    }

    public float perlin(float x, float y) {
        float fx = (float) Math.floor(x), fy = (float) Math.floor(y);
        int xi = ((int) fx) & 255, yi = ((int) fy) & 255;
        float xf = x - fx, yf = y - fy, u = fade(xf), v = fade(yf);
        int aa = p[p[xi] + yi], ab = p[p[xi] + yi + 1], ba = p[p[xi + 1] + yi], bb = p[p[xi + 1] + yi + 1];
        return lerp(lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u), lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u), v);
    }

    public float fbm(float x, float y, int oct) {
        float sum = 0, amp = 1, norm = 0, fr = 1;
        for (int i = 0; i < oct; i++) { sum += perlin(x * fr, y * fr) * amp; norm += amp; amp *= 0.5f; fr *= 2f; }
        return sum / norm;
    }
}
