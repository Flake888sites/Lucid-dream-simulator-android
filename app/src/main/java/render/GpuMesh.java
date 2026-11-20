package render;

import static android.opengl.GLES20.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import world.Mesh;

/** A vertex buffer holding interleaved x,y,z,r,g,b floats (the CPU-side {@link Mesh} layout). */
public final class GpuMesh {
    private static FloatBuffer scratch = alloc(1 << 16);

    private static FloatBuffer alloc(int floats) {
        return ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private final int vbo, usage;
    private int count;

    public GpuMesh(boolean dynamic) {
        usage = dynamic ? GL_STREAM_DRAW : GL_STATIC_DRAW;
        int[] id = new int[1];
        glGenBuffers(1, id, 0);
        vbo = id[0];
    }

    public void upload(Mesh m) {
        count = m.n / 6;
        if (m.n == 0) return;
        if (scratch.capacity() < m.n) scratch = alloc(Math.max(m.n, scratch.capacity() * 2));
        scratch.clear();
        scratch.put(m.d, 0, m.n).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, m.n * 4, scratch, usage);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public void draw() {
        if (count == 0) return;
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 24, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 24, 12);
        glEnableVertexAttribArray(1);
        glDrawArrays(GL_TRIANGLES, 0, count);
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /** Only call this while the GL context that created the buffer is still alive. */
    public void close() {
        glDeleteBuffers(1, new int[]{vbo}, 0);
    }
}
