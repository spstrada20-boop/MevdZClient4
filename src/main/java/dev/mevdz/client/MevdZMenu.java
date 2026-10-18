package dev.mevdz.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ana ayar menüsü (Insert): modül kartları, filtre/arama, her modülün kendi ayar sayfası,
 * HUD düzenleme modu. Modüller Config.ELEMENTS ve Features.CATEGORIES'ten otomatik oluşur;
 * yeni ayar/kategori eklenince menüye kendiliğinden gelir.
 */
public class MevdZMenu extends GuiScreen {
    /** HUD düzenleme modu açıkken true: Hud öğelerin çerçevesini çizer. */
    public static boolean editingHud = false;

    private static int tabIdx = 0;     // 0 modüller, 1 ayarlar
    private static int filterIdx = 0;

    private static final int PW = 430, PH_MAX = 272;
    private static final int T_HUD = 1, T_PVP = 2, T_VIS = 4, T_MOVE = 8;
    private static final String[] TABS = {"MODÜLLER", "AYARLAR"};
    private static final String[] FILTERS = {"TÜMÜ", "HUD", "PVP", "GÖRSEL", "HAREKET"};
    private static final int[] FILTER_BITS = {0, T_HUD, T_PVP, T_VIS, T_MOVE};

    private static final int ICON_FPS = 0, ICON_PING = 1, ICON_CPS = 2, ICON_KEYS = 3, ICON_COMBO = 4,
            ICON_SPRINT = 5, ICON_SWORD = 6, ICON_ZOOM = 7, ICON_EYE = 8, ICON_FRAME = 9, ICON_BLUR = 10,
            ICON_HITBOX = 11, ICON_TAG = 12, ICON_CROSS = 13, ICON_GENERIC = 14;

    // ------------------------------------------------------------ modül + satır tipleri
    private static final class Mod {
        final String name, key;
        final int icon, tags;
        final Config.HudElement el;
        final Features.Category cat;
        final boolean sprint;
        float anim, hov;

        Mod(String name, String key, int icon, int tags, Config.HudElement el, Features.Category cat,
            boolean sprint) {
            this.name = name;
            this.key = key;
            this.icon = icon;
            this.tags = tags;
            this.el = el;
            this.cat = cat;
            this.sprint = sprint;
        }
    }

    private static final class Row {
        static final int HEADER = 0, TOGGLE = 1, SLIDER = 2, SWATCH = 3, BUTTON = 4, HINT = 5;
        final int type;
        final String label;
        Features.Setting fs;
        int mode = -1;
        int y, h;
        float anim, hov;

        Row(int type, String label) {
            this.type = type;
            this.label = label;
        }
    }

    private static final List<Mod> MODS = new ArrayList<Mod>();

    private static Config.HudElement elById(String id) {
        for (Config.HudElement e : Config.ELEMENTS) if (e.id.equals(id)) return e;
        return null;
    }

    private static void addEl(String id, String name, String key, int icon, int tags) {
        Config.HudElement e = elById(id);
        if (e != null) MODS.add(new Mod(name, key, icon, tags, e, null, false));
    }

    private static void addCat(Features.Category c) {
        String n = c.name;
        if (n.equals("Combo")) return; // Combo modülü HUD öğesiyle birlikte eklendi
        if (n.equals("Eşya")) MODS.add(new Mod("EŞYA / KILIÇ", "esya kilic item sword 1.7 blok", ICON_SWORD, T_VIS | T_PVP, null, c, false));
        else if (n.equals("Zoom")) MODS.add(new Mod("ZOOM", "zoom yakinlas", ICON_ZOOM, T_VIS, null, c, false));
        else if (n.equals("Bakış")) MODS.add(new Mod("FREELOOK", "freelook bakis kamera", ICON_EYE, T_MOVE | T_VIS, null, c, false));
        else if (n.equals("Çerçeve")) MODS.add(new Mod("BLOK ÇERÇEVESİ", "blok cerceve outline", ICON_FRAME, T_VIS, null, c, false));
        else if (n.equals("Blur")) MODS.add(new Mod("MOTION BLUR", "motion blur bulaniklik", ICON_BLUR, T_VIS, null, c, false));
        else if (n.equals("Hitbox")) MODS.add(new Mod("HITBOX", "hitbox renk", ICON_HITBOX, T_PVP | T_VIS, null, c, false));
        else if (n.equals("Nametag")) MODS.add(new Mod("NAMETAG", "nametag isim", ICON_TAG, T_PVP | T_VIS, null, c, false));
        else if (n.equals("Crosshair")) MODS.add(new Mod("CROSSHAIR", "crosshair nisangah", ICON_CROSS, T_PVP | T_VIS, null, c, false));
        else MODS.add(new Mod(n.toUpperCase(new Locale("tr", "TR")), n.toLowerCase(Locale.ROOT), ICON_GENERIC, T_VIS, null, c, false));
    }

    private static void buildMods() {
        if (!MODS.isEmpty()) return;
        Features.Category combo = null;
        for (Features.Category c : Features.CATEGORIES) if (c.name.equals("Combo")) combo = c;
        addEl("keys", "KEYSTROKES", "keystrokes tus keys wasd", ICON_KEYS, T_HUD | T_PVP);
        addEl("fps", "FPS", "fps", ICON_FPS, T_HUD);
        addEl("ping", "PING", "ping", ICON_PING, T_HUD);
        addEl("cps", "CPS", "cps tiklama", ICON_CPS, T_HUD | T_PVP);
        Config.HudElement ce = elById("combo");
        if (combo != null && ce != null) {
            MODS.add(new Mod("COMBO", "combo vurus sayac", ICON_COMBO, T_HUD | T_PVP, ce, combo, false));
        }
        Config.HudElement se = elById("sprint");
        if (se != null) {
            MODS.add(new Mod("OTOMATİK KOŞU", "otomatik kosu sprint", ICON_SPRINT, T_MOVE | T_HUD, se, null, true));
        }
        for (Features.Category c : Features.CATEGORIES) addCat(c);
    }

    // ------------------------------------------------------------ durum
    private final GuiScreen parent;
    private final long t0 = System.currentTimeMillis();
    private final List<Row> rows = new ArrayList<Row>();
    private final StringBuilder search = new StringBuilder();
    private final int[][] tabR = new int[2][];
    private final int[][] chipR = new int[FILTERS.length][];
    private int[] closeR, hudBtn, backR, searchR, doneR;

    private Mod open;
    private boolean editLayout, confirmReset;
    private float slide;
    private int scaleF = 2;
    private int px, py, ph, cx, cy, cw, ch, contentH;
    private float scroll, scrollTarget;
    private Row dragRow;
    private Config.HudElement dragEl;
    private int offX, offY;

    public MevdZMenu(GuiScreen parent) {
        this.parent = parent;
        buildMods();
        if (MevdZSettingsGui.selected == null) MevdZSettingsGui.selected = Config.ELEMENTS.get(0);
        if (tabIdx > 1) tabIdx = 0;
        if (filterIdx >= FILTERS.length) filterIdx = 0;
    }

    @Override
    public void initGui() {
        layout();
        buildRows();
    }

    // ------------------------------------------------------------ yerleşim
    private void layout() {
        ph = Math.min(PH_MAX, height - 8);
        px = (width - PW) / 2;
        py = Math.max(4, (height - ph) / 2);
        cx = px + 12;
        cw = PW - 24;

        int tx = px + 178;
        for (int i = 0; i < 2; i++) {
            int w = Fx.spacedWidth(fontRendererObj, TABS[i]) + 24;
            tabR[i] = new int[]{tx, py + 8, w, 18};
            tx += w + 6;
        }
        closeR = new int[]{px + PW - 26, py + 9, 16, 16};
        hudBtn = new int[]{px + 12, py + ph - 17, 96, 13};
        doneR = new int[]{width / 2 - 50, height - 34, 100, 18};

        int top = py + 40;
        if (open != null) {
            backR = new int[]{cx, top, 62, 16};
            top += 24;
        } else if (tabIdx == 0) {
            int x = cx;
            for (int i = 0; i < FILTERS.length; i++) {
                int w = Fx.spacedWidth(fontRendererObj, FILTERS[i]) + 16;
                chipR[i] = new int[]{x, top, w, 16};
                x += w + 6;
            }
            searchR = new int[]{cx + cw - 120, top, 120, 16};
            top += 24;
        }
        cy = top;
        ch = py + ph - 22 - top;
    }

    private List<Mod> filtered() {
        List<Mod> out = new ArrayList<Mod>();
        String q = search.toString().toLowerCase(Locale.ROOT).trim();
        int bit = FILTER_BITS[filterIdx];
        for (Mod m : MODS) {
            if (bit != 0 && (m.tags & bit) == 0) continue;
            if (q.length() > 0 && !m.key.contains(q) && !m.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(m);
        }
        return out;
    }

    private static final int COLS = 3, GAP = 8, CARD_H = 92;

    private int[] cardRect(int i, int off) {
        int cardW = (cw - GAP * (COLS - 1)) / COLS;
        return new int[]{cx + (i % COLS) * (cardW + GAP), cy + 2 + (i / COLS) * (CARD_H + GAP) - off, cardW, CARD_H};
    }

    // ------------------------------------------------------------ modül durumu
    private boolean isOn(Mod m) {
        if (m.sprint) return Config.autoSprint;
        if (m.cat != null && !m.cat.list.isEmpty() && m.cat.list.get(0) instanceof Features.Bool) {
            return ((Features.Bool) m.cat.list.get(0)).value;
        }
        return m.el != null && m.el.enabled;
    }

    private void toggleMod(Mod m) {
        if (m.sprint) {
            Config.autoSprint = !Config.autoSprint;
        } else if (m.cat != null && !m.cat.list.isEmpty() && m.cat.list.get(0) instanceof Features.Bool) {
            Features.Bool b = (Features.Bool) m.cat.list.get(0);
            b.value = !b.value;
        } else if (m.el != null) {
            m.el.enabled = !m.el.enabled;
        }
        saveAll();
    }

    private void resetMod(Mod m) {
        if (m.cat != null) Features.resetCategory(m.cat);
        if (m.el != null) m.el.reset();
        if (m.sprint) Config.autoSprint = false;
        saveAll();
    }

    private void resetAll() {
        for (Features.Category c : Features.CATEGORIES) Features.resetCategory(c);
        for (Config.HudElement e : Config.ELEMENTS) e.reset();
        Config.autoSprint = false;
        saveAll();
    }

    private void saveAll() {
        Features.save();
        Config.save();
    }

    // ------------------------------------------------------------ satırlar (ayar sayfaları)
    private Row add(int type, String label, Features.Setting fs, int mode) {
        Row r = new Row(type, label);
        r.fs = fs;
        r.mode = mode;
        rows.add(r);
        return r;
    }

    private static String keyName(KeyBinding kb) {
        return Keyboard.getKeyName(kb.getKeyCode());
    }

    private void buildRows() {
        rows.clear();
        scroll = 0f;
        scrollTarget = 0f;
        confirmReset = false;
        if (open != null) {
            Mod m = open;
            if (m.cat != null) {
                List<Features.Setting> l = m.cat.list;
                for (int i = 0; i < l.size(); i++) {
                    Features.Setting s = l.get(i);
                    if (i == 0 && s instanceof Features.Bool) continue; // ana anahtar karttaki AÇIK/KAPALI
                    add(s instanceof Features.Bool ? Row.TOGGLE : Row.SLIDER, s.label, s, -1);
                }
            }
            if (m.el != null) {
                MevdZSettingsGui.selected = m.el;
                add(Row.HEADER, "HUD ÖĞESİ", null, -1);
                add(Row.TOGGLE, "Göster", null, 1);
                add(Row.TOGGLE, "Arka plan", null, 2);
                add(Row.TOGGLE, "Gökkuşağı rengi", null, 3);
                add(Row.SLIDER, "Boyut", null, 0);
                add(Row.SLIDER, "Kırmızı", null, 1);
                add(Row.SLIDER, "Yeşil", null, 2);
                add(Row.SLIDER, "Mavi", null, 3);
                add(Row.SWATCH, "", null, -1);
            }
            add(Row.BUTTON, "Bu modülü sıfırla", null, 0);
        } else if (tabIdx == 1) {
            add(Row.HEADER, "KISAYOLLAR", null, -1);
            add(Row.HINT, "Menü: " + keyName(ExtraModule.keyMain), null, -1);
            add(Row.HINT, "Zoom (basılı tut): " + keyName(FeatureModule.keyZoom), null, -1);
            add(Row.HINT, "Otomatik koşu aç/kapa: " + keyName(MevdZClient.keySprint), null, -1);
            add(Row.HINT, "Freelook: fare orta tuş", null, -1);
            add(Row.HINT, "Tuşları Oyun Ayarları > Kontroller'den değiştirebilirsin.", null, -1);
            add(Row.HEADER, "SIFIRLA", null, -1);
            add(Row.BUTTON, "Tüm ayarları sıfırla", null, 2);
            add(Row.HEADER, "HAKKINDA", null, -1);
            add(Row.HINT, "MevdZ Client v1.0  •  Minecraft 1.8.9", null, -1);
        }
        int y = 0;
        for (Row r : rows) {
            r.h = heightOf(r.type);
            r.y = y;
            y += r.h + 4;
        }
        contentH = y;
    }

    private static int heightOf(int type) {
        switch (type) {
            case Row.HEADER: return 12;
            case Row.TOGGLE: return 18;
            case Row.SLIDER: return 26;
            case Row.SWATCH: return 18;
            case Row.BUTTON: return 18;
            default: return 12;
        }
    }

    private String rowLabel(Row r) {
        if (r.type == Row.BUTTON && r.mode == 2 && confirmReset) return "Emin misin? Tekrar tıkla";
        return r.label;
    }

    private boolean getBool(Row r) {
        if (r.fs instanceof Features.Bool) return ((Features.Bool) r.fs).value;
        Config.HudElement el = MevdZSettingsGui.selected;
        switch (r.mode) {
            case 0: return Config.autoSprint;
            case 1: return el.enabled;
            case 2: return el.background;
            default: return el.chroma;
        }
    }

    private void toggleBool(Row r) {
        if (r.fs instanceof Features.Bool) {
            Features.Bool b = (Features.Bool) r.fs;
            b.value = !b.value;
        } else {
            Config.HudElement el = MevdZSettingsGui.selected;
            switch (r.mode) {
                case 0: Config.autoSprint = !Config.autoSprint; break;
                case 1: el.enabled = !el.enabled; break;
                case 2: el.background = !el.background; break;
                default: el.chroma = !el.chroma; break;
            }
        }
        saveAll();
    }

    private float getFrac(Row r) {
        if (r.fs instanceof Features.Num) return ((Features.Num) r.fs).frac();
        Config.HudElement el = MevdZSettingsGui.selected;
        switch (r.mode) {
            case 0: return (el.scale - 0.5f) / 2.5f;
            case 1: return ((el.color >> 16) & 255) / 255f;
            case 2: return ((el.color >> 8) & 255) / 255f;
            default: return (el.color & 255) / 255f;
        }
    }

    private String getText(Row r) {
        if (r.fs instanceof Features.Num) return ((Features.Num) r.fs).text();
        Config.HudElement el = MevdZSettingsGui.selected;
        switch (r.mode) {
            case 0: return (Math.round(el.scale * 100) / 100f) + "x";
            case 1: return String.valueOf((el.color >> 16) & 255);
            case 2: return String.valueOf((el.color >> 8) & 255);
            default: return String.valueOf(el.color & 255);
        }
    }

    private int getFill(Row r) {
        if (r.fs instanceof Features.Num) return ((Features.Num) r.fs).fill;
        switch (r.mode) {
            case 0: return 0x19C3FF;
            case 1: return 0xFF4D4D;
            case 2: return 0x4DFF6A;
            default: return 0x4D8DFF;
        }
    }

    private void setFrac(Row r, float f) {
        if (r.fs instanceof Features.Num) {
            ((Features.Num) r.fs).setFrac(f);
            return;
        }
        Config.HudElement el = MevdZSettingsGui.selected;
        if (r.mode == 0) {
            el.scale = Math.round((0.5f + f * 2.5f) * 20f) / 20f;
        } else {
            int rr = (el.color >> 16) & 255, gg = (el.color >> 8) & 255, bb = el.color & 255;
            int v = Math.round(f * 255f);
            if (r.mode == 1) rr = v;
            else if (r.mode == 2) gg = v;
            else bb = v;
            el.color = (rr << 16) | (gg << 8) | bb;
        }
    }

    // ------------------------------------------------------------ çizim
    private void clip(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scaleF, mc.displayHeight - (y + h + (int) slide) * scaleF, w * scaleF, h * scaleF);
    }

    private void unclip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        layout();
        scaleF = new ScaledResolution(mc).getScaleFactor();
        Fx.pxScale = scaleF;
        editingHud = editLayout;

        long now = System.currentTimeMillis();
        float t = (now - t0) / 1000f;
        float o = Math.min(1f, (now - t0) / 240f);
        float e = 1f - (1f - o) * (1f - o) * (1f - o);
        slide = 16f * (1f - e);

        if (mc.theWorld == null) {
            drawGradientRect(0, 0, width, height, 0xFF0A0B10, 0xFF171A2B);
            Hud.renderAll();
        }

        if (editLayout) {
            drawEditLayout(mx, my);
            return;
        }

        drawGradientRect(0, 0, width, height, (((int) (0x50 * e)) << 24), (((int) (0x99 * e)) << 24));

        GlStateManager.pushMatrix();
        GlStateManager.translate(0f, slide, 0f);

        // panel + parıltı
        int glow = Ui.lerp(Ui.CYAN, Ui.VIOLET, 0.5f + 0.5f * (float) Math.sin(t * 1.2f));
        for (int i = 6; i >= 1; i--) {
            Ui.outline(px - i, py - i, PW + 2 * i, ph + 2 * i, (Math.max(2, 48 / (i * i)) << 24) | glow);
        }
        drawRect(px, py, px + PW, py + ph, 0xF2090B14);
        clip(px, py, PW, ph);
        Fx.orb(px + 60, py + 20, 170, Ui.CYAN, 38);
        Fx.orb(px + PW - 40, py + ph - 10, 190, Ui.VIOLET, 42);
        unclip();
        drawRect(px, py, px + PW, py + 34, 0x66000000);
        Ui.outline(px, py, PW, ph, 0xFF222637);
        Ui.hGrad(px, py, PW, 2, Ui.CYAN, Ui.VIOLET, 255);
        Ui.hGrad(px + 1, py + 34, PW - 2, 1, Ui.CYAN, Ui.VIOLET, 90);

        drawHeader(mx, my, t, glow);

        boolean inC = Ui.in(mx, my, cx, cy, cw, ch);
        scroll += (scrollTarget - scroll) * 0.3f;
        if (Math.abs(scrollTarget - scroll) < 0.1f) scroll = scrollTarget;

        if (open != null) {
            drawSubHeader(mx, my);
            drawRows(mx, my, inC);
        } else if (tabIdx == 0) {
            drawFilterBar(mx, my, t);
            drawGrid(mx, my, inC);
        } else {
            drawRows(mx, my, inC);
        }
        drawScrollbar();
        drawFooter(mx, my);

        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawHeader(int mx, int my, float t, int glow) {
        Fx.poly(px + 22, py + 17, 10.5f, 10.5f, 6, 0.5236f, 1.6f, 0xFF000000 | glow);
        fontRendererObj.drawStringWithShadow("M", px + 22 - 3f, py + 17 - 4f, 0xFFFFFFFF);
        Ui.animText(fontRendererObj, "MEVDZ", px + 40, py + 13, t);
        Fx.drawSpaced(fontRendererObj, "CLIENT", px + 40 + 36, py + 13, 0xFFB8C4E8, true);

        for (int i = 0; i < 2; i++) {
            int[] r = tabR[i];
            boolean sel = i == tabIdx;
            boolean hov = Ui.in(mx, my, r);
            drawRect(r[0], r[1], r[0] + r[2], r[1] + r[3], sel ? 0xCC262C44 : (hov ? 0x55303650 : 0x33202538));
            Fx.drawSpacedCentered(fontRendererObj, TABS[i], r[0] + r[2] / 2f, r[1] + 5,
                    sel ? 0xFFFFFFFF : 0xFFB8C4E8, false);
            if (sel) Ui.hGrad(r[0], r[1] + r[3] - 2, r[2], 2, Ui.CYAN, Ui.VIOLET, 255);
        }

        boolean hc = Ui.in(mx, my, closeR);
        drawRect(closeR[0], closeR[1], closeR[0] + 16, closeR[1] + 16, hc ? 0xFFE0405A : 0x33FFFFFF);
        Fx.line(closeR[0] + 4.5f, closeR[1] + 4.5f, closeR[0] + 11.5f, closeR[1] + 11.5f, 1.2f, 0xFFFFFFFF);
        Fx.line(closeR[0] + 11.5f, closeR[1] + 4.5f, closeR[0] + 4.5f, closeR[1] + 11.5f, 1.2f, 0xFFFFFFFF);
    }

    private void drawSubHeader(int mx, int my) {
        boolean hov = Ui.in(mx, my, backR);
        drawRect(backR[0], backR[1], backR[0] + backR[2], backR[1] + backR[3], hov ? 0xFF2A3047 : 0xFF1C2030);
        Fx.line(backR[0] + 9, backR[1] + 4, backR[0] + 5, backR[1] + 8, 1.2f, 0xFFB8C4E8);
        Fx.line(backR[0] + 5, backR[1] + 8, backR[0] + 9, backR[1] + 12, 1.2f, 0xFFB8C4E8);
        Fx.drawSpaced(fontRendererObj, "GERİ", backR[0] + 16, backR[1] + 4, 0xFFB8C4E8, false);
        Fx.drawSpaced(fontRendererObj, open.name, backR[0] + backR[2] + 12, backR[1] + 4, 0xFFFFFFFF, true);
    }

    private void drawFilterBar(int mx, int my, float t) {
        for (int i = 0; i < FILTERS.length; i++) {
            int[] r = chipR[i];
            boolean sel = i == filterIdx;
            boolean hov = Ui.in(mx, my, r);
            drawRect(r[0], r[1], r[0] + r[2], r[1] + r[3], sel ? 0xFF2D6BFF : (hov ? 0xFF2A3047 : 0xFF1B2030));
            Fx.drawSpacedCentered(fontRendererObj, FILTERS[i], r[0] + r[2] / 2f, r[1] + 4,
                    sel ? 0xFFFFFFFF : 0xFF9AA6C8, false);
        }
        int[] s = searchR;
        drawRect(s[0], s[1], s[0] + s[2], s[1] + s[3], 0xFF10131C);
        Ui.outline(s[0], s[1], s[2], s[3], 0xFF222637);
        Fx.ring(s[0] + 9, s[1] + 7, 3f, 1f, 0xFF6F7896);
        Fx.line(s[0] + 11.5f, s[1] + 9.5f, s[0] + 14, s[1] + 12, 1f, 0xFF6F7896);
        String txt = search.toString();
        if (txt.length() == 0) {
            fontRendererObj.drawString("Ara...", s[0] + 19, s[1] + 4, 0xFF4F5878);
        } else {
            String shown = txt + (((int) (t * 2f)) % 2 == 0 ? "_" : "");
            fontRendererObj.drawString(shown, s[0] + 19, s[1] + 4, 0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------ modül kartları
    private void drawGrid(int mx, int my, boolean inC) {
        List<Mod> list = filtered();
        int rowsN = (list.size() + COLS - 1) / COLS;
        contentH = rowsN * (CARD_H + GAP) + 2;
        scrollTarget = Ui.clamp(scrollTarget, 0f, Math.max(0, contentH - ch));
        int off = Math.round(scroll);

        clip(cx, cy, cw, ch);
        for (int i = 0; i < list.size(); i++) {
            int[] r = cardRect(i, off);
            if (r[1] + r[3] < cy || r[1] > cy + ch) continue;
            drawCard(list.get(i), r[0], r[1], r[2], r[3], mx, my, inC);
        }
        if (list.isEmpty()) {
            Fx.drawSpacedCentered(fontRendererObj, "MODÜL BULUNAMADI", cx + cw / 2f, cy + 30, 0xFF6F7896, false);
        }
        unclip();
    }

    private void drawCard(Mod m, int x, int y, int w, int h, int mx, int my, boolean inC) {
        boolean hov = inC && Ui.in(mx, my, x, y, w, h);
        m.hov += ((hov ? 1f : 0f) - m.hov) * 0.25f;
        boolean on = isOn(m);
        m.anim += ((on ? 1f : 0f) - m.anim) * 0.25f;
        if (Math.abs((on ? 1f : 0f) - m.anim) < 0.01f) m.anim = on ? 1f : 0f;

        int accent = Ui.lerp(Ui.CYAN, Ui.VIOLET, MODS.indexOf(m) / (float) Math.max(1, MODS.size() - 1));
        float act = Math.max(m.anim * 0.6f, m.hov);

        drawRect(x, y, x + w, y + h, 0xF0121521);
        drawGradientRect(x, y, x + w, y + 48, (((int) (0x10 + 0x38 * act)) << 24) | accent, accent);
        Ui.outline(x, y, w, h, (((int) (0x40 + 0x90 * m.hov)) << 24) | Ui.lerp(0x2A3045, accent, m.hov));

        drawIcon(m.icon, x + w / 2, y + 21, Ui.lerp(0x6B7699, accent, 0.25f + 0.75f * act));
        int nw = fontRendererObj.getStringWidth(m.name);
        fontRendererObj.drawString(m.name, x + (w - nw) / 2, y + 41,
                0xFF000000 | Ui.lerp(0x8A94B8, 0xFFFFFF, Math.max(m.hov, m.anim)));

        int optY = y + h - 36, enY = y + h - 19;
        boolean hOpt = inC && Ui.in(mx, my, x, optY, w, 17);
        boolean hEn = inC && Ui.in(mx, my, x, enY, w, 19);
        drawRect(x + 1, optY, x + w - 1, optY + 17, hOpt ? 0xFF2A3047 : 0xFF1C2030);
        Fx.drawSpacedCentered(fontRendererObj, "AYARLAR", x + w / 2f, optY + 5, 0xFFB8C4E8, false);
        int en = Ui.lerp(0x242938, 0x2DBE60, m.anim);
        if (hEn) en = Ui.lerp(en, 0xFFFFFF, 0.14f);
        drawRect(x + 1, enY, x + w - 1, y + h - 1, 0xFF000000 | en);
        Fx.drawSpacedCentered(fontRendererObj, on ? "AÇIK" : "KAPALI", x + w / 2f, enY + 6,
                0xFF000000 | Ui.lerp(0x7C86A8, 0xFFFFFF, m.anim), false);
    }

    private void drawIcon(int id, int cx, int cy, int rgb) {
        int c = 0xFF000000 | rgb;
        switch (id) {
            case ICON_FPS:
                for (int i = 0; i < 4; i++) {
                    drawRect(cx - 12 + i * 7, cy + 10 - (5 + i * 5), cx - 12 + i * 7 + 5, cy + 10, c);
                }
                break;
            case ICON_PING: {
                float[] xs = {-13, -6, -3, 2, 5, 13};
                float[] ys = {2, 2, -9, 9, 2, 2};
                for (int i = 0; i < 5; i++) Fx.line(cx + xs[i], cy + ys[i], cx + xs[i + 1], cy + ys[i + 1], 1.5f, c);
                break;
            }
            case ICON_CPS:
                Ui.outline(cx - 7, cy - 11, 14, 22, c);
                drawRect(cx - 7, cy - 11, cx, cy - 2, c);
                drawRect(cx - 7, cy - 3, cx + 7, cy - 2, c);
                break;
            case ICON_KEYS:
                drawRect(cx - 4, cy - 11, cx + 4, cy - 3, c);
                drawRect(cx - 13, cy - 1, cx - 5, cy + 7, c);
                drawRect(cx - 4, cy - 1, cx + 4, cy + 7, c);
                drawRect(cx + 5, cy - 1, cx + 13, cy + 7, c);
                break;
            case ICON_COMBO: {
                String s = "x3";
                GlStateManager.pushMatrix();
                GlStateManager.translate((float) cx, (float) (cy - 8), 0f);
                GlStateManager.scale(2f, 2f, 1f);
                fontRendererObj.drawStringWithShadow(s, -fontRendererObj.getStringWidth(s) / 2f, 0f, c);
                GlStateManager.popMatrix();
                break;
            }
            case ICON_SPRINT:
                Fx.line(cx - 10, cy - 8, cx - 2, cy, 2.2f, c);
                Fx.line(cx - 2, cy, cx - 10, cy + 8, 2.2f, c);
                Fx.line(cx - 1, cy - 8, cx + 7, cy, 2.2f, c);
                Fx.line(cx + 7, cy, cx - 1, cy + 8, 2.2f, c);
                break;
            case ICON_SWORD:
                Fx.line(cx - 3, cy + 3, cx + 10, cy - 10, 3f, c);
                Fx.line(cx - 8, cy - 1, cx + 1, cy + 8, 2f, c);
                Fx.line(cx - 5, cy + 5, cx - 10, cy + 10, 3f, c);
                break;
            case ICON_ZOOM:
                Fx.ring(cx - 2, cy - 2, 8, 1.8f, c);
                Fx.line(cx + 4, cy + 4, cx + 11, cy + 11, 3f, c);
                break;
            case ICON_EYE:
                Fx.poly(cx, cy, 13, 7, 28, 0f, 1.6f, c);
                Fx.disc(cx, cy, 3.5f, c);
                break;
            case ICON_FRAME:
                Ui.outline(cx - 10, cy - 10, 20, 20, c);
                Ui.outline(cx - 9, cy - 9, 18, 18, c);
                break;
            case ICON_BLUR:
                drawRect(cx - 10, cy - 8, cx + 2, cy + 4, 0x50000000 | rgb);
                drawRect(cx - 6, cy - 4, cx + 6, cy + 8, 0xA0000000 | rgb);
                drawRect(cx - 2, cy, cx + 10, cy + 12, c);
                break;
            case ICON_HITBOX:
                Ui.outline(cx - 10, cy - 6, 14, 16, c);
                Ui.outline(cx - 4, cy - 12, 14, 16, c);
                Fx.line(cx - 10, cy - 6, cx - 4, cy - 12, 1f, c);
                Fx.line(cx + 4, cy - 6, cx + 10, cy - 12, 1f, c);
                Fx.line(cx - 10, cy + 10, cx - 4, cy + 4, 1f, c);
                Fx.line(cx + 4, cy + 10, cx + 10, cy + 4, 1f, c);
                break;
            case ICON_TAG:
                Ui.outline(cx - 12, cy - 8, 24, 12, c);
                drawRect(cx - 7, cy - 3, cx + 7, cy - 1, c);
                Fx.line(cx - 3, cy + 4, cx, cy + 8, 1.2f, c);
                Fx.line(cx + 3, cy + 4, cx, cy + 8, 1.2f, c);
                break;
            case ICON_CROSS:
                Fx.line(cx - 11, cy, cx - 3, cy, 2f, c);
                Fx.line(cx + 3, cy, cx + 11, cy, 2f, c);
                Fx.line(cx, cy - 11, cx, cy - 3, 2f, c);
                Fx.line(cx, cy + 3, cx, cy + 11, 2f, c);
                break;
            default:
                Fx.ring(cx, cy, 8, 1.8f, c);
                Fx.disc(cx, cy, 2.5f, c);
                break;
        }
    }

    // ------------------------------------------------------------ ayar satırları
    private void drawRows(int mx, int my, boolean inC) {
        int off = Math.round(scroll);
        scrollTarget = Ui.clamp(scrollTarget, 0f, Math.max(0, contentH + 4 - ch));
        clip(cx, cy, cw, ch);
        for (Row r : rows) {
            int ry = cy + 2 + r.y - off;
            if (ry + r.h < cy || ry > cy + ch) continue;
            drawRow(r, ry, mx, my, inC);
        }
        unclip();
    }

    private void drawRow(Row r, int ry, int mx, int my, boolean inC) {
        boolean hov = inC && Ui.in(mx, my, cx, ry, cw, r.h);
        r.hov += ((hov ? 1f : 0f) - r.hov) * 0.25f;

        switch (r.type) {
            case Row.HEADER: {
                Fx.drawSpaced(fontRendererObj, r.label, cx + 2, ry + 2, 0xFF000000 | Ui.MUTED, false);
                drawRect(cx + Fx.spacedWidth(fontRendererObj, r.label) + 10, ry + 5, cx + cw - 2, ry + 6, 0x4019C3FF);
                break;
            }
            case Row.TOGGLE: {
                drawRect(cx, ry, cx + cw, ry + r.h, ((0x12 + (int) (0x20 * r.hov)) << 24) | 0xFFFFFF);
                float target = getBool(r) ? 1f : 0f;
                r.anim += (target - r.anim) * 0.3f;
                if (Math.abs(target - r.anim) < 0.01f) r.anim = target;
                drawRect(cx, ry, cx + 2, ry + r.h, (((int) (255 * r.anim)) << 24) | Ui.CYAN);
                fontRendererObj.drawString(r.label, cx + 8, ry + (r.h - 8) / 2, 0xFFDDE6FF);
                int tx = cx + cw - 32, ty = ry + (r.h - 10) / 2;
                drawRect(tx, ty, tx + 26, ty + 10, 0xFF000000 | Ui.lerp(0x2A2F42, Ui.CYAN, r.anim));
                int kx = tx + 1 + (int) (16 * r.anim);
                drawRect(kx, ty + 1, kx + 8, ty + 9, 0xFFFFFFFF);
                break;
            }
            case Row.SLIDER: {
                drawRect(cx, ry, cx + cw, ry + r.h, ((0x12 + (int) (0x20 * r.hov)) << 24) | 0xFFFFFF);
                float fr = Ui.clamp(getFrac(r), 0f, 1f);
                int fill = getFill(r);
                fontRendererObj.drawString(r.label, cx + 8, ry + 3, 0xFFB8C4E8);
                String v = getText(r);
                fontRendererObj.drawString(v, cx + cw - fontRendererObj.getStringWidth(v) - 6, ry + 3, 0xFFFFFFFF);
                int tw = cw - 16, tx = cx + 8, ty = ry + 16;
                drawRect(tx, ty, tx + tw, ty + 3, 0xFF222637);
                int fw = (int) (tw * fr);
                drawRect(tx, ty, tx + fw, ty + 3, 0xFF000000 | fill);
                drawRect(tx + fw - 2, ty - 3, tx + fw + 2, ty + 6, 0xFFFFFFFF);
                break;
            }
            case Row.SWATCH: {
                Config.HudElement el = MevdZSettingsGui.selected;
                drawRect(cx, ry, cx + cw, ry + r.h, 0x18FFFFFF);
                fontRendererObj.drawString("Renk önizleme", cx + 8, ry + (r.h - 8) / 2, 0xFFB8C4E8);
                int sx = cx + cw - 80;
                drawRect(sx, ry + 3, cx + cw - 6, ry + r.h - 3, 0xFF000000 | el.currentColor());
                Ui.outline(sx, ry + 3, cx + cw - 6 - sx, r.h - 6, 0xFF222637);
                break;
            }
            case Row.BUTTON: {
                drawRect(cx, ry, cx + cw, ry + r.h, hov ? 0x40FFFFFF : 0x20FFFFFF);
                String lb = rowLabel(r);
                fontRendererObj.drawString(lb, cx + (cw - fontRendererObj.getStringWidth(lb)) / 2,
                        ry + (r.h - 8) / 2, 0xFFDDE6FF);
                break;
            }
            default: {
                fontRendererObj.drawString(r.label, cx + 2, ry + 2, 0xFF000000 | Ui.MUTED);
                break;
            }
        }
    }

    private void drawScrollbar() {
        if (contentH + 4 <= ch) return;
        float max = contentH + 4 - ch;
        int bh = Math.max(16, (int) (ch * (float) ch / (contentH + 4)));
        int by = cy + (int) ((ch - bh) * (scroll / max));
        drawRect(px + PW - 8, cy, px + PW - 6, cy + ch, 0x20FFFFFF);
        drawRect(px + PW - 8, by, px + PW - 6, by + bh, 0xAA19C3FF);
    }

    private void drawFooter(int mx, int my) {
        drawRect(px + 1, py + ph - 21, px + PW - 1, py + ph - 20, 0xFF1B1F31);
        boolean hh = Ui.in(mx, my, hudBtn);
        drawRect(hudBtn[0], hudBtn[1], hudBtn[0] + hudBtn[2], hudBtn[1] + hudBtn[3], hh ? 0xFF3A6BEA : 0xFF2A55C8);
        Fx.drawSpacedCentered(fontRendererObj, "HUD DÜZENLE", hudBtn[0] + hudBtn[2] / 2f, hudBtn[1] + 3,
                0xFFFFFFFF, false);
        String info = "Insert / Esc: kapat   •   v1.0";
        fontRendererObj.drawString(info, px + PW - 12 - fontRendererObj.getStringWidth(info), py + ph - 14,
                0xFF000000 | Ui.MUTED);
    }

    private void drawEditLayout(int mx, int my) {
        drawRect(0, 0, width, height, 0x30000000);
        String hint = "HUD öğelerini fareyle sürükle";
        fontRendererObj.drawStringWithShadow(hint, (width - fontRendererObj.getStringWidth(hint)) / 2f,
                height - 50, 0xFFFFFFFF);
        boolean hov = Ui.in(mx, my, doneR);
        drawRect(doneR[0], doneR[1], doneR[0] + doneR[2], doneR[1] + doneR[3], hov ? 0xFF3A6BEA : 0xFF2A55C8);
        Fx.drawSpacedCentered(fontRendererObj, "BİTTİ", doneR[0] + doneR[2] / 2f, doneR[1] + 5, 0xFFFFFFFF, false);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------ giriş
    private void setFromMouse(int mx) {
        float f = Ui.clamp((mx - (cx + 8)) / (float) (cw - 16), 0f, 1f);
        setFrac(dragRow, f);
    }

    private boolean startElementDrag(int mx, int my) {
        for (int i = Config.ELEMENTS.size() - 1; i >= 0; i--) {
            Config.HudElement el = Config.ELEMENTS.get(i);
            if (el.enabled && Ui.in(mx, my, el.x, el.y, el.boxW, el.boxH)) {
                MevdZSettingsGui.selected = el;
                dragEl = el;
                offX = mx - el.x;
                offY = my - el.y;
                return true;
            }
        }
        return false;
    }

    private void clickRows(int mx, int my) {
        if (!Ui.in(mx, my, cx, cy, cw, ch)) return;
        int off = Math.round(scroll);
        for (Row r : rows) {
            int ry = cy + 2 + r.y - off;
            if (!Ui.in(mx, my, cx, ry, cw, r.h)) continue;
            if (r.type == Row.TOGGLE) {
                toggleBool(r);
            } else if (r.type == Row.SLIDER) {
                dragRow = r;
                setFromMouse(mx);
            } else if (r.type == Row.BUTTON) {
                if (r.mode == 0 && open != null) {
                    resetMod(open);
                } else if (r.mode == 2) {
                    if (confirmReset) {
                        resetAll();
                        confirmReset = false;
                    } else {
                        confirmReset = true;
                    }
                }
            }
            return;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        layout();
        if (btn != 0) return;

        if (editLayout) {
            if (Ui.in(mx, my, doneR)) {
                editLayout = false;
                saveAll();
                return;
            }
            startElementDrag(mx, my);
            return;
        }

        if (Ui.in(mx, my, closeR)) {
            close();
            return;
        }
        for (int i = 0; i < 2; i++) {
            if (Ui.in(mx, my, tabR[i])) {
                if (tabIdx != i || open != null) {
                    tabIdx = i;
                    open = null;
                    layout();
                    buildRows();
                }
                return;
            }
        }
        if (Ui.in(mx, my, hudBtn)) {
            editLayout = true;
            return;
        }
        if (open != null) {
            if (Ui.in(mx, my, backR)) {
                open = null;
                layout();
                buildRows();
                return;
            }
            clickRows(mx, my);
            return;
        }
        if (tabIdx == 1) {
            clickRows(mx, my);
            return;
        }

        for (int i = 0; i < FILTERS.length; i++) {
            if (Ui.in(mx, my, chipR[i])) {
                filterIdx = i;
                scrollTarget = 0f;
                return;
            }
        }
        if (Ui.in(mx, my, cx, cy, cw, ch)) {
            List<Mod> list = filtered();
            int off = Math.round(scroll);
            for (int i = 0; i < list.size(); i++) {
                int[] r = cardRect(i, off);
                if (!Ui.in(mx, my, r)) continue;
                Mod m = list.get(i);
                if (my >= r[1] + r[3] - 19) {
                    toggleMod(m);
                } else {
                    open = m;
                    layout();
                    buildRows();
                }
                return;
            }
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        if (dragRow != null) {
            setFromMouse(mx);
        } else if (dragEl != null) {
            dragEl.x = (int) Ui.clamp(mx - offX, 0, width - 8);
            dragEl.y = (int) Ui.clamp(my - offY, 0, height - 8);
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (dragRow != null || dragEl != null) saveAll();
        dragRow = null;
        dragEl = null;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0 && !editLayout) {
            float max = Math.max(0f, contentH + 4 - ch);
            scrollTarget = Ui.clamp(scrollTarget - Integer.signum(d) * 24f, 0f, max);
        }
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE) {
            if (editLayout) {
                editLayout = false;
                saveAll();
            } else if (open != null) {
                open = null;
                layout();
                buildRows();
            } else {
                close();
            }
            return;
        }
        if (code == ExtraModule.keyMain.getKeyCode() && !ChatAllowedCharacters.isAllowedCharacter(c)) {
            close();
            return;
        }
        if (!editLayout && open == null && tabIdx == 0) {
            if (code == Keyboard.KEY_BACK) {
                if (search.length() > 0) search.setLength(search.length() - 1);
                scrollTarget = 0f;
            } else if (ChatAllowedCharacters.isAllowedCharacter(c) && search.length() < 16) {
                search.append(c);
                scrollTarget = 0f;
            }
        }
    }

    private void close() {
        saveAll();
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        editingHud = false;
        saveAll();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
