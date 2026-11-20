package core;

import creature.Creature;
import creature.Mind;
import java.util.List;
import render.Gfx;

/** The "imagination" window (Q): a live view of a creature's thoughts and mental images, plus an editor for what it knows. */
final class MindPanel {
    int tab;
    private int mx, my;
    private boolean click;

    private boolean button(String label, float x, float y, float w, float h, float r, float g, float b) {
        boolean hov = mx >= x - 4 && mx <= x + w + 4 && my >= y - 4 && my <= y + h + 4;     // a little slack for fingers
        float l = hov && click ? 0.2f : 0f;
        Gfx.rect(x, y, w, h, Math.min(1f, r + l), Math.min(1f, g + l), Math.min(1f, b + l), 0.92f);
        Gfx.text(label, x + 8, y + 3, 1, 1, 1);
        return hov && click;
    }

    private void bar(float x, float y, float w, float h, float v, float r, float g, float b) {
        Gfx.rect(x, y, w, h, 0.15f, 0.15f, 0.2f, 0.9f);
        Gfx.rect(x, y, w * Math.max(0f, Math.min(1f, v)), h, r, g, b, 0.95f);
    }

    void draw(Creature c, int W, int H, int mouseX, int mouseY, boolean clicked, float t) {
        mx = mouseX; my = mouseY; click = clicked;
        float w = 760, h = 672, x0 = Math.max(0f, W - w - 20), y0 = 24;      // docked to the right edge
        Gfx.rect(x0, y0, w, h, 0.03f, 0.02f, 0.08f, 0.88f);
        Gfx.rect(x0, y0, w, 3, 0.6f, 0.5f, 1f, 1f);
        Gfx.text(c.name + " the " + c.species + "  -  " + c.activity(), x0 + 16, y0 + 10, 1f, 0.95f, 0.7f);
        Gfx.text("Back closes", x0 + w - 120, y0 + 10, 0.6f, 0.6f, 0.7f);
        if (button("Imagination", x0 + 16, y0 + 44, 150, 28, tab == 0 ? 0.35f : 0.15f, tab == 0 ? 0.3f : 0.15f, tab == 0 ? 0.7f : 0.3f)) tab = 0;
        if (button("Mind editor", x0 + 176, y0 + 44, 150, 28, tab == 1 ? 0.35f : 0.15f, tab == 1 ? 0.3f : 0.15f, tab == 1 ? 0.7f : 0.3f)) tab = 1;
        if (tab == 0) imagination(c, x0, y0 + 84, t); else editor(c, x0, y0 + 84);
    }

    // ------------------------------------------------------------------ tab 1
    private void imagination(Creature c, float x0, float y0, float t) {
        Mind m = c.mind;
        float cw = 340, ch = 270, cx0 = x0 + 16;
        Gfx.rect(cx0, y0, cw, ch, 0.08f, 0.08f, 0.2f, 0.9f);
        Gfx.text("What it pictures", cx0 + 8, y0 + 4, 0.7f, 0.7f, 1f);
        for (int i = 0; i < m.imageCount; i++) {
            float px = cx0 + 36 + m.ix[i] * (cw - 100) + (float) Math.sin(t * 0.7f + m.ip[i]) * 9;
            float py = y0 + 40 + m.iy[i] * (ch - 110) + (float) Math.cos(t * 0.5f + m.ip[i]) * 7;
            int id = m.imagery[i];
            if (id >= 0) { pict(id, px, py, t); label(Mind.LABEL[id], px, py + 34, 1f, 1f, 1f); }
            else { pict(-1, px, py, t); label("? " + m.nameOf(-id - 1), px, py + 34, 0.7f, 0.7f, 0.85f); }
        }
        float tx = x0 + 376, ty = y0;
        Gfx.text("What it thinks", tx, ty + 4, 0.7f, 0.7f, 1f);
        float yy = ty + 32;
        for (int i = m.thoughts.size() - 1; i >= 0 && yy < y0 + ch + 40; i--) {
            float fade = i == m.thoughts.size() - 1 ? 1f : Math.max(0.45f, 0.85f - (m.thoughts.size() - 1 - i) * 0.08f);
            List<String> lines = Gfx.wrap(m.thoughts.get(i), 33);
            for (String ln : lines) {
                if (yy > y0 + ch + 60) break;
                Gfx.text(ln, tx, yy, fade, fade, fade * 0.95f);
                yy += 22;
            }
            yy += 8;
        }
        float by = y0 + ch + 20;
        Gfx.text("Mood", cx0, by, 0.7f, 0.7f, 1f);
        String[] mn = {"Fear", "Curiosity", "Aggression", "Sociability"};
        float[] mv = {c.fear, c.curio, c.aggr, c.social};
        float[][] mc = {{1f, 0.4f, 0.4f}, {0.4f, 0.9f, 1f}, {1f, 0.6f, 0.2f}, {0.6f, 1f, 0.5f}};
        for (int i = 0; i < 4; i++) {
            Gfx.text(mn[i], cx0, by + 28 + i * 30, 1, 1, 1);
            bar(cx0 + 130, by + 32 + i * 30, 200, 14, mv[i], mc[i][0], mc[i][1], mc[i][2]);
        }
        float rx = x0 + 376;
        Gfx.text("It sees the dreamer as " + Mind.BELIEF[m.dreamerBelief] + ".", rx, by, 1f, 0.9f, 0.6f);
        Gfx.text("Carrying: " + c.wood + " wood, " + c.stone + " stone, " + c.crystal + " crystal", rx, by + 30, 1, 1, 1);
        Gfx.text("Buildings finished: " + c.built, rx, by + 60, 1, 1, 1);
        int known = 0;
        for (int i = 0; i < Mind.N; i++) if (m.know[i] >= 0.5f) known++;
        Gfx.text("Understands " + known + " of " + Mind.N + " ideas", rx, by + 90, 1, 1, 1);
        Gfx.text("Body: " + c.species + ", size " + String.format("%.1f", c.size), rx, by + 120, 0.8f, 0.8f, 0.9f);
        Gfx.text("Tip: open the Mind editor to teach or erase ideas.", x0 + 16, y0 + 560, 0.6f, 0.65f, 0.8f);
    }

    private void label(String s, float cx, float y, float r, float g, float b) {
        Gfx.text(s, cx - Gfx.textW(s) / 2f, y, r, g, b);
    }

    // ------------------------------------------------------------------ tab 2
    private void editor(Creature c, float x0, float y0) {
        Mind m = c.mind;
        Gfx.text("Ideas it understands (tap to teach or erase)", x0 + 16, y0, 0.7f, 0.7f, 1f);
        int hover = -1;
        for (int i = 0; i < Mind.N; i++) {
            float yy = y0 + 32 + i * 36;
            if (my >= yy - 2 && my <= yy + 30 && mx >= x0 + 16 && mx <= x0 + 456) hover = i;
            Gfx.text(Mind.LABEL[i], x0 + 16, yy + 3, 1, 1, 1);
            bar(x0 + 190, yy + 7, 110, 14, m.know[i], 0.4f, 0.8f, 1f);
            if (button("Teach", x0 + 312, yy, 64, 28, 0.15f, 0.45f, 0.25f)) m.teach(i);
            if (button("Forget", x0 + 384, yy, 72, 28, 0.5f, 0.2f, 0.2f)) m.forget(i);
        }
        float fy = y0 + 32 + Mind.N * 36 + 8;
        if (hover >= 0) {
            float k = m.know[hover];
            String s = k < 0.2f ? "It has no idea what that is." : k < 0.5f ? "A vague feeling, nothing more." : "It believes: " + Mind.FACT[hover];
            Gfx.text(s, x0 + 16, fy, 1f, 0.9f, 0.6f);
        } else Gfx.text("Teaching building lets it make huts, towers and walls.", x0 + 16, fy, 0.6f, 0.65f, 0.8f);

        float rx = x0 + 490;
        Gfx.text("Personality", rx, y0, 0.7f, 0.7f, 1f);
        String[] tn = {"Fear", "Curiosity", "Aggression", "Social"};
        float[] tv = {c.fear, c.curio, c.aggr, c.social};
        for (int i = 0; i < 4; i++) {
            float yy = y0 + 32 + i * 36;
            Gfx.text(tn[i], rx, yy + 3, 1, 1, 1);
            bar(rx + 100, yy + 7, 60, 14, tv[i], 1f, 0.7f, 0.4f);
            if (button("-", rx + 168, yy, 28, 28, 0.3f, 0.2f, 0.2f)) c.nudge(i, -0.15f);
            if (button("+", rx + 202, yy, 28, 28, 0.2f, 0.3f, 0.2f)) c.nudge(i, 0.15f);
        }
        float by = y0 + 32 + 4 * 36 + 16;
        Gfx.text("It sees the dreamer as:", rx, by, 0.7f, 0.7f, 1f);
        Gfx.text(Mind.BELIEF[m.dreamerBelief], rx, by + 28, 1f, 0.9f, 0.6f);
        if (button("<", rx, by + 58, 28, 28, 0.25f, 0.25f, 0.4f)) m.dreamerBelief = (m.dreamerBelief + Mind.BELIEF.length - 1) % Mind.BELIEF.length;
        if (button(">", rx + 34, by + 58, 28, 28, 0.25f, 0.25f, 0.4f)) m.dreamerBelief = (m.dreamerBelief + 1) % Mind.BELIEF.length;
        float gy = by + 110;
        Gfx.text("Leave a gift", rx, gy, 0.7f, 0.7f, 1f);
        if (button("Wood", rx, gy + 30, 70, 28, 0.35f, 0.25f, 0.15f)) c.gift(0);
        if (button("Stone", rx + 76, gy + 30, 70, 28, 0.3f, 0.3f, 0.35f)) c.gift(1);
        if (button("Crystal", rx + 152, gy + 30, 80, 28, 0.2f, 0.35f, 0.45f)) c.gift(2);
        Gfx.text("Holds: " + c.wood + " wood, " + c.stone + " stone, " + c.crystal + " crystal", rx, gy + 70, 0.8f, 0.8f, 0.9f);
    }

    // ------------------------------------------------------------------ pictograms (built from rectangles)
    private void pict(int id, float cx, float cy, float t) {
        switch (id) {
            case Mind.WATER -> { for (int i = 0; i < 3; i++) Gfx.rect(cx - 22 + (float) Math.sin(t * 2 + i) * 4, cy - 10 + i * 10, 44, 6, 0.2f, 0.5f + i * 0.1f, 1f, 0.95f); }
            case Mind.TREES -> {
                Gfx.rect(cx - 3, cy + 8, 6, 14, 0.5f, 0.3f, 0.15f, 1f);
                Gfx.rect(cx - 16, cy + 2, 32, 8, 0.2f, 0.7f, 0.3f, 1f);
                Gfx.rect(cx - 11, cy - 6, 22, 8, 0.25f, 0.8f, 0.35f, 1f);
                Gfx.rect(cx - 6, cy - 14, 12, 8, 0.3f, 0.9f, 0.4f, 1f);
            }
            case Mind.LIGHT -> {
                Gfx.rect(cx - 9, cy - 9, 18, 18, 1f, 0.9f, 0.3f, 1f);
                Gfx.rect(cx - 2, cy - 22, 4, 10, 1f, 0.9f, 0.4f, 1f); Gfx.rect(cx - 2, cy + 12, 4, 10, 1f, 0.9f, 0.4f, 1f);
                Gfx.rect(cx - 22, cy - 2, 10, 4, 1f, 0.9f, 0.4f, 1f); Gfx.rect(cx + 12, cy - 2, 10, 4, 1f, 0.9f, 0.4f, 1f);
            }
            case Mind.DARK -> {
                Gfx.rect(cx - 22, cy - 16, 44, 32, 0.1f, 0.05f, 0.2f, 1f);
                float blink = Math.sin(t * 1.3f) > 0.9f ? 0f : 4f;
                Gfx.rect(cx - 10, cy - 4, 5, blink, 1f, 1f, 0.8f, 1f); Gfx.rect(cx + 5, cy - 4, 5, blink, 1f, 1f, 0.8f, 1f);
            }
            case Mind.DREAMER -> {
                Gfx.rect(cx - 7, cy - 22, 14, 14, 0.95f, 0.95f, 1f, 1f);
                Gfx.rect(cx - 10, cy - 7, 20, 22, 0.8f, 0.85f, 1f, 1f);
                Gfx.rect(cx - 18, cy - 5, 8, 4, 0.8f, 0.85f, 1f, 1f); Gfx.rect(cx + 10, cy - 5, 8, 4, 0.8f, 0.85f, 1f, 1f);
                Gfx.rect(cx - 8, cy + 15, 6, 8, 0.7f, 0.75f, 1f, 1f); Gfx.rect(cx + 2, cy + 15, 6, 8, 0.7f, 0.75f, 1f, 1f);
            }
            case Mind.OTHERS -> {
                Gfx.rect(cx - 20, cy - 6, 14, 22, 0.9f, 0.6f, 0.4f, 1f); Gfx.rect(cx - 17, cy - 16, 8, 8, 0.9f, 0.6f, 0.4f, 1f);
                Gfx.rect(cx + 4, cy - 2, 16, 18, 0.5f, 0.8f, 0.6f, 1f); Gfx.rect(cx + 8, cy - 12, 8, 8, 0.5f, 0.8f, 0.6f, 1f);
            }
            case Mind.MUSIC -> { for (int i = 0; i < 4; i++) Gfx.rect(cx - 18 + i * 10, cy + 14 - (8 + 10 * (0.5f + 0.5f * (float) Math.sin(t * 5 + i * 1.7f))), 7, 8 + 10 * (0.5f + 0.5f * (float) Math.sin(t * 5 + i * 1.7f)), 1f, 0.5f, 0.9f, 1f); }
            case Mind.CRYSTALS -> {
                Gfx.rect(cx - 3, cy - 20, 6, 6, 0.6f, 1f, 1f, 1f); Gfx.rect(cx - 8, cy - 14, 16, 8, 0.5f, 0.95f, 1f, 1f);
                Gfx.rect(cx - 13, cy - 6, 26, 10, 0.4f, 0.9f, 1f, 1f); Gfx.rect(cx - 8, cy + 4, 16, 8, 0.3f, 0.8f, 0.95f, 1f);
                Gfx.rect(cx - 3, cy + 12, 6, 6, 0.3f, 0.7f, 0.9f, 1f);
            }
            case Mind.BUILD -> {
                Gfx.rect(cx - 14, cy, 28, 18, 0.8f, 0.6f, 0.4f, 1f);
                Gfx.rect(cx - 18, cy - 6, 36, 6, 0.9f, 0.4f, 0.3f, 1f); Gfx.rect(cx - 12, cy - 12, 24, 6, 0.9f, 0.4f, 0.3f, 1f); Gfx.rect(cx - 6, cy - 18, 12, 6, 0.9f, 0.4f, 0.3f, 1f);
                Gfx.rect(cx - 4, cy + 7, 8, 11, 0.3f, 0.2f, 0.1f, 1f);
            }
            case Mind.DESTROY -> {
                Gfx.rect(cx - 16, cy - 4, 14, 14, 0.9f, 0.3f, 0.3f, 1f);
                Gfx.rect(cx + 2, cy - 10 + (float) Math.sin(t * 4) * 3, 12, 12, 0.9f, 0.4f, 0.3f, 1f);
                Gfx.rect(cx - 6, cy + 12, 8, 8, 0.8f, 0.3f, 0.2f, 1f); Gfx.rect(cx + 10, cy + 8, 6, 6, 0.8f, 0.3f, 0.2f, 1f);
            }
            case Mind.SLEEP -> {
                Gfx.rect(cx - 14, cy - 14, 16, 28, 0.8f, 0.8f, 1f, 1f); Gfx.rect(cx - 6, cy - 14, 14, 28, 0.08f, 0.08f, 0.2f, 1f);
                Gfx.text("z", cx + 8, cy - 22, 0.8f, 0.7f, 1f); Gfx.text("Z", cx + 16, cy - 34 + (float) Math.sin(t * 2) * 3, 0.9f, 0.8f, 1f);
            }
            case Mind.SPEECH -> {
                Gfx.rect(cx - 22, cy - 14, 44, 26, 1f, 1f, 1f, 0.95f); Gfx.rect(cx - 12, cy + 12, 8, 8, 1f, 1f, 1f, 0.95f);
                for (int i = 0; i < 3; i++) Gfx.rect(cx - 14 + i * 12, cy - 4, 6, 6, 0.2f, 0.2f, 0.3f, 1f);
            }
            default -> {
                Gfx.rect(cx - 20, cy - 18, 40, 36, 0.3f, 0.3f, 0.4f, 0.8f);
                Gfx.text("?", cx - 5, cy - 14, 1f, 1f, 1f);
            }
        }
    }
}
