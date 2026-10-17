package dev.mevdz.client;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Yeni özellikler menüsü (Sağ Ctrl). Mevcut HUD ayar ekranıyla aynı görünümde;
 * satırlar Features.CATEGORIES listesinden otomatik oluşur.
 */
public class MevdZFeaturesGui extends GuiScreen {
    private static int tabIdx = 0;
    private static final int PW = 224, PH = 238;

    private final GuiScreen parent;
    private final long t0 = System.currentTimeMillis();
    private int px, py;
    private final List<int[]> rows = new ArrayList<int[]>();
    private int[] rClose, rHud, rReset;
    private int[][] tabs;
    private Features.Num dragNum;
    private int[] dragRect;

    public MevdZFeaturesGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        layout();
    }

    private Features.Category cat() {
        return Features.CATEGORIES.get(tabIdx);
    }

    private void layout() {
        px = (width - PW) / 2;
        py = Math.max(4, (height - PH) / 2);
        int x = px + 8, w = PW - 16;
        rClose = new int[]{px + PW - 16, py + 5, 11, 11};

        int n = Features.CATEGORIES.size();
        tabs = new int[n][];
        int tw = w / n;
        for (int i = 0; i < n; i++) tabs[i] = new int[]{x + i * tw, py + 26, tw - 2, 14};

        rows.clear();
        int y = py + 48;
        for (Features.Setting s : cat().list) {
            int h = s instanceof Features.Num ? 22 : 14;
            rows.add(new int[]{x, y, w, h});
            y += h + 4;
        }
        rHud = new int[]{x, py + PH - 22, w / 2 - 2, 14};
        rReset = new int[]{x + w / 2 + 2, py + PH - 22, w / 2 - 2, 14};
    }

    // ------------------------------------------------------------ çizim
    @Override
    public void drawScreen(int mx, int my, float pt) {
        layout();
        float t = (System.currentTimeMillis() - t0) / 1000f;

        if (mc.theWorld == null) {
            drawGradientRect(0, 0, width, height, 0xFF0A0B10, 0xFF171A2B);
        }

        drawRect(px, py, px + PW, py + PH, 0xEE0B0D16);
        Ui.outline(px, py, PW, PH, 0xFF222637);
        Ui.hGrad(px, py, PW, 2, Ui.CYAN, Ui.VIOLET, 255);
        Ui.animText(fontRendererObj, "MevdZ Özellikler", px + 8, py + 9, t);

        boolean hc = Ui.in(mx, my, rClose);
        drawRect(rClose[0], rClose[1], rClose[0] + 11, rClose[1] + 11, hc ? 0xFFE0405A : 0x30FFFFFF);
        fontRendererObj.drawString("x", rClose[0] + 3, rClose[1] + 2, 0xFFFFFFFF);

        for (int i = 0; i < tabs.length; i++) {
            int[] r = tabs[i];
            boolean sel = i == tabIdx;
            boolean hov = Ui.in(mx, my, r);
            drawRect(r[0], r[1], r[0] + r[2], r[1] + r[3],
                    sel ? 0x5019C3FF : (hov ? 0x30FFFFFF : 0x18FFFFFF));
            String name = Features.CATEGORIES.get(i).name;
            int lw = fontRendererObj.getStringWidth(name);
            fontRendererObj.drawString(name, r[0] + (r[2] - lw) / 2, r[1] + 3, 0xFFFFFFFF);
            if (sel) Ui.hGrad(r[0], r[1] + r[3] - 2, r[2], 2, Ui.CYAN, Ui.VIOLET, 255);
        }

        List<Features.Setting> list = cat().list;
        for (int i = 0; i < list.size(); i++) {
            Features.Setting s = list.get(i);
            int[] r = rows.get(i);
            if (s instanceof Features.Bool) {
                toggle(r, s.label, ((Features.Bool) s).value, mx, my);
            } else {
                Features.Num n = (Features.Num) s;
                slider(r, n.label, n.text(), n.frac(), n.fill);
            }
        }

        button(rHud, "HUD ayarları", mx, my);
        button(rReset, "Sıfırla", mx, my);

        String zk = Keyboard.getKeyName(FeatureModule.keyZoom.getKeyCode());
        String hint = "Zoom: " + zk + "  •  Freelook: fare orta tuş  •  Esc: kapat";
        fontRendererObj.drawStringWithShadow(hint, (width - fontRendererObj.getStringWidth(hint)) / 2f,
                height - 14, 0xFFB8C4E8);
    }

    private void button(int[] r, String label, int mx, int my) {
        drawRect(r[0], r[1], r[0] + r[2], r[1] + r[3], Ui.in(mx, my, r) ? 0x40FFFFFF : 0x20FFFFFF);
        fontRendererObj.drawString(label, r[0] + (r[2] - fontRendererObj.getStringWidth(label)) / 2,
                r[1] + 3, 0xFFDDE6FF);
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
    private void applyDrag(int mx) {
        float f = Ui.clamp((mx - (dragRect[0] + 4)) / (float) (dragRect[2] - 8), 0f, 1f);
        dragNum.setFrac(f);
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        layout();
        if (btn != 0) return;
        if (Ui.in(mx, my, rClose)) {
            close();
            return;
        }
        for (int i = 0; i < tabs.length; i++) {
            if (Ui.in(mx, my, tabs[i])) {
                tabIdx = i;
                layout();
                return;
            }
        }
        if (Ui.in(mx, my, rHud)) {
            Features.save();
            mc.displayGuiScreen(new MevdZSettingsGui(this));
            return;
        }
        if (Ui.in(mx, my, rReset)) {
            Features.resetCategory(cat());
            Features.save();
            return;
        }
        List<Features.Setting> list = cat().list;
        for (int i = 0; i < list.size(); i++) {
            int[] r = rows.get(i);
            if (!Ui.in(mx, my, r)) continue;
            Features.Setting s = list.get(i);
            if (s instanceof Features.Bool) {
                Features.Bool b = (Features.Bool) s;
                b.value = !b.value;
                Features.save();
            } else {
                dragNum = (Features.Num) s;
                dragRect = r;
                applyDrag(mx);
            }
            return;
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        if (dragNum != null) applyDrag(mx);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (dragNum != null) Features.save();
        dragNum = null;
        dragRect = null;
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE || code == FeatureModule.keyFeatures.getKeyCode()) close();
    }

    private void close() {
        Features.save();
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        Features.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
