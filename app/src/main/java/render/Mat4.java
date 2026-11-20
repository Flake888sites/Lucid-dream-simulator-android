package render;

/** Tiny column-major 4x4 matrix helpers (same layout OpenGL expects), replacing the removed fixed-function matrix stack. */
public final class Mat4 {
    private Mat4() {}

    public static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    /** Equivalent of gluPerspective. */
    public static float[] perspective(float fovyDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovyDeg) / 2));
        float[] m = new float[16];
        m[0] = f / aspect; m[5] = f;
        m[10] = (far + near) / (near - far); m[11] = -1;
        m[14] = 2 * far * near / (near - far);
        return m;
    }

    public static float[] rotX(float deg) {
        float c = (float) Math.cos(Math.toRadians(deg)), s = (float) Math.sin(Math.toRadians(deg));
        float[] m = identity();
        m[5] = c; m[6] = s; m[9] = -s; m[10] = c;
        return m;
    }

    public static float[] rotY(float deg) {
        float c = (float) Math.cos(Math.toRadians(deg)), s = (float) Math.sin(Math.toRadians(deg));
        float[] m = identity();
        m[0] = c; m[2] = -s; m[8] = s; m[10] = c;
        return m;
    }

    public static float[] translate(float x, float y, float z) {
        float[] m = identity();
        m[12] = x; m[13] = y; m[14] = z;
        return m;
    }

    /** a * b */
    public static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int col = 0; col < 4; col++)
            for (int row = 0; row < 4; row++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[col * 4 + k];
                r[col * 4 + row] = s;
            }
        return r;
    }

    /** First-person view matrix: Rx(pitch) * Ry(yaw) * T(-pos), as in the original glRotatef/glTranslatef sequence. */
    public static float[] view(float pitch, float yaw, float px, float py, float pz) {
        return mul(mul(rotX(pitch), rotY(yaw)), translate(-px, -py, -pz));
    }

    /** Projects a world point to logical screen pixels (origin top-left). Returns null when behind the camera / beyond far plane. */
    public static float[] project(float[] proj, float[] mv, float x, float y, float z, int w, int h) {
        float[] m = mul(proj, mv);
        float cx = m[0] * x + m[4] * y + m[8] * z + m[12];
        float cy = m[1] * x + m[5] * y + m[9] * z + m[13];
        float cz = m[2] * x + m[6] * y + m[10] * z + m[14];
        float cw = m[3] * x + m[7] * y + m[11] * z + m[15];
        if (cw <= 0) return null;
        float nz = cz / cw;
        if (nz >= 1f) return null;
        return new float[]{(cx / cw * 0.5f + 0.5f) * w, (0.5f - cy / cw * 0.5f) * h};
    }
}
