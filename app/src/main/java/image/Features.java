package image;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

/** Deterministic image -> seed + features. Manual box downscale (no interpolation) so results are reproducible. */
public final class Features {
    public static final int N = 64;
    public long seed, fileSize;
    public String hash;
    public float brightness, saturation, hue, edge, symmetry;
    public final float[][] palette = new float[4][3];

    public static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b & 255));
        return sb.toString();
    }

    /** Where the pixels come from. Lets the phone decode big photos a few rows at a time instead of loading them whole. */
    public interface Source {
        int width();
        int height();
        /** ARGB pixels of rows [y0, y1), full width, row after row (length = width * (y1 - y0)). */
        int[] rows(int y0, int y1) throws Exception;
    }

    public static Features load(Source src, long fileSize) throws Exception {
        int w = src.width(), h = src.height();
        if (w <= 0 || h <= 0) throw new Exception("Unsupported image");
        int[] px = new int[N * N];
        for (int y = 0; y < N; y++) {
            int y0 = y * h / N, y1 = Math.max(y0 + 1, (y + 1) * h / N);
            int[] band = src.rows(y0, y1);
            for (int x = 0; x < N; x++) {
                int x0 = x * w / N, x1 = Math.max(x0 + 1, (x + 1) * w / N);
                long r = 0, g = 0, b = 0; int c = 0;
                for (int yy = y0; yy < y1; yy++) for (int xx = x0; xx < x1; xx++) {
                    int p = band[(yy - y0) * w + xx];
                    r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255; c++;
                }
                px[y * N + x] = ((int) (r / c) << 16) | ((int) (g / c) << 8) | (int) (b / c);
            }
        }
        Features ft = new Features();
        ft.fileSize = fileSize;

        // Seed: SHA-256(downscaled pixels + file size)
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (int p : px) { md.update((byte) (p >> 16)); md.update((byte) (p >> 8)); md.update((byte) p); }
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.putLong(ft.fileSize);
        md.update(bb.array());
        byte[] d = md.digest();
        ft.seed = ByteBuffer.wrap(d).getLong();
        ft.hash = hex(d);

        float[] gray = new float[N * N];
        float[] hw = new float[12], hr = new float[12], hg = new float[12], hb = new float[12];
        double sb = 0, ss = 0;
        for (int i = 0; i < N * N; i++) {
            int p = px[i];
            float r = ((p >> 16) & 255) / 255f, g = ((p >> 8) & 255) / 255f, b = (p & 255) / 255f;
            float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b)), dl = mx - mn;
            float s = mx == 0 ? 0 : dl / mx;
            gray[i] = .299f * r + .587f * g + .114f * b;
            sb += gray[i]; ss += s;
            float hu = 0;
            if (dl > 0) {
                if (mx == r) hu = ((g - b) / dl) % 6; else if (mx == g) hu = (b - r) / dl + 2; else hu = (r - g) / dl + 4;
                hu /= 6; if (hu < 0) hu += 1;
            }
            int k = Math.min(11, (int) (hu * 12));
            float wgt = s * mx + 0.01f;
            hw[k] += wgt; hr[k] += r * wgt; hg[k] += g * wgt; hb[k] += b * wgt;
        }
        ft.brightness = (float) (sb / (N * N));
        ft.saturation = (float) (ss / (N * N));
        boolean[] used = new boolean[12];
        for (int p = 0; p < 4; p++) {
            int best = -1;
            for (int k = 0; k < 12; k++) if (!used[k] && (best < 0 || hw[k] > hw[best])) best = k;
            used[best] = true;
            if (p == 0) ft.hue = (best + 0.5f) / 12f;
            if (hw[best] > 0f) {
                ft.palette[p][0] = 0.15f + 0.85f * hr[best] / hw[best];
                ft.palette[p][1] = 0.15f + 0.85f * hg[best] / hw[best];
                ft.palette[p][2] = 0.15f + 0.85f * hb[best] / hw[best];
            } else {
                // This hue never appears in the image (0/0 used to give NaN colours): derive a neighbour of the previous colour instead.
                float[] q = ft.palette[Math.max(0, p - 1)];
                ft.palette[p][0] = 0.15f + 0.85f * q[1] * 0.9f;
                ft.palette[p][1] = 0.15f + 0.85f * q[2] * 0.9f;
                ft.palette[p][2] = 0.15f + 0.85f * q[0] * 0.9f;
            }
        }
        // Sobel edge density
        int cnt = 0;
        for (int y = 1; y < N - 1; y++) for (int x = 1; x < N - 1; x++) {
            float gx = -gray[(y - 1) * N + x - 1] + gray[(y - 1) * N + x + 1] - 2 * gray[y * N + x - 1] + 2 * gray[y * N + x + 1] - gray[(y + 1) * N + x - 1] + gray[(y + 1) * N + x + 1];
            float gy = -gray[(y - 1) * N + x - 1] - 2 * gray[(y - 1) * N + x] - gray[(y - 1) * N + x + 1] + gray[(y + 1) * N + x - 1] + 2 * gray[(y + 1) * N + x] + gray[(y + 1) * N + x + 1];
            if (Math.sqrt(gx * gx + gy * gy) > 0.5) cnt++;
        }
        ft.edge = cnt / (float) ((N - 2) * (N - 2));
        // Left/right symmetry
        double dsum = 0;
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) dsum += Math.abs(gray[y * N + x] - gray[y * N + N - 1 - x]);
        ft.symmetry = Math.max(0f, Math.min(1f, 1f - (float) (dsum / (N * N)) * 4f));
        return ft;
    }
}
