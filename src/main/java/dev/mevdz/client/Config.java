package dev.mevdz.client;

import net.minecraft.client.Minecraft;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Ayarlar: config/mevdz_client.properties dosyasına kaydedilir. */
public class Config {
    public static final List<HudElement> ELEMENTS = new ArrayList<HudElement>();
    public static boolean autoSprint = false;

    static {
        ELEMENTS.add(new HudElement("fps", "FPS", 4, 4, true));
        ELEMENTS.add(new HudElement("ping", "PING", 4, 20, true));
        ELEMENTS.add(new HudElement("cps", "CPS", 4, 36, true));
        ELEMENTS.add(new HudElement("sprint", "KOŞU", 4, 52, false));
        ELEMENTS.add(new HudElement("keys", "TUŞ", 4, 70, true));
        ELEMENTS.add(new HudElement("combo", "COMBO", 4, 166, true));
    }

    public static class HudElement {
        public final String id, label;
        public final int defX, defY;
        public final boolean defEnabled;
        public boolean enabled, background = true, chroma = false;
        public int x, y, color = 0xFFFFFF;
        public float scale = 1f;
        /** Son çizimdeki ekran boyutu (sürükleme için). */
        public int boxW = 60, boxH = 13;

        public HudElement(String id, String label, int x, int y, boolean enabled) {
            this.id = id;
            this.label = label;
            this.defX = x;
            this.defY = y;
            this.defEnabled = enabled;
            reset();
        }

        public void reset() {
            x = defX;
            y = defY;
            scale = 1f;
            color = 0xFFFFFF;
            chroma = false;
            background = true;
            enabled = defEnabled;
        }

        public int currentColor() {
            if (!chroma) return color;
            float hue = (System.currentTimeMillis() % 3000L) / 3000f;
            return Color.HSBtoRGB(hue, 0.6f, 1f) & 0xFFFFFF;
        }
    }

    private static File file() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "mevdz_client.properties");
    }

    public static void load() {
        Properties p = new Properties();
        try {
            File f = file();
            if (!f.exists()) return;
            FileInputStream in = new FileInputStream(f);
            try {
                p.load(in);
            } finally {
                in.close();
            }
            autoSprint = Boolean.parseBoolean(p.getProperty("autoSprint", "false"));
            for (HudElement e : ELEMENTS) {
                String k = e.id + ".";
                e.enabled = Boolean.parseBoolean(p.getProperty(k + "on", String.valueOf(e.enabled)));
                e.background = Boolean.parseBoolean(p.getProperty(k + "bg", String.valueOf(e.background)));
                e.chroma = Boolean.parseBoolean(p.getProperty(k + "chroma", String.valueOf(e.chroma)));
                e.x = Integer.parseInt(p.getProperty(k + "x", String.valueOf(e.x)));
                e.y = Integer.parseInt(p.getProperty(k + "y", String.valueOf(e.y)));
                e.color = Integer.parseInt(p.getProperty(k + "color", String.valueOf(e.color)));
                e.scale = Float.parseFloat(p.getProperty(k + "scale", String.valueOf(e.scale)));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("autoSprint", String.valueOf(autoSprint));
        for (HudElement e : ELEMENTS) {
            String k = e.id + ".";
            p.setProperty(k + "on", String.valueOf(e.enabled));
            p.setProperty(k + "bg", String.valueOf(e.background));
            p.setProperty(k + "chroma", String.valueOf(e.chroma));
            p.setProperty(k + "x", String.valueOf(e.x));
            p.setProperty(k + "y", String.valueOf(e.y));
            p.setProperty(k + "color", String.valueOf(e.color));
            p.setProperty(k + "scale", String.valueOf(e.scale));
        }
        try {
            FileOutputStream out = new FileOutputStream(file());
            try {
                p.store(out, "MevdZ Client ayarlari");
            } finally {
                out.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
