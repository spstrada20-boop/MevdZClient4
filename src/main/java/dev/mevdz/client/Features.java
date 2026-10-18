package dev.mevdz.client;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Yeni özelliklerin ayarları. Her ayar bir kez burada tanımlanır; menü (MevdZFeaturesGui)
 * ve kaydetme/yükleme bu listeden otomatik çalışır. Yeni ayar eklemek için
 * aşağıya bir satır eklemen yeter. Dosya: config/mevdz_features.properties
 */
public class Features {
    // ------------------------------------------------------------ tipler
    public static abstract class Setting {
        public final String key, label;

        protected Setting(String key, String label) {
            this.key = key;
            this.label = label;
        }

        abstract void read(Properties p);

        abstract void write(Properties p);

        abstract void reset();
    }

    public static class Bool extends Setting {
        public boolean value;
        private final boolean def;

        Bool(String key, String label, boolean def) {
            super(key, label);
            this.def = def;
            this.value = def;
        }

        void read(Properties p) {
            value = Boolean.parseBoolean(p.getProperty(key, String.valueOf(value)));
        }

        void write(Properties p) {
            p.setProperty(key, String.valueOf(value));
        }

        void reset() {
            value = def;
        }
    }

    public static class Num extends Setting {
        public float value;
        public final float min, max, step, def;
        public final String unit;
        public final int fill;
        /** Doluysa değer sayı yerine bu isimlerden biri olarak gösterilir (seçenek listesi). */
        public String[] names;

        Num(String key, String label, float min, float max, float step, float def, String unit, int fill) {
            super(key, label);
            this.min = min;
            this.max = max;
            this.step = step;
            this.def = def;
            this.unit = unit;
            this.fill = fill;
            this.value = def;
        }

        private float snap(float v) {
            v = Math.round((v - min) / step) * step + min;
            return Ui.clamp(v, min, max);
        }

        public float frac() {
            return (value - min) / (max - min);
        }

        public void setFrac(float f) {
            value = snap(min + (max - min) * Ui.clamp(f, 0f, 1f));
        }

        public String text() {
            if (names != null) {
                int i = Math.max(0, Math.min(names.length - 1, Math.round(value)));
                return names[i];
            }
            if (step >= 1f) return Math.round(value) + unit;
            return (Math.round(value * 100f) / 100f) + unit;
        }

        void read(Properties p) {
            try {
                value = snap(Float.parseFloat(p.getProperty(key, String.valueOf(value))));
            } catch (NumberFormatException ignored) {
            }
        }

        void write(Properties p) {
            p.setProperty(key, String.valueOf(value));
        }

        void reset() {
            value = def;
        }
    }

    public static class Category {
        public final String name;
        public final List<Setting> list = new ArrayList<Setting>();

        Category(String name) {
            this.name = name;
        }
    }

    // ------------------------------------------------------------ kayıt yardımcıları
    public static final List<Category> CATEGORIES = new ArrayList<Category>();
    private static Category cur;

    private static Category cat(String name) {
        cur = new Category(name);
        CATEGORIES.add(cur);
        return cur;
    }

    private static Bool bool(String key, String label, boolean def) {
        Bool b = new Bool(key, label, def);
        cur.list.add(b);
        return b;
    }

    private static Num num(String key, String label, float min, float max, float step, float def,
                           String unit, int fill) {
        Num n = new Num(key, label, min, max, step, def, unit, fill);
        cur.list.add(n);
        return n;
    }

    private static Num numNames(String key, String label, String[] names, int def, int fill) {
        Num n = new Num(key, label, 0f, names.length - 1, 1f, def, "", fill);
        n.names = names;
        cur.list.add(n);
        return n;
    }

    // ------------------------------------------------------------ AYARLAR
    // 1) Eşya / kılıç
    private static final Category C_ITEM = cat("Eşya");
    public static final Bool ITEM_CUSTOM = bool("item.custom", "Özel konum / boyut", false);
    public static final Num ITEM_X = num("item.x", "Konum X", -1f, 1f, 0.01f, 0f, "", 0x19C3FF);
    public static final Num ITEM_Y = num("item.y", "Konum Y", -1f, 1f, 0.01f, 0f, "", 0x19C3FF);
    public static final Num ITEM_Z = num("item.z", "Konum Z", -1f, 1f, 0.01f, 0f, "", 0x19C3FF);
    public static final Num ITEM_SCALE = num("item.scale", "Boyut", 0.3f, 1.5f, 0.05f, 1f, "x", 0x7C5CFF);
    public static final Bool OLD_BLOCK = bool("item.oldblock", "1.7 kılıç bloklama animasyonu", false);

    // 2) Zoom
    private static final Category C_ZOOM = cat("Zoom");
    public static final Bool ZOOM_ON = bool("zoom.on", "Zoom açık", true);
    public static final Num ZOOM_FACTOR = num("zoom.factor", "Yakınlaşma oranı", 1.5f, 8f, 0.1f, 3f, "x", 0x19C3FF);
    public static final Num ZOOM_SPEED = num("zoom.speed", "Geçiş hızı", 0.1f, 0.6f, 0.05f, 0.3f, "", 0x7C5CFF);

    // 3) Freelook
    private static final Category C_LOOK = cat("Bakış");
    public static final Bool FREELOOK_ON = bool("freelook.on", "Freelook (fare orta tuş)", true);

    // 4) Blok çerçevesi
    private static final Category C_OUT = cat("Çerçeve");
    public static final Bool OUTLINE_ON = bool("outline.on", "Özel blok çerçevesi", false);
    public static final Bool OUTLINE_CHROMA = bool("outline.chroma", "Gökkuşağı (chroma)", false);
    public static final Num OUTLINE_R = num("outline.r", "Kırmızı", 0f, 255f, 1f, 25f, "", 0xFF4D4D);
    public static final Num OUTLINE_G = num("outline.g", "Yeşil", 0f, 255f, 1f, 195f, "", 0x4DFF6A);
    public static final Num OUTLINE_B = num("outline.b", "Mavi", 0f, 255f, 1f, 255f, "", 0x4D8DFF);
    public static final Num OUTLINE_WIDTH = num("outline.width", "Kalınlık", 1f, 8f, 0.5f, 2f, "", 0x19C3FF);
    public static final Num OUTLINE_ALPHA = num("outline.alpha", "Opaklık", 0.1f, 1f, 0.05f, 0.8f, "", 0x7C5CFF);

    // 5) Motion blur
    private static final Category C_BLUR = cat("Blur");
    public static final Bool BLUR_ON = bool("blur.on", "Motion Blur", false);
    public static final Num BLUR_AMOUNT = num("blur.amount", "Yoğunluk", 0.1f, 0.9f, 0.05f, 0.5f, "", 0x19C3FF);

    // 6) Hitbox rengi
    private static final Category C_HIT = cat("Hitbox");
    public static final Bool HB_ON = bool("hitbox.on", "Özel hitbox rengi", false);
    public static final Bool HB_PLAYERS = bool("hitbox.players", "Sadece oyuncular", true);
    public static final Bool HB_FLASH = bool("hitbox.flash", "Vurulunca kırmızı yan", true);
    public static final Bool HB_EYE = bool("hitbox.eye", "Göz hizası çizgisi", false);
    public static final Bool HB_CHROMA = bool("hitbox.chroma", "Gökkuşağı (chroma)", false);
    public static final Num HB_R = num("hitbox.r", "Kırmızı", 0f, 255f, 1f, 255f, "", 0xFF4D4D);
    public static final Num HB_G = num("hitbox.g", "Yeşil", 0f, 255f, 1f, 220f, "", 0x4DFF6A);
    public static final Num HB_B = num("hitbox.b", "Mavi", 0f, 255f, 1f, 40f, "", 0x4D8DFF);
    public static final Num HB_WIDTH = num("hitbox.width", "Kalınlık", 1f, 6f, 0.5f, 2f, "", 0x19C3FF);
    public static final Num HB_ALPHA = num("hitbox.alpha", "Opaklık", 0.1f, 1f, 0.05f, 0.9f, "", 0x7C5CFF);

    // 7) Nametag
    private static final Category C_NAME = cat("Nametag");
    public static final Bool NT_ON = bool("nametag.on", "Özel nametag", false);
    public static final Num NT_SCALE = num("nametag.scale", "Boyut", 0.5f, 3f, 0.1f, 1.3f, "x", 0x19C3FF);
    public static final Bool NT_FAR = bool("nametag.far", "Uzakta büyüt (kolay gör)", true);
    public static final Num NT_BG = num("nametag.bg", "Arka plan opaklığı", 0f, 1f, 0.05f, 0.4f, "", 0x7C5CFF);
    public static final Bool NT_SHADOW = bool("nametag.shadow", "Yazı gölgesi", true);
    public static final Bool NT_HEALTH = bool("nametag.health", "Can göster", true);
    public static final Bool NT_DIST = bool("nametag.dist", "Mesafe göster", false);
    public static final Bool NT_RAINBOW = bool("nametag.rainbow", "Gökkuşağı isim", false);

    // 8) Combo sayacı
    private static final Category C_COMBO = cat("Combo");
    public static final Bool COMBO_ON = bool("combo.on", "Combo sayacı", true);
    public static final Bool COMBO_HIDE0 = bool("combo.hide0", "Sıfırken gizle", true);
    public static final Bool COMBO_BEST = bool("combo.best", "En iyi combo'yu göster", false);
    public static final Num COMBO_TIMEOUT = num("combo.timeout", "Sıfırlanma süresi", 1f, 5f, 0.5f, 2f, "sn", 0x19C3FF);

    // 9) Crosshair
    private static final Category C_CROSS = cat("Crosshair");
    public static final Bool CH_ON = bool("crosshair.on", "Özel crosshair", false);
    public static final Num CH_STYLE = numNames("crosshair.style", "Şekil",
            new String[]{"Artı", "Nokta", "Daire", "Kare", "Çarpı", "Artı + Nokta"}, 0, 0x7C5CFF);
    public static final Num CH_SIZE = num("crosshair.size", "Boyut", 1f, 20f, 1f, 6f, "", 0x19C3FF);
    public static final Num CH_GAP = num("crosshair.gap", "Boşluk", 0f, 10f, 1f, 2f, "", 0x19C3FF);
    public static final Num CH_THICK = num("crosshair.thick", "Kalınlık", 0.5f, 4f, 0.5f, 1f, "", 0x7C5CFF);
    public static final Bool CH_OUTLINE = bool("crosshair.outline", "Siyah kenar", true);
    public static final Bool CH_CHROMA = bool("crosshair.chroma", "Gökkuşağı (chroma)", false);
    public static final Num CH_R = num("crosshair.r", "Kırmızı", 0f, 255f, 1f, 255f, "", 0xFF4D4D);
    public static final Num CH_G = num("crosshair.g", "Yeşil", 0f, 255f, 1f, 255f, "", 0x4DFF6A);
    public static final Num CH_B = num("crosshair.b", "Mavi", 0f, 255f, 1f, 255f, "", 0x4D8DFF);
    public static final Num CH_ALPHA = num("crosshair.alpha", "Opaklık", 0.1f, 1f, 0.05f, 1f, "", 0x7C5CFF);

    // ------------------------------------------------------------ kaydet / yükle
    private static File file() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "mevdz_features.properties");
    }

    public static void load() {
        try {
            File f = file();
            if (!f.exists()) return;
            Properties p = new Properties();
            FileInputStream in = new FileInputStream(f);
            try {
                p.load(in);
            } finally {
                in.close();
            }
            for (Category c : CATEGORIES) for (Setting s : c.list) s.read(p);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void save() {
        Properties p = new Properties();
        for (Category c : CATEGORIES) for (Setting s : c.list) s.write(p);
        try {
            FileOutputStream out = new FileOutputStream(file());
            try {
                p.store(out, "MevdZ Client ozellikleri");
            } finally {
                out.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void resetCategory(Category c) {
        for (Setting s : c.list) s.reset();
    }
}
