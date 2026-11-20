package audio;

import image.Features;
import java.security.MessageDigest;
import java.util.Random;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;

/** Procedural PCM synth: pad + bass + arp + percussion + water + comb reverb. All deterministic (StrictMath, LCG noise). */
public final class Synth {
    static final int SR = 22050;
    static final double TAU = 2 * Math.PI;
    // major, minor, dorian, phrygian, lydian
    static final int[][] MODES = {{0, 2, 4, 5, 7, 9, 11}, {0, 2, 3, 5, 7, 8, 10}, {0, 2, 3, 5, 7, 9, 10}, {0, 1, 3, 5, 7, 8, 10}, {0, 2, 4, 6, 7, 9, 11}};
    static final double[] BPM_MUL = {1, 0.85, 0.55, 1.3};
    static final double[] DET = {0.001, 0.003, 0.002, 0.012};
    static final double[] VIB = {0, 0.001, 0.002, 0.012};
    static final float[] REV = {0, 0.1f, 0.25f, 0.15f};

    public volatile float energy, dark, water, volume = 1f;
    public volatile int sleep;
    private volatile boolean running;

    private final int root; private final int[] scale; private final double bpm; private final float timbre;
    private final int[] melody = new int[16], prog = new int[4]; private final boolean[] gate = new boolean[16];
    private float gPad, gBass, gArp, gPerc, gWater, lp, aEnv, kEnv, hEnv;
    private final double[] padPh = new double[3], padF = new double[3];
    private double bassPh, bassF, arpPh, arpF, kickPh, sPos;
    private long n, lastStep = -1; private int noise = 12345;
    private final float[] c1 = new float[1051], c2 = new float[1423], c3 = new float[1877];
    private int i1, i2, i3;

    public Synth(Features f) {
        root = 45 + (int) (f.hue * 12);
        int m = f.brightness < 0.3f ? 3 : f.brightness < 0.45f ? 1 : f.saturation > 0.6f ? 4 : f.brightness > 0.7f ? 0 : 2;
        scale = MODES[m];
        bpm = 60 + f.brightness * 70;
        timbre = f.saturation;
        Random r = new Random(f.seed);
        float pg = Math.min(0.9f, 0.3f + f.edge * 2f);
        for (int i = 0; i < 16; i++) { melody[i] = r.nextInt(7) + (r.nextInt(3) == 0 ? 7 : 0); gate[i] = r.nextFloat() < pg; }
        prog[0] = 0; prog[1] = r.nextInt(7); prog[2] = r.nextInt(7); prog[3] = 4;
    }

    private double freq(int deg, int octShift) {
        int len = scale.length;
        int midi = root + scale[deg % len] + 12 * (deg / len) + 12 * octShift;
        return 440.0 * StrictMath.pow(2, (midi - 69) / 12.0);
    }

    private float noise() { noise = noise * 1664525 + 1013904223; return (noise >>> 8) / 8388608f - 1f; }

    public float next() {
        double t = (double) n / SR;
        float en = energy, dk = dark, wt = water; int sl = sleep & 3;
        float k = 0.00004f;
        gPad += (0.5f - gPad) * k;
        gBass += (0.15f + dk * 0.6f - gBass) * k;
        gArp += ((0.25f + en * 0.4f) * (1 - dk * 0.4f) - gArp) * k;
        gPerc += ((en > 0.3f ? (en - 0.3f) * 1.2f : 0f) - gPerc) * k;
        gWater += (wt * 0.6f - gWater) * k;

        double stepLen = SR * 60.0 / (bpm * BPM_MUL[sl]) / 2.0;
        sPos += 1.0 / stepLen;
        long step = (long) sPos;
        if (step != lastStep) {
            lastStep = step;
            int d = prog[(int) ((step / 16) % 4)];
            for (int i = 0; i < 3; i++) padF[i] = freq(d + 2 * i, 0);
            bassF = freq(d, -2);
            int si = (int) (step % 16);
            if (gate[si]) { arpF = freq(d + melody[si], 1); aEnv = 1f; }
            if (step % 4 == 0 || (en > 0.7f && step % 2 == 0)) kEnv = 1f;
            if (step % 2 == 1) hEnv = 1f;
        }
        double vib = 1 + VIB[sl] * StrictMath.sin(t * (sl == 3 ? 7 : 0.5));
        double pad = 0;
        for (int i = 0; i < 3; i++) {
            padPh[i] += TAU * padF[i] * (1 + DET[sl] * (i - 1)) * vib / SR;
            pad += StrictMath.sin(padPh[i]) + timbre * 0.3 * StrictMath.sin(2 * padPh[i]);
        }
        pad *= 0.12 * gPad * (0.7 + 0.3 * StrictMath.sin(t * 0.2));
        bassPh += TAU * bassF * vib / SR;
        double bass = (StrictMath.sin(bassPh) + dk * 0.4 * StrictMath.sin(2 * bassPh)) * gBass * 0.25;
        arpPh += TAU * arpF * vib / SR;
        double arp = (StrictMath.sin(arpPh) + timbre * 0.5 * StrictMath.sin(2 * arpPh) + timbre * 0.25 * StrictMath.sin(3 * arpPh)) * aEnv * gArp * 0.2;
        aEnv *= 0.99982f;
        kickPh += TAU * (45 + 110 * kEnv) / SR;
        double perc = StrictMath.sin(kickPh) * kEnv * gPerc * 0.5;
        kEnv *= 0.9997f;
        float nz = noise();
        perc += nz * hEnv * 0.15 * gPerc;
        hEnv *= 0.99887f;
        lp += (nz - lp) * 0.02f;
        double wat = lp * 3 * gWater * (0.6 + 0.4 * StrictMath.sin(t * 0.37));

        double dry = pad + bass + arp + perc + wat;
        float fb = Math.min(0.9f, 0.55f + 0.3f * dk + REV[sl]);
        float o1 = c1[i1], o2 = c2[i2], o3 = c3[i3];
        c1[i1] = (float) dry + o1 * fb; c2[i2] = (float) dry + o2 * fb; c3[i3] = (float) dry + o3 * fb;
        if (++i1 >= c1.length) i1 = 0;
        if (++i2 >= c2.length) i2 = 0;
        if (++i3 >= c3.length) i3 = 0;
        float mix = 0.15f + 0.35f * dk + REV[sl];
        double x = dry * (1 - mix * 0.5) + (o1 + o2 + o3) * 0.33 * mix;
        n++;
        return (float) (x / (1 + Math.abs(x)));
    }

    public short next16() { return (short) (Math.max(-1f, Math.min(1f, next() * volume)) * 30000); }

    /** Hash of 6 seconds rendered with fixed parameters. */
    public static String hash(Features f) throws Exception {
        Synth s = new Synth(f);
        s.energy = 0.5f; s.dark = 0.5f; s.water = 0.5f;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < SR * 6; i++) { short v = s.next16(); md.update((byte) v); md.update((byte) (v >> 8)); }
        return Features.hex(md.digest());
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        Thread th = new Thread(() -> {
            AudioTrack track = null;
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                int min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                int bytes = Math.max(min, SR);      // about half a second of audio: forgiving on slow phones
                track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(bytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
                track.play();
                short[] buf = new short[1024];
                while (running) {
                    for (int i = 0; i < buf.length; i++) buf[i] = next16();
                    if (track.write(buf, 0, buf.length) < 0) break;
                }
            } catch (Throwable e) {
                System.err.println("Audio unavailable: " + e);
            } finally {
                if (track != null) {
                    try { track.stop(); } catch (Throwable ignored) { }
                    track.release();
                }
            }
        }, "synth");
        th.setDaemon(true);
        th.start();
    }

    public synchronized void stop() { running = false; }
}
