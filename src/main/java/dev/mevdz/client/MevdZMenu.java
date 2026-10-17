package dev.mevdz.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Birleşik ana ayar menüsü (Insert). Sol tarafta sekmeler: HUD + Features.CATEGORIES'teki
 * tüm kategoriler. Sağda kaydırılabilir ayar satırları. Yeni ayar eklenince otomatik görünür.
 * HUD sekmesindeyken HUD öğelerini panelin dışından fareyle sürükleyebilirsin.
 */
public class MevdZMenu extends GuiScreen {
    /** HUD sekmesi açıkken true: Hud, öğelerin çerçevesini çizer. */
    public static boolean editingHud = false;

    private static int tabIdx = 0;
    private static final int PW = 380, PH_MAX = 262, SW = 88;

    private static final class Row {
        static final int HEADER = 0, TOGGLE = 1, SLIDER = 2, CHIPS = 3, SWATCH = 4, BUTTON = 5, HINT = 6;
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

    private final GuiScreen parent;
    private final long t0 = System.currentTimeMillis();
    private final List<Row> rows = new ArrayList<Row>();
    private final float[] tabHover = new float[1 + Features.CATEGORIES.size()];

    private int px, py, ph, cx, cy, cw, ch, contentH;
    private float scroll, scrollTarget;
    private Row dragRow;
    private Config.HudElement dragEl;
    private int offX, offY;

    public MevdZMenu(GuiScreen parent) {
        this.parent = parent;
        if (MevdZSettingsGui.selected == null) MevdZSettingsGui.selected = Config.ELEMENTS.get(0);
        if (tabIdx >= tabHover.length) tabIdx = 0;
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
        cx = px + SW + 10;
        cy = py + 34;
        cw = PW - SW - 20;
        ch = ph - 34 - 22;
    }

    private int tabH() {
        return Math.max(12, Math.min(20, (ph - 70) / tabHover.length));
    }

    private String tabName(int i) {
        return i == 0 ? "HUD" : Features.CATEGORIES.get(i - 1).name;
    }

    private boolean tabActive(int i) {
        if (i == 0) return false;
        List<Features.Setting> l = Features.CATEGORIES.get(i - 1).list;
        return !l.isEmpty() && l.get(0) instanceof Features.Bool && ((Features.Bool) l.get(0)).value;
    }

    private Row add(int type, String label, Features.Setting fs, int mode) {
        Row r = new Row(type, label);
        r.fs = fs;
        r.mode = mode;
        rows.add(r);
        return r;
    }

    private static int heightOf(int type) {
        switch (type) {
            case Row.HEADER: return 12;
            case Row.TOGGLE: return 18;
            case Row.SLIDER: return 26;
            case Row.CHIPS: return 18;
            case Row.SWATCH: return 18;
            case Row.BUTTON: return 18;
            default: return 12;
        }
    }

    private void buildRows() {
        rows.clear();
        scroll = 0f;
        scrollTarget = 0f;
        if (tabIdx == 0) {
            add(Row.HEADER, "GENEL", null, -1);
            add(Row.TOGGLE, "Otomatik Koşu", null, 0);
            add(Row.HEADER, "HUD ÖĞESİ", null, -1);
            add(Row.CHIPS, "", null, -1);
            add(Row.TOGGLE, "Göster", null, 1);
            add(Row.TOGGLE, "Arka plan", null, 2);
            add(Row.TOGGLE, "Gökkuşağı rengi", null, 3);
            add(Row.SLIDER, "Boyut", null, 0);
            add(Row.SLIDER, "Kırmızı", null, 1);
            add(Row.SLIDER, "Yeşil", null, 2);
            add(Row.SLIDER, "Mavi", null, 3);
            add(Row.SWATCH, "", null, -1);
            add(Row.HINT, "Öğeleri panelin dışından fareyle sürükle.", null, -1);
        } else {
            Features.Category c = Features.CATEGORIES.get(tabIdx - 1);
            for (Features.Setting s : c.list) {
                add(s instanceof Features.Bool ? Row.TOGGLE : Row.SLIDER, s.label, s, -1);
            }
            add(Row.BUTTON, "Bu sekmeyi sıfırla", null, 0);
        }
        int y = 0;
        for (Row r : rows) {
            r.h = heightOf(r.type);
            r.y = y;
            y += r.h + 4;
        }
        contentH = y;
    }

    // ------------------------------------------------------------ değer erişimi
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

    private void saveAll() {
        Features.save();
        Config.save();
    }

    // ------------------------------------------------------------ çizim
    @Override
    public void drawScreen(int mx, int my, float pt) {
        layout();
        editingHud = tabIdx == 0;
        long now = System.currentTimeMillis();
        float t = (now - t0) / 1000f;
        float o = Math.min(1f, (now - t0) / 240f);
        float e = 1f - (1f - o) * (1f - o) * (1f - o);
        int slide = (int) (16 * (1f - e));

        if (mc.theWorld == null) {
            drawGradientRect(0, 0, width, height, 0xFF0A0B10, 0xFF171A2B);
            Hud.renderAll();
        }
        drawRect(0, 0, width, height, ((int) (0x45 * e)) << 24);

        GlStateManager.pushMatrix();
        GlStateManager.translate(0f, (float) slide, 0f);

        // dış parıltı
        int glow = Ui.lerp(Ui.CYAN, Ui.VIOLET, 0.5f + 0.5f * (float) Math.sin(t * 1.2f));
        for (int i = 6; i >= 1; i--) {
            Ui.outline(px - i, py - i, PW + 2 * i, ph + 2 * i, (Math.max(2, 48 / (i * i)) << 24) | glow);
        }

        // panel
        drawRect(px, py, px + PW, py + ph, 0xF0090B14);
        drawGradientRect(px + SW + 1, py + ph - 60, px + PW, py + ph, 0x0019C3FF, 0x1419C3FF);
        drawRect(px, py, px + SW, py + ph, 0xF0060810);
        drawRect(px + SW, py, px + SW + 1, py + ph, 0xFF1B1F31);
        Ui.outline(px, py, PW, ph, 0xFF222637);
        Ui.hGrad(px, py, PW, 2, Ui.CYAN, Ui.VIOLET, 255);

        // marka
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) (px + 10), (float) (py + 9), 0f);
        GlStateManager.scale(1.6f, 1.6f, 1f);
        Ui.animText(fontRendererObj, "MevdZ", 0f, 0f, t);
        GlStateManager.popMatrix();
        fontRendererObj.drawString("C L I E N T", px + 11, py + 26, 0xFF000000 | Ui.MUTED);
        Ui.hGrad(px + 8, py + 37, SW - 16, 1, Ui.CYAN, Ui.VIOLET, 140);

        // sekmeler
        int eh = tabH();
        for (int i = 0; i < tabHover.length; i++) {
            int ex = px + 4, ey = py + 44 + i * eh, ew = SW - 8, eHt = eh - 2;
            boolean hov = Ui.in(mx, my, ex, ey, ew, eHt);
            boolean sel = i == tabIdx;
            tabHover[i] += ((hov ? 1f : 0f) - tabHover[i]) * 0.25f;
            float h = tabHover[i];
            drawRect(ex, ey, ex + ew, ey + eHt, ((0x10 + (int) (0x28 * h)) << 24) | 0xFFFFFF);
            if (sel) {
                drawRect(ex, ey, ex + ew, ey + eHt, 0x3019C3FF);
                drawRect(ex, ey, ex + 2, ey + eHt, 0xFF000000 | Ui.CYAN);
            }
            int icon = Ui.hsb(0.5f + 0.6f * i / tabHover.length, 0.6f, 1f);
            drawRect(ex + 6, ey + (eHt - 4) / 2, ex + 10, ey + (eHt - 4) / 2 + 4, 0xFF000000 | icon);
            fontRendererObj.drawString(tabName(i), ex + 15 + (int) (h * 2), ey + (eHt - 8) / 2,
                    0xFF000000 | Ui.lerp(0xB8C4E8, 0xFFFFFF, sel ? 1f : h));
            if (tabActive(i)) {
                drawRect(ex + ew - 7, ey + (eHt - 4) / 2, ex + ew - 3, ey + (eHt - 4) / 2 + 4, 0xFF4DFF6A);
            }
        }

        // başlık + kapat
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) cx, (float) (py + 10), 0f);
        GlStateManager.scale(1.3f, 1.3f, 1f);
        fontRendererObj.drawStringWithShadow(tabName(tabIdx), 0f, 0f, 0xFFFFFFFF);
        GlStateManager.popMatrix();
        Ui.hGrad(cx, py + 30, cw, 1, Ui.CYAN, Ui.VIOLET, 120);
        boolean hc = Ui.in(mx, my, px + PW - 16, py + 6, 11, 11);
        drawRect(px + PW - 16, py + 6, px + PW - 5, py + 17, hc ? 0xFFE0405A : 0x30FFFFFF);
        fontRendererObj.drawString("x", px + PW - 13, py + 8, 0xFFFFFFFF);

        // içerik (kırpılmış, kaydırılabilir)
        scroll += (scrollTarget - scroll) * 0.3f;
        if (Math.abs(scrollTarget - scroll) < 0.1f) scroll = scrollTarget;
        int f = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(cx * f, mc.displayHeight - (cy + ch + slide) * f, cw * f, ch * f);
        boolean inC = Ui.in(mx, my, cx, cy, cw, ch);
        int off = Math.round(scroll);
        for (Row r : rows) {
            int ry = cy + 2 + r.y - off;
            if (ry + r.h < cy || ry > cy + ch) continue;
            drawRow(r, ry, mx, my, inC);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // kaydırma çubuğu
        if (contentH + 4 > ch) {
            float max = contentH + 4 - ch;
            int bh = Math.max(16, (int) (ch * (float) ch / (contentH + 4)));
            int by = cy + (int) ((ch - bh) * (scroll / max));
            drawRect(px + PW - 7, cy, px + PW - 5, cy + ch, 0x20FFFFFF);
            drawRect(px + PW - 7, by, px + PW - 5, by + bh, 0xAA19C3FF);
        }

        // alt bilgi
        drawRect(px + SW + 1, py + ph - 20, px + PW, py + ph - 19, 0xFF1B1F31);
        fontRendererObj.drawString("Insert / Esc: kapat   •   Tekerlek: kaydır", cx, py + ph - 13,
                0xFF000000 | Ui.MUTED);
        String ver = "v1.0";
        fontRendererObj.drawString(ver, px + PW - fontRendererObj.getStringWidth(ver) - 8, py + ph - 13,
                0xFF000000 | Ui.MUTED);

        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawRow(Row r, int ry, int mx, int my, boolean inC) {
        boolean hov = inC && Ui.in(mx, my, cx, ry, cw, r.h);
        r.hov += ((hov ? 1f : 0f) - r.hov) * 0.25f;

        switch (r.type) {
            case Row.HEADER: {
                fontRendererObj.drawString(r.label, cx + 2, ry + 2, 0xFF000000 | Ui.MUTED);
                drawRect(cx + fontRendererObj.getStringWidth(r.label) + 8, ry + 5, cx + cw - 2, ry + 6, 0x4019C3FF);
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
            case Row.CHIPS: {
                int n = Config.ELEMENTS.size();
                int w = cw / n;
                for (int i = 0; i < n; i++) {
                    Config.HudElement el = Config.ELEMENTS.get(i);
                    int x = cx + i * w;
                    boolean sel = el == MevdZSettingsGui.selected;
                    boolean hv = inC && Ui.in(mx, my, x, ry, w - 2, r.h);
                    drawRect(x, ry, x + w - 2, ry + r.h, sel ? 0x5019C3FF : (hv ? 0x30FFFFFF : 0x18FFFFFF));
                    int lw = fontRendererObj.getStringWidth(el.label);
                    fontRendererObj.drawString(el.label, x + (w - 2 - lw) / 2, ry + 5,
                            el.enabled ? 0xFFFFFFFF : 0xFF6F7896);
                    if (sel) Ui.hGrad(x, ry + r.h - 2, w - 2, 2, Ui.CYAN, Ui.VIOLET, 255);
                }
                break;
            }
            case Row.SWATCH: {
                Config.HudElement el = MevdZSettingsGui.selected;
                int bw = cw / 2 - 2;
                boolean h = inC && Ui.in(mx, my, cx, ry, bw, r.h);
                drawRect(cx, ry, cx + bw, ry + r.h, h ? 0x40FFFFFF : 0x20FFFFFF);
                String rs = "Öğeyi sıfırla";
                fontRendererObj.drawString(rs, cx + (bw - fontRendererObj.getStringWidth(rs)) / 2,
                        ry + (r.h - 8) / 2, 0xFFDDE6FF);
                int sx = cx + cw / 2 + 2;
                drawRect(sx, ry, cx + cw, ry + r.h, 0xFF000000 | el.currentColor());
                Ui.outline(sx, ry, cx + cw - sx, r.h, 0xFF222637);
                break;
            }
            case Row.BUTTON: {
                drawRect(cx, ry, cx + cw, ry + r.h, hov ? 0x40FFFFFF : 0x20FFFFFF);
                fontRendererObj.drawString(r.label, cx + (cw - fontRendererObj.getStringWidth(r.label)) / 2,
                        ry + (r.h - 8) / 2, 0xFFDDE6FF);
                break;
            }
            default: {
                fontRendererObj.drawString(r.label, cx + 2, ry + 2, 0xFF000000 | Ui.MUTED);
                break;
            }
        }
    }

    // ------------------------------------------------------------ giriş
    private void setFromMouse(int mx) {
        float f = Ui.clamp((mx - (cx + 8)) / (float) (cw - 16), 0f, 1f);
        setFrac(dragRow, f);
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        layout();
        if (btn != 0) return;
        if (Ui.in(mx, my, px + PW - 16, py + 6, 11, 11)) {
            close();
            return;
        }
        int eh = tabH();
        for (int i = 0; i < tabHover.length; i++) {
            if (Ui.in(mx, my, px + 4, py + 44 + i * eh, SW - 8, eh - 2)) {
                if (tabIdx != i) {
                    tabIdx = i;
                    buildRows();
                }
                return;
            }
        }
        if (Ui.in(mx, my, cx, cy, cw, ch)) {
            int off = Math.round(scroll);
            for (Row r : rows) {
                int ry = cy + 2 + r.y - off;
                if (!Ui.in(mx, my, cx, ry, cw, r.h)) continue;
                if (r.type == Row.TOGGLE) {
                    toggleBool(r);
                } else if (r.type == Row.SLIDER) {
                    dragRow = r;
                    setFromMouse(mx);
                } else if (r.type == Row.CHIPS) {
                    int n = Config.ELEMENTS.size();
                    int i = (int) Ui.clamp((mx - cx) * n / cw, 0, n - 1);
                    MevdZSettingsGui.selected = Config.ELEMENTS.get(i);
                } else if (r.type == Row.SWATCH) {
                    if (mx < cx + cw / 2) {
                        MevdZSettingsGui.selected.reset();
                        saveAll();
                    }
                } else if (r.type == Row.BUTTON) {
                    if (tabIdx > 0) {
                        Features.resetCategory(Features.CATEGORIES.get(tabIdx - 1));
                        saveAll();
                    }
                }
                return;
            }
            return;
        }
        if (Ui.in(mx, my, px, py, PW, ph)) return;

        // panelin dışı: HUD öğesi sürükle
        if (tabIdx == 0) {
            for (int i = Config.ELEMENTS.size() - 1; i >= 0; i--) {
                Config.HudElement el = Config.ELEMENTS.get(i);
                if (el.enabled && Ui.in(mx, my, el.x, el.y, el.boxW, el.boxH)) {
                    MevdZSettingsGui.selected = el;
                    dragEl = el;
                    offX = mx - el.x;
                    offY = my - el.y;
                    return;
                }
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
        if (d != 0) {
            float max = Math.max(0f, contentH + 4 - ch);
            scrollTarget = Ui.clamp(scrollTarget - Integer.signum(d) * 24f, 0f, max);
        }
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE || code == ExtraModule.keyMain.getKeyCode()) close();
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
