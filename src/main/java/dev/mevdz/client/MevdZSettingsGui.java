package dev.mevdz.client;

import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * HUD ayar ekranı: modül seç, aç/kapat, renk (R/G/B), boyut, arka plan, gökkuşağı.
 * HUD öğelerini fareyle sürükleyerek konumlandır.
 */
public class MevdZSettingsGui extends GuiScreen {
    public static Config.HudElement selected = null;

    private static final int PW = 224, PH = 206;
    private final GuiScreen parent;
    private final long t0 = System.currentTimeMillis();

    private int px, py;
    private int dragSlider = -1; // 0 boyut, 1 R, 2 G, 3 B
    private Config.HudElement dragEl;
    private int offX, offY;

    private final int[][] tab = new int[5][];
    private int[] rClose, rAuto, rShow, rBg, rChroma, rSize, rR, rG, rB, rReset, rSwatch;

    public MevdZSettingsGui(GuiScreen parent) {
        this.parent = parent;
        if (selected == null) selected = Config.ELEMENTS.get(0);
    }

    @Override
    public void initGui() {
        layout();
    }

    private void layout() {
        px = Math.max(4, width - PW - 8);
        py = Math.max(4, (height - PH) / 2);
        int x = px + 8, w = PW - 16;
        rClose = new int[]{px + PW - 16, py + 5, 11, 11};
        int tw = w / 5;
        for (int i = 0; i < 5; i++) tab[i] = new int[]{x + i * tw, py + 26, tw - 2, 14};
        rAuto = new int[]{x, py + 46, w, 14};
        rShow = new int[]{x, py + 72, w, 14};
        rBg = new int[]{x, py + 88, w, 14};
        rChroma = new int[]{x, py + 104, w, 14};
        rSize = new int[]{x, py + 123, w, 14};
        rR = new int[]{x, py + 139, w, 14};
        rG = new int[]{x, py + 155, w, 14};
        rB = new int[]{x, py + 171, w, 14};
        rReset = new int[]{x, py + 189, w / 2 - 2, 14};
        rSwatch = new int[]{x + w / 2 + 2, py + 189, w / 2 - 2, 14};
    }

    // ------------------------------------------------------------ çizim
    @Override
    public void drawScreen(int mx, int my, float pt) {
        layout();
        float t = (System.currentTimeMillis() - t0) / 1000f;

        if (mc.theWorld == null) {
            drawGradientRect(0, 0, width, height, 0xFF0A0B10, 0xFF171A2B);
            Hud.renderAll();
        }

        drawRect(px, py, px + PW, py + PH, 0xEE0B0D16);
        Ui.outline(px, py, PW, PH, 0xFF222637);
        Ui.hGrad(px, py, PW, 2, Ui.CYAN, Ui.VIOLET, 255);
        Ui.animText(fontRendererObj, "MevdZ Client", px + 8, py + 9, t);

        boolean hc = Ui.in(mx, my, rClose);
        drawRect(rClose[0], rClose[1], rClose[0] + 11, rClose[1] + 11, hc ? 0xFFE0405A : 0x30FFFFFF);
        fontRendererObj.drawString("x", rClose[0] + 3, rClose[1] + 2, 0xFFFFFFFF);

        // sekmeler
        for (int i = 0; i < 5; i++) {
            Config.HudElement e = Config.ELEMENTS.get(i);
            int[] r = tab[i];
            boolean sel = e == selected;
            boolean hov = Ui.in(mx, my, r);
            drawRect(r[0], r[1], r[0] + r[2], r[1] + r[3],
                    sel ? 0x5019C3FF : (hov ? 0x30FFFFFF : 0x18FFFFFF));
            int lw = fontRendererObj.getStringWidth(e.label);
            fontRendererObj.drawString(e.label, r[0] + (r[2] - lw) / 2, r[1] + 3,
                    e.enabled ? 0xFFFFFFFF : 0xFF6F7896);
            if (sel) Ui.hGrad(r[0], r[1] + r[3] - 2, r[2], 2, Ui.CYAN, Ui.VIOLET, 255);
        }

        toggle(rAuto, "Otomatik Koşu", Config.autoSprint, mx, my);

        Config.HudElement el = selected;
        fontRendererObj.drawString(el.label + " ayarları", px + 10, py + 63, 0xFF6F7896);
        toggle(rShow, "Göster", el.enabled, mx, my);
        toggle(rBg, "Arka plan", el.background, mx, my);
        toggle(rChroma, "Gökkuşağı rengi", el.chroma, mx, my);

        slider(rSize, "Boyut", (Math.round(el.scale * 100) / 100f) + "x",
                (el.scale - 0.5f) / 2.5f, 0x19C3FF);
        slider(rR, "Kırmızı", String.valueOf((el.color >> 16) & 255), ((el.color >> 16) & 255) / 255f, 0xFF4D4D);
        slider(rG, "Yeşil", String.valueOf((el.color >> 8) & 255), ((el.color >> 8) & 255) / 255f, 0x4DFF6A);
        slider(rB, "Mavi", String.valueOf(el.color & 255), (el.color & 255) / 255f, 0x4D8DFF);

        boolean hr = Ui.in(mx, my, rReset);
        drawRect(rReset[0], rReset[1], rReset[0] + rReset[2], rReset[1] + rReset[3], hr ? 0x40FFFFFF : 0x20FFFFFF);
        String rs = "Sıfırla";
        fontRendererObj.drawString(rs, rReset[0] + (rReset[2] - fontRendererObj.getStringWidth(rs)) / 2,
                rReset[1] + 3, 0xFFDDE6FF);
        drawRect(rSwatch[0], rSwatch[1], rSwatch[0] + rSwatch[2], rSwatch[1] + rSwatch[3],
                0xFF000000 | el.currentColor());
        Ui.outline(rSwatch[0], rSwatch[1], rSwatch[2], rSwatch[3], 0xFF222637);

        String hint = "HUD öğelerini fareyle sürükle  •  Esc: kapat";
        fontRendererObj.drawStringWithShadow(hint, (width - fontRendererObj.getStringWidth(hint)) / 2f,
                height - 14, 0xFFB8C4E8);
    }

    private void toggle(int[] r, String label, boolean on, int mx, int my) {
        drawRect(r[0], r[1], r[0] + r[2], r[1] + r[3], Ui.in(mx, my, r) ? 0x30FFFFFF : 0x18FFFFFF);
        fontRendererObj.drawString(label, r[0] + 4, r[1] + 3, 0xFFDDE6FF);
        int tx = r[0] + r[2] - 26, ty = r[1] + 3;
        drawRect(tx, ty, tx + 22, ty + 8, on ? (0xFF000000 | Ui.CYAN) : 0xFF2A2F42);
        int kx = on ? tx + 14 : tx;
        drawRect(kx, ty, kx + 8, ty + 8, 0xFFFFFFFF);
    }

    private void slider(int[] r, String label, String val, float frac, int fill) {
        frac = Ui.clamp(frac, 0f, 1f);
        fontRendererObj.drawString(label, r[0] + 4, r[1], 0xFFB8C4E8);
        fontRendererObj.drawString(val, r[0] + r[2] - fontRendererObj.getStringWidth(val) - 4, r[1], 0xFFFFFFFF);
        int ty = r[1] + 10, tw = r[2] - 8;
        drawRect(r[0] + 4, ty, r[0] + 4 + tw, ty + 3, 0xFF222637);
        int fw = (int) (tw * frac);
        drawRect(r[0] + 4, ty, r[0] + 4 + fw, ty + 3, 0xFF000000 | fill);
        drawRect(r[0] + 2 + fw, ty - 2, r[0] + 6 + fw, ty + 5, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------ giriş
    private void applySlider(int mx) {
        int[] r = dragSlider == 0 ? rSize : dragSlider == 1 ? rR : dragSlider == 2 ? rG : rB;
        float f = Ui.clamp((mx - (r[0] + 4)) / (float) (r[2] - 8), 0f, 1f);
        Config.HudElement el = selected;
        if (dragSlider == 0) {
            el.scale = Math.round((0.5f + f * 2.5f) * 20f) / 20f;
        } else {
            int rr = (el.color >> 16) & 255, gg = (el.color >> 8) & 255, bb = el.color & 255;
            int v = Math.round(f * 255f);
            if (dragSlider == 1) rr = v;
            else if (dragSlider == 2) gg = v;
            else bb = v;
            el.color = (rr << 16) | (gg << 8) | bb;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        layout();
        if (btn != 0) return;
        if (Ui.in(mx, my, rClose)) { close(); return; }
        for (int i = 0; i < 5; i++) {
            if (Ui.in(mx, my, tab[i])) { selected = Config.ELEMENTS.get(i); return; }
        }
        if (Ui.in(mx, my, rAuto)) { Config.autoSprint = !Config.autoSprint; Config.save(); return; }
        Config.HudElement el = selected;
        if (Ui.in(mx, my, rShow)) { el.enabled = !el.enabled; Config.save(); return; }
        if (Ui.in(mx, my, rBg)) { el.background = !el.background; Config.save(); return; }
        if (Ui.in(mx, my, rChroma)) { el.chroma = !el.chroma; Config.save(); return; }
        if (Ui.in(mx, my, rSize)) { dragSlider = 0; applySlider(mx); return; }
        if (Ui.in(mx, my, rR)) { dragSlider = 1; applySlider(mx); return; }
        if (Ui.in(mx, my, rG)) { dragSlider = 2; applySlider(mx); return; }
        if (Ui.in(mx, my, rB)) { dragSlider = 3; applySlider(mx); return; }
        if (Ui.in(mx, my, rReset)) { el.reset(); Config.save(); return; }
        if (Ui.in(mx, my, px, py, PW, PH)) return; // panelin içi, boş alan

        for (int i = Config.ELEMENTS.size() - 1; i >= 0; i--) {
            Config.HudElement e = Config.ELEMENTS.get(i);
            if (e.enabled && Ui.in(mx, my, e.x, e.y, e.boxW, e.boxH)) {
                selected = e;
                dragEl = e;
                offX = mx - e.x;
                offY = my - e.y;
                return;
            }
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        if (dragSlider >= 0) {
            applySlider(mx);
        } else if (dragEl != null) {
            dragEl.x = (int) Ui.clamp(mx - offX, 0, width - 8);
            dragEl.y = (int) Ui.clamp(my - offY, 0, height - 8);
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (dragSlider >= 0 || dragEl != null) Config.save();
        dragSlider = -1;
        dragEl = null;
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (code == 1 || code == MevdZClient.keyMenu.getKeyCode()) close();
    }

    private void close() {
        Config.save();
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        Config.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
