package world;

import java.util.Arrays;

/** Flat-shaded triangle soup: x,y,z,r,g,b per vertex (CPU side only). StrictMath keeps geometry bit-identical across JVMs. */
public final class Mesh {
    public float[] d = new float[6 * 3 * 512];
    public int n;

    public void tri(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float[] c, float k) {
        float ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) return;
        float s = (0.4f + 0.6f * Math.abs((nx * 0.4f + ny * 0.8f + nz * 0.3f) / l)) * k;
        if (n + 18 > d.length) d = Arrays.copyOf(d, d.length * 2);
        put(ax, ay, az, c, s); put(bx, by, bz, c, s); put(cx, cy, cz, c, s);
    }

    private void put(float x, float y, float z, float[] c, float s) {
        d[n++] = x; d[n++] = y; d[n++] = z; d[n++] = c[0] * s; d[n++] = c[1] * s; d[n++] = c[2] * s;
    }

    public void cone(float x, float y, float z, float r, float h, int s, float rot, float[] c, float k) {
        for (int i = 0; i < s; i++) {
            double a0 = rot + 2 * Math.PI * i / s, a1 = rot + 2 * Math.PI * (i + 1) / s;
            tri(x + (float) (r * StrictMath.cos(a0)), y, z + (float) (r * StrictMath.sin(a0)),
                x + (float) (r * StrictMath.cos(a1)), y, z + (float) (r * StrictMath.sin(a1)), x, y + h, z, c, k);
        }
    }

    public void prism(float x, float y, float z, float r, float h, int s, float rot, float[] c, float k) {
        for (int i = 0; i < s; i++) {
            double a0 = rot + 2 * Math.PI * i / s, a1 = rot + 2 * Math.PI * (i + 1) / s;
            float x0 = x + (float) (r * StrictMath.cos(a0)), z0 = z + (float) (r * StrictMath.sin(a0));
            float x1 = x + (float) (r * StrictMath.cos(a1)), z1 = z + (float) (r * StrictMath.sin(a1));
            tri(x0, y, z0, x1, y, z1, x1, y + h, z1, c, k);
            tri(x0, y, z0, x1, y + h, z1, x0, y + h, z0, c, k);
        }
    }

    /** Double cone centred on (x,y,z), extending +-h vertically. */
    public void blob(float x, float y, float z, float r, float h, int s, float[] c, float k) {
        cone(x, y, z, r, h, s, 0, c, k);
        cone(x, y, z, r, -h, s, 0, c, k);
    }

    /** Oriented low-poly spheroid. Local axes: x = right, y = up, z = forward; forward = (sin yaw, -cos yaw). */
    public void spheroid(float x, float y, float z, float ax, float ay, float az, float yaw, int sl, int st, float[] c, float k) {
        float cs = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
        int row = sl + 1;
        float[] vx = new float[(st + 1) * row], vy = new float[vx.length], vz = new float[vx.length];
        for (int i = 0; i <= st; i++) {
            double phi = Math.PI * i / st;
            float sp = (float) Math.sin(phi), cp = (float) Math.cos(phi);
            for (int j = 0; j <= sl; j++) {
                double th = 2 * Math.PI * j / sl;
                float lx = ax * sp * (float) Math.cos(th), lz = az * sp * (float) Math.sin(th);
                int id = i * row + j;
                vx[id] = x + lx * cs + lz * sn; vy[id] = y + ay * cp; vz[id] = z + lx * sn - lz * cs;
            }
        }
        for (int i = 0; i < st; i++) for (int j = 0; j < sl; j++) {
            int a = i * row + j, b = a + 1, d = a + row, e = d + 1;
            tri(vx[a], vy[a], vz[a], vx[d], vy[d], vz[d], vx[b], vy[b], vz[b], c, k);
            tri(vx[b], vy[b], vz[b], vx[d], vy[d], vz[d], vx[e], vy[e], vz[e], c, k);
        }
    }

    /** Tapered tube between two points (legs, arms, tentacles, horns). */
    public void tube(float x0, float y0, float z0, float x1, float y1, float z1, float r0, float r1, int s, float[] c, float k) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-5f) return;
        dx /= l; dy /= l; dz /= l;
        float ax = Math.abs(dy) < 0.9f ? 0f : 1f, ay = Math.abs(dy) < 0.9f ? 1f : 0f;
        float ux = -dz * ay, uy = dz * ax, uz = dx * ay - dy * ax;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        for (int i = 0; i < s; i++) {
            double a0 = 2 * Math.PI * i / s, a1 = 2 * Math.PI * (i + 1) / s;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float p0x = x0 + (ux * c0 + vx * s0) * r0, p0y = y0 + (uy * c0 + vy * s0) * r0, p0z = z0 + (uz * c0 + vz * s0) * r0;
            float p1x = x0 + (ux * c1 + vx * s1) * r0, p1y = y0 + (uy * c1 + vy * s1) * r0, p1z = z0 + (uz * c1 + vz * s1) * r0;
            float q0x = x1 + (ux * c0 + vx * s0) * r1, q0y = y1 + (uy * c0 + vy * s0) * r1, q0z = z1 + (uz * c0 + vz * s0) * r1;
            float q1x = x1 + (ux * c1 + vx * s1) * r1, q1y = y1 + (uy * c1 + vy * s1) * r1, q1z = z1 + (uz * c1 + vz * s1) * r1;
            tri(p0x, p0y, p0z, p1x, p1y, p1z, q0x, q0y, q0z, c, k);
            tri(p1x, p1y, p1z, q1x, q1y, q1z, q0x, q0y, q0z, c, k);
        }
    }
}
