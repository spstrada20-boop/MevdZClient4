package dev.mevdz.client;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.Random;

/** Animasyonlu MevdZ ana menüsü. */
public class MevdZMainMenu extends GuiScreen {
    private static final String[] LABELS = {
            "Tek Oyunculu", "Çok Oyunculu", "Oyun Ayarları", "MevdZ Client Ayarları", "Çıkış"};
    private static boolean announced = false;

    private final float[][] parts = new float[80][5]; // x, y, vx, vy, boyut
    private final float[] hover = new float[LABELS.length];
    private final Random rnd = new Random();
    private final long t0 = System.currentTimeMillis();

    private final int bw = 200, bh = 20, gap = 5;

    @Override
    public void initGui() {
        for (float[] p : parts) {
            p[0] = rnd.nextFloat() * width;
            p[1] = rnd.nextFloat() * height;
            p[2] = (rnd.nextFloat() - 0.5f) * 0.3f;
            p[3] = -(0.1f + rnd.nextFloat() * 0.5f);
            p[4] = 1 + rnd.nextInt(3);
        }
        if (!announced) {
            announced = true;
            System.out.println("MevdZ_READY");
        }
    }

    private int startY() {
        return Math.max(8, (height - 190) / 2);
    }

    private int buttonY(int i) {
        return startY() + 62 + i * (bh + gap);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        float t = (System.currentTimeMillis() - t0) / 1000f;

        // hareketli degrade arka plan
        int top = Ui.hsb(0.62f + 0.03f * (float) Math.sin(t * 0.4), 0.9f, 0.10f);
        int bot = Ui.hsb(0.74f + 0.05f * (float) Math.cos(t * 0.3), 0.85f, 0.26f);
        drawGradientRect(0, 0, width, height, 0xFF000000 | top, 0xFF000000 | bot);

        // kayan ızgara
        int gs = 24;
        int off = (int) ((t * 10f) % gs);
        for (int x = -off; x < width; x += gs) drawRect(x, 0, x + 1, height, 0x0A19C3FF);
        for (int y = -off; y < height; y += gs) drawRect(0, y, width, y + 1, 0x0A19C3FF);

        // tarama çizgisi
        int sy = (int) ((t * 60f) % (height + 80)) - 40;
        drawGradientRect(0, sy - 24, width, sy, 0x0019C3FF, 0x2219C3FF);
        drawGradientRect(0, sy, width, sy + 24, 0x2219C3FF, 0x0019C3FF);

        // parçacıklar
        for (int i = 0; i < parts.length; i++) {
            float[] p = parts[i];
            p[0] += p[2];
            p[1] += p[3];
            if (p[1] < -4) { p[1] = height + 4; p[0] = rnd.nextFloat() * width; }
            if (p[0] < -4) p[0] = width + 4;
            if (p[0] > width + 4) p[0] = -4;
            int a = 60 + (int) (60 * Math.sin(t * 2f + i));
            int c = (i % 2 == 0) ? Ui.CYAN : Ui.VIOLET;
            drawRect((int) p[0], (int) p[1], (int) (p[0] + p[4]), (int) (p[1] + p[4]), (a << 24) | c);
        }

        // başlık
        int sY = startY();
        String title = "MevdZ";
        float scale = 4f * (1f + 0.03f * (float) Math.sin(t * 2f));
        int tw = fontRendererObj.getStringWidth(title);
        GlStateManager.pushMatrix();
        GlStateManager.translate(width / 2f, sY, 0f);
        GlStateManager.scale(scale, scale, 1f);
        Ui.animText(fontRendererObj, title, -tw / 2f, 0f, t);
        GlStateManager.popMatrix();
        drawCenteredString(fontRendererObj, "L A U N C H E R", width / 2, sY + 42, 0xFF000000 | Ui.CYAN);
        Ui.hGrad(width / 2 - 50, sY + 55, 100, 1, Ui.CYAN, Ui.VIOLET, 200);

        // butonlar
        int bx = width / 2 - bw / 2;
        for (int i = 0; i < LABELS.length; i++) {
            int y = buttonY(i);
            boolean over = Ui.in(mx, my, bx, y, bw, bh);
            hover[i] += ((over ? 1f : 0f) - hover[i]) * 0.25f;
            float h = hover[i];

            drawRect(bx, y, bx + bw, y + bh, ((0x60 + (int) (0x40 * h)) << 24) | 0x0B0D16);
            drawRect(bx, y, bx + (int) (bw * h), y + bh, 0x3019C3FF);
            Ui.outline(bx, y, bw, bh, ((0x50 + (int) (0xA0 * h)) << 24) | Ui.lerp(0x222637, Ui.CYAN, h));
            drawRect(bx, y, bx + 2, y + bh, 0xFF000000 | Ui.lerp(Ui.VIOLET, Ui.CYAN, h));
            drawCenteredString(fontRendererObj, LABELS[i], width / 2 + (int) (h * 3), y + (bh - 8) / 2,
                    0xFF000000 | Ui.lerp(0xB8C4E8, 0xFFFFFF, h));
        }

        // alt bilgi
        fontRendererObj.drawStringWithShadow("MevdZ Launcher", 6, height - 12, 0xFF000000 | Ui.MUTED);
        String ver = "Minecraft 1.8.9";
        fontRendererObj.drawStringWithShadow(ver, width - fontRendererObj.getStringWidth(ver) - 6,
                height - 12, 0xFF000000 | Ui.MUTED);
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        if (btn != 0) return;
        int bx = width / 2 - bw / 2;
        for (int i = 0; i < LABELS.length; i++) {
            if (Ui.in(mx, my, bx, buttonY(i), bw, bh)) {
                mc.getSoundHandler().playSound(
                        PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
                switch (i) {
                    case 0: mc.displayGuiScreen(new GuiSelectWorld(this)); break;
                    case 1: mc.displayGuiScreen(new GuiMultiplayer(this)); break;
                    case 2: mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)); break;
                    case 3: mc.displayGuiScreen(new MevdZMenu(this)); break;
                    default: mc.shutdown(); break;
                }
                return;
            }
        }
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
