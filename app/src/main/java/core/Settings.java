package core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/** User settings, saved in the app's private folder. {@code rev} goes up on every change so the game knows to re-apply. */
final class Settings {
    // Defaults are the "Low" preset: made for phones with little GPU power (Android Go and similar).
    int radius = 2;           // chunks drawn around you
    int vegetation = 40;      // % of trees / grass
    int distortion = 40;      // % strength of the sleep-state warping
    int creatureDist = 50;    // metres at which creatures and buildings stop being drawn
    int fov = 70;
    int fog = 130;            // % fog density
    int volume = 100;         // % music volume
    int sens = 100;           // % touch look sensitivity
    boolean showFps = false;
    int rev;
    private File file;

    static int cl(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    void clampAll() {
        radius = cl(radius, 1, 6); vegetation = cl(vegetation, 0, 150); distortion = cl(distortion, 0, 100);
        creatureDist = cl(creatureDist, 30, 150); fov = cl(fov, 60, 100); fog = cl(fog, 50, 200);
        volume = cl(volume, 0, 100); sens = cl(sens, 25, 200);
    }

    static Settings load(File dir) {
        Settings s = new Settings();
        s.file = new File(dir, "settings.properties");
        try (InputStream in = new FileInputStream(s.file)) {
            Properties p = new Properties(); p.load(in);
            s.radius = Integer.parseInt(p.getProperty("radius", "" + s.radius));
            s.vegetation = Integer.parseInt(p.getProperty("vegetation", "" + s.vegetation));
            s.distortion = Integer.parseInt(p.getProperty("distortion", "" + s.distortion));
            s.creatureDist = Integer.parseInt(p.getProperty("creatureDist", "" + s.creatureDist));
            s.fov = Integer.parseInt(p.getProperty("fov", "" + s.fov));
            s.fog = Integer.parseInt(p.getProperty("fog", "" + s.fog));
            s.volume = Integer.parseInt(p.getProperty("volume", "" + s.volume));
            s.sens = Integer.parseInt(p.getProperty("sens", "" + s.sens));
            s.showFps = Boolean.parseBoolean(p.getProperty("showFps", "" + s.showFps));
        } catch (IOException | RuntimeException e) { /* first run or unreadable file: keep defaults */ }
        s.clampAll();
        return s;
    }

    void save() {
        if (file == null) return;
        Properties p = new Properties();
        p.setProperty("radius", "" + radius); p.setProperty("vegetation", "" + vegetation); p.setProperty("distortion", "" + distortion);
        p.setProperty("creatureDist", "" + creatureDist); p.setProperty("fov", "" + fov); p.setProperty("fog", "" + fog);
        p.setProperty("volume", "" + volume); p.setProperty("sens", "" + sens);
        p.setProperty("showFps", "" + showFps);
        try (OutputStream out = new FileOutputStream(file)) { p.store(out, "Lucid Dream settings"); }
        catch (IOException e) { System.err.println("Could not save settings: " + e.getMessage()); }
    }

    /** 0 = Low (weak phones), 1 = Medium, 2 = High. */
    void preset(int q) {
        switch (q) {
            case 0 -> { radius = 2; vegetation = 40; distortion = 40; creatureDist = 50; fog = 130; }
            case 1 -> { radius = 3; vegetation = 75; distortion = 60; creatureDist = 80; fog = 100; }
            default -> { radius = 5; vegetation = 100; distortion = 70; creatureDist = 120; fog = 80; }
        }
        rev++;
    }
}
