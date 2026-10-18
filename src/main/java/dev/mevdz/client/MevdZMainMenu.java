package dev.mevdz.client;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.Random;

/** MevdZ ana menüsü: ortada logo, birkaç geniş buton, altta küçük simge sırası. */
public class MevdZMainMenu extends GuiScreen {
    private static final String[] MAIN = {"TEK OYUNCULU", "ÇOK OYUNCULU", "ÇIKIŞ"};
    private static final String[] TIPS = {"MEVDZ AYARLARI", "OYUN AYARLARI", "DİL", "GÖRÜNÜM"};
    private static boolean announced = false;

    private final float[][] parts = new float[60][5]; // x, y, vx, vy, boyut
    private final float[] hover = new float[MAIN.length];
    private final float[] ihover = new float[TIPS.length];
    private final Random rnd = new Random();
    private final long t0 = System.currentTimeMillis();

    private final int bw = 190, bh = 20, gap = 6, isz = 22, igap = 6;

    @Override
    public void initGui() {
        for (float[] p : parts) {
            p[0] = rnd.nextFloat() * width;
            p[1] = rnd.nextFloat() * height;
            p[2] = (rnd.nextFloat() - 0.5f) * 0.25f;
            p[3] = -(0.08f + rnd.nextFloat() * 0.4f);
            p[4] = 1 + rnd.nextInt(2);
        }
        if (!announced) {
            announced = true;
            System.out.println("MevdZ_READY");
        }
    }

    private int topY() {
        return Math.max(12, (height - 200) / 2 - 8);
    }

    private int buttonY(int i) {
        return topY() + 90 + i * (bh + gap);
    }

    private int iconX(int i) {
        int total = TIPS.length * isz + (TIPS.length - 1) * igap;
        return width / 2 - total / 2 + i * (isz + igap);
    }

    private int iconY() {
        return height - 36;
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        Fx.pxScale = new ScaledResolution(mc).getScaleFactor();
        float t = (System.currentTimeMillis() - t0) / 1000f;

        // arka plan: koyu degrade + yumuşak, yavaş kayan ışık lekeleri
        int top = Ui.hsb(0.62f + 0.03f * (float) Math.sin(t * 0.4), 0.9f, 0.09f);
        int bot = Ui.hsb(0.74f + 0.05f * (float) Math.cos(t * 0.3), 0.85f, 0.22f);
        drawGradientRect(0, 0, width, height, 0xFF000000 | top, 0xFF000000 | bot);
        float big = Math.max(width, height);
        Fx.orb(width * (0.25f + 0.10f * (float) Math.sin(t * 0.23f)),
                height * (0.35f + 0.10f * (float) Math.cos(t * 0.19f)), big * 0.38f, Ui.CYAN, 70);
        Fx.orb(width * (0.75f + 0.08f * (float) Math.cos(t * 0.21f)),
                height * (0.60f + 0.10f * (float) Math.sin(t * 0.17f)), big * 0.42f, Ui.VIOLET, 80);
        Fx.orb(width * (0.50f + 0.15f * (float) Math.sin(t * 0.13f)),
                height * (0.85f + 0.05f * (float) Math.cos(t * 0.27f)), big * 0.30f, 0xFF4D8D, 45);

        // toz parçacıkları
        for (int i = 0; i < parts.length; i++) {
            float[] p = parts[i];
            p[0] += p[2];
            p[1] += p[3];
            if (p[1] < -4) { p[1] = height + 4; p[0] = rnd.nextFloat() * width; }
            if (p[0] < -4) p[0] = width + 4;
            if (p[0] > width + 4) p[0] = -4;
            int a = 30 + (int) (30 * Math.sin(t * 2f + i));
            drawRect((int) p[0], (int) p[1], (int) (p[0] + p[4]), (int) (p[1] + p[4]),
                    (a << 24) | ((i % 2 == 0) ? Ui.CYAN : Ui.VIOLET));
        }
        drawGradientRect(0, 0, width, height / 3, 0x66000000, 0x00000000);
        drawGradientRect(0, height * 2 / 3, width, height, 0x00000000, 0x77000000);

        // logo
        float lx = width / 2f, ly = topY() + 26;
        int gc = 0xFF000000 | Ui.lerp(Ui.CYAN, Ui.VIOLET, 0.5f + 0.5f * (float) Math.sin(t * 1.3f));
        Fx.orb(lx, ly, 56, Ui.CYAN, 110);
        Fx.poly(lx, ly, 24, 24, 6, 0.5236f, 2f, gc);
        Fx.poly(lx, ly, 18, 18, 6, 0.5236f, 1f, 0x66FFFFFF);
        GlStateManager.pushMatrix();
        GlStateManager.translate(lx, ly, 0f);
        GlStateManager.scale(2.4f, 2.4f, 1f);
        Ui.animText(fontRendererObj, "M", -fontRendererObj.getStringWidth("M") / 2f, -4f, t);
        GlStateManager.popMatrix();

        // başlık
        GlStateManager.pushMatrix();
        GlStateManager.translate(lx, (float) (topY() + 64), 0f);
        GlStateManager.scale(1.4f, 1.4f, 1f);
        Fx.drawSpacedCentered(fontRendererObj, "MEVDZ CLIENT", 0f, 0f, 0xFFFFFFFF, true);
        GlStateManager.popMatrix();

        // ana butonlar
        int bx = width / 2 - bw / 2;
        for (int i = 0; i < MAIN.length; i++) {
            int y = buttonY(i);
            boolean over = Ui.in(mx, my, bx, y, bw, bh);
            hover[i] += ((over ? 1f : 0f) - hover[i]) * 0.25f;
            float h = hover[i];
            boolean red = i == MAIN.length - 1;

            int fill = red ? ((0x70 + (int) (0x30 * h)) << 24) | 0xC2303F
                    : ((0x55 + (int) (0x30 * h)) << 24) | 0x10131E;
            drawRect(bx, y, bx + bw, y + bh, fill);
            Ui.outline(bx, y, bw, bh, ((0x40 + (int) (0x90 * h)) << 24) | (red ? 0xFF6E7E : 0xFFFFFF));
            if (h > 0.02f) {
                int uw = (int) (bw * h);
                Ui.hGrad(bx + (bw - uw) / 2, y + bh - 2, uw, 2, Ui.CYAN, Ui.VIOLET, 255);
            }
            Fx.drawSpacedCentered(fontRendererObj, MAIN[i], width / 2f + h * 2f, y + (bh - 8) / 2f,
                    0xFF000000 | Ui.lerp(0xC8D2F0, 0xFFFFFF, h), false);
        }

        // alt simge sırası
        String tip = null;
        int tipX = 0;
        for (int i = 0; i < TIPS.length; i++) {
            int x = iconX(i), y = iconY();
            boolean over = Ui.in(mx, my, x, y, isz, isz);
            ihover[i] += ((over ? 1f : 0f) - ihover[i]) * 0.25f;
            float h = ihover[i];
            drawRect(x, y, x + isz, y + isz, ((0x55 + (int) (0x30 * h)) << 24) | 0x10131E);
            Ui.outline(x, y, isz, isz, ((0x40 + (int) (0x90 * h)) << 24) | Ui.lerp(0xFFFFFF, Ui.CYAN, h));
            drawPictogram(i, x + isz / 2f, y + isz / 2f, 0xFF000000 | Ui.lerp(0xB8C4E8, 0xFFFFFF, h));
            if (over) {
                tip = TIPS[i];
                tipX = x + isz / 2;
            }
        }
        if (tip != null) {
            int tw = Fx.spacedWidth(fontRendererObj, tip) + 12;
            int tx = tipX - tw / 2, ty = iconY() - 18;
            drawRect(tx, ty, tx + tw, ty + 14, 0xE00B0D16);
            Ui.outline(tx, ty, tw, 14, 0xFF222637);
            Fx.drawSpacedCentered(fontRendererObj, tip, tipX, ty + 3, 0xFFDDE6FF, false);
        }

        // alt bilgi
        fontRendererObj.drawStringWithShadow("MevdZ Launcher", 6, height - 12, 0xFF000000 | Ui.MUTED);
        String ver = "Minecraft 1.8.9";
        fontRendererObj.drawStringWithShadow(ver, width - fontRendererObj.getStringWidth(ver) - 6,
                height - 12, 0xFF000000 | Ui.MUTED);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawPictogram(int id, float cx, float cy, int c) {
        switch (id) {
            case 0: // MevdZ ayarları: dişli
                Fx.ring(cx, cy, 5.5f, 1.6f, c);
                Fx.disc(cx, cy, 1.8f, c);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    Fx.line((float) (cx + Math.cos(a) * 6.5), (float) (cy + Math.sin(a) * 6.5),
                            (float) (cx + Math.cos(a) * 8.5), (float) (cy + Math.sin(a) * 8.5), 1.6f, c);
                }
                break;
            case 1: // oyun ayarları: kaydırıcılar
                Fx.line(cx - 7, cy - 5, cx + 7, cy - 5, 1.2f, c);
                Fx.line(cx - 7, cy, cx + 7, cy, 1.2f, c);
                Fx.line(cx - 7, cy + 5, cx + 7, cy + 5, 1.2f, c);
                Fx.disc(cx - 2, cy - 5, 2f, c);
                Fx.disc(cx + 3, cy, 2f, c);
                Fx.disc(cx - 3, cy + 5, 2f, c);
                break;
            case 2: // dil: küre
                Fx.ring(cx, cy, 7f, 1.3f, c);
                Fx.poly(cx, cy, 3f, 7f, 20, 0f, 1.1f, c);
                Fx.line(cx - 7, cy, cx + 7, cy, 1.1f, c);
                break;
            default: // görünüm: tişört
                drawRect((int) (cx - 4), (int) (cy - 4), (int) (cx + 4), (int) (cy + 7), c);
                drawRect((int) (cx - 8), (int) (cy - 4), (int) (cx - 4), (int) (cy), c);
                drawRect((int) (cx + 4), (int) (cy - 4), (int) (cx + 8), (int) (cy), c);
                break;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        if (btn != 0) return;
        int bx = width / 2 - bw / 2;
        for (int i = 0; i < MAIN.length; i++) {
            if (Ui.in(mx, my, bx, buttonY(i), bw, bh)) {
                click();
                switch (i) {
                    case 0: mc.displayGuiScreen(new GuiSelectWorld(this)); break;
                    case 1: mc.displayGuiScreen(new GuiMultiplayer(this)); break;
                    default: mc.shutdown(); break;
                }
                return;
            }
        }
        for (int i = 0; i < TIPS.length; i++) {
            if (Ui.in(mx, my, iconX(i), iconY(), isz, isz)) {
                click();
                switch (i) {
                    case 0: mc.displayGuiScreen(new MevdZMenu(this)); break;
                    case 1: mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)); break;
                    case 2: mc.displayGuiScreen(new GuiLanguage(this, mc.gameSettings, mc.getLanguageManager())); break;
                    default: mc.displayGuiScreen(new GuiCustomizeSkin(this)); break;
                }
                return;
            }
        }
    }

    private void click() {
        mc.getSoundHandler().playSound(
                PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }

    @Override
    protected void keyTyped(char c, int code) {
        // Esc ile ana menüden çıkılmasın
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
