package core;

import render.Gfx;

/** Pause menu and settings screen, laid out for touch (big buttons). Settings change instantly; the game re-applies them when {@code Settings.rev} moves. */
final class MenuPanel {
    static final int NONE = 0, RESUME = 1, HUB = 2, QUIT = 3, CHOOSE = 4;
    int page;
    private int mx, my;
    private boolean click;

    private boolean button(String label, float x, float y, float w, float h, float r, float g, float b) {
        boolean hit = mx >= x - 4 && mx <= x + w + 4 && my >= y - 4 && my <= y + h + 4;
        float l = hit && click ? 0.25f : 0f;
        Gfx.rect(x, y, w, h, Math.min(1f, r + l), Math.min(1f, g + l), Math.min(1f, b + l), 0.94f);
        Gfx.text(label, x + (w - Gfx.textW(label)) / 2f, y + (h - 24) / 2f + 1, 1, 1, 1);
        return hit && click;
    }

    private int stepper(String label, String value, float x, float y) {
        Gfx.text(label, x, y + 10, 1, 1, 1);
        Gfx.text(value, x + 250, y + 10, 1f, 0.9f, 0.6f);
        int r = 0;
        if (button("-", x + 400, y, 56, 44, 0.3f, 0.2f, 0.2f)) r = -1;
        if (button("+", x + 464, y, 56, 44, 0.2f, 0.3f, 0.2f)) r = 1;
        return r;
    }

    private boolean toggle(String label, boolean v, float x, float y) {
        Gfx.text(label, x, y + 10, 1, 1, 1);
        return button(v ? "On" : "Off", x + 400, y, 120, 44, v ? 0.2f : 0.4f, v ? 0.45f : 0.2f, 0.25f) ? !v : v;
    }

    /** Draws the menu; returns what the player chose (RESUME / HUB / CHOOSE / QUIT / NONE). */
    int draw(Settings s, boolean inHub, int W, int H, int mouseX, int mouseY, boolean clicked) {
        mx = mouseX; my = mouseY; click = clicked;
        Gfx.rect(0, 0, W, H, 0, 0, 0.03f, 0.68f);
        return page == 0 ? main(inHub, W, H) : settings(s, W, H);
    }

    private int main(boolean inHub, int W, int H) {
        float bw = 400, bh = 56, gap = 14, x = (W - bw) / 2f;
        int n = inHub ? 4 : 5;
        float y = (H - (n * bh + (n - 1) * gap)) / 2f + 20;
        Gfx.text("PAUSED", W / 2f - Gfx.textW("PAUSED") / 2f, y - 50, 1f, 0.95f, 0.7f);
        int act = NONE;
        if (button("Resume", x, y, bw, bh, 0.2f, 0.35f, 0.55f)) act = RESUME;
        y += bh + gap;
        if (button("Settings", x, y, bw, bh, 0.25f, 0.25f, 0.5f)) page = 1;
        y += bh + gap;
        if (button("Choose another image", x, y, bw, bh, 0.25f, 0.25f, 0.5f)) act = CHOOSE;
        y += bh + gap;
        if (!inHub) {
            if (button("Return to the hub", x, y, bw, bh, 0.25f, 0.25f, 0.5f)) act = HUB;
            y += bh + gap;
        }
        if (button("Quit game", x, y, bw, bh, 0.5f, 0.2f, 0.2f)) act = QUIT;
        return act;
    }

    private int settings(Settings s, int W, int H) {
        float pw = 1120, ph = 610, x0 = (W - pw) / 2f, y0 = Math.max(8f, (H - ph) / 2f);
        Gfx.rect(x0, y0, pw, ph, 0.03f, 0.02f, 0.09f, 0.94f);
        Gfx.rect(x0, y0, pw, 3, 0.6f, 0.5f, 1f, 1f);
        Gfx.text("SETTINGS", x0 + 28, y0 + 14, 1f, 0.95f, 0.7f);
        float c1 = x0 + 28, c2 = x0 + pw / 2f + 14;
        float y = y0 + 56;
        Gfx.text("Graphics preset", c1, y + 10, 1, 1, 1);
        if (button("Low", c1 + 250, y, 120, 44, 0.2f, 0.3f, 0.45f)) s.preset(0);
        if (button("Medium", c1 + 382, y, 120, 44, 0.2f, 0.3f, 0.45f)) s.preset(1);
        if (button("High", c1 + 514, y, 120, 44, 0.2f, 0.3f, 0.45f)) s.preset(2);
        y += 62;
        Gfx.text("Graphics", c1, y, 0.7f, 0.7f, 1f);
        Gfx.text("Sound and controls", c2, y, 0.7f, 0.7f, 1f);
        y += 34;
        float yl = y, yr = y;

        int d = stepper("Render distance", s.radius + " chunks (" + s.radius * 32 + " m)", c1, yl);
        if (d != 0) { s.radius = Settings.cl(s.radius + d, 1, 6); s.rev++; } yl += 56;
        d = stepper("Trees and grass", s.vegetation + " %", c1, yl);
        if (d != 0) { s.vegetation = Settings.cl(s.vegetation + d * 25, 0, 150); s.rev++; } yl += 56;
        d = stepper("Dream distortion", s.distortion + " %", c1, yl);
        if (d != 0) { s.distortion = Settings.cl(s.distortion + d * 10, 0, 100); s.rev++; } yl += 56;
        d = stepper("Creature distance", s.creatureDist + " m", c1, yl);
        if (d != 0) { s.creatureDist = Settings.cl(s.creatureDist + d * 10, 30, 150); s.rev++; } yl += 56;
        d = stepper("Field of view", s.fov + " deg", c1, yl);
        if (d != 0) { s.fov = Settings.cl(s.fov + d * 5, 60, 100); s.rev++; } yl += 56;
        d = stepper("Fog", s.fog + " %", c1, yl);
        if (d != 0) { s.fog = Settings.cl(s.fog + d * 25, 50, 200); s.rev++; }

        d = stepper("Music volume", s.volume + " %", c2, yr);
        if (d != 0) { s.volume = Settings.cl(s.volume + d * 10, 0, 100); s.rev++; } yr += 56;
        d = stepper("Look sensitivity", s.sens + " %", c2, yr);
        if (d != 0) { s.sens = Settings.cl(s.sens + d * 25, 25, 200); s.rev++; } yr += 56;
        boolean v = toggle("Show FPS", s.showFps, c2, yr);
        if (v != s.showFps) { s.showFps = v; s.rev++; } yr += 76;
        Gfx.text("Changes apply instantly and are saved.", c2, yr, 0.6f, 0.65f, 0.8f); yr += 30;
        Gfx.text("If the game stutters: preset Low, or", c2, yr, 0.6f, 0.65f, 0.8f); yr += 28;
        Gfx.text("lower the render distance.", c2, yr, 0.6f, 0.65f, 0.8f);

        if (button("Back", c1, y0 + ph - 74, 200, 52, 0.25f, 0.25f, 0.5f)) page = 0;
        return NONE;
    }
}
