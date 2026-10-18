package dev.mevdz.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Menü çizimi için küçük GL yardımcıları (yeni menüler kullanır). */
public class Fx {
    /** Ekranın GUI ölçek çarpanı; çizgi kalınlıklarını piksele çevirmek için menüler günceller. */
    public static float pxScale = 2f;

    private static void on() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
    }

    private static void off() {
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    public static void color(int argb) {
        GlStateManager.color(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f,
                (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
    }

    public static void line(float x1, float y1, float x2, float y2, float w, int argb) {
        on();
        color(argb);
        GL11.glLineWidth(Math.max(1f, w * pxScale));
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        off();
    }

    /** Çok kenarlı çizgi (sides büyükse elips/halka, 6 ise altıgen). rot radyan. */
    public static void poly(float cx, float cy, float rx, float ry, int sides, float rot, float w, int argb) {
        on();
        color(argb);
        GL11.glLineWidth(Math.max(1f, w * pxScale));
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < sides; i++) {
            double a = rot + i * 2.0 * Math.PI / sides;
            GL11.glVertex2f((float) (cx + Math.cos(a) * rx), (float) (cy + Math.sin(a) * ry));
        }
        GL11.glEnd();
        off();
    }

    public static void ring(float cx, float cy, float r, float w, int argb) {
        poly(cx, cy, r, r, 28, 0f, w, argb);
    }

    public static void disc(float cx, float cy, float r, int argb) {
        on();
        color(argb);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= 24; i++) {
            double a = i * 2.0 * Math.PI / 24;
            GL11.glVertex2f((float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r));
        }
        GL11.glEnd();
        off();
    }

    /** Ortası parlak, kenarı şeffaf yumuşak ışık lekesi (tek çizim çağrısı). */
    public static void orb(float cx, float cy, float r, int rgb, int centerAlpha) {
        float cr = ((rgb >> 16) & 255) / 255f, cg = ((rgb >> 8) & 255) / 255f, cb = (rgb & 255) / 255f;
        on();
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(cr, cg, cb, centerAlpha / 255f);
        GL11.glVertex2f(cx, cy);
        GL11.glColor4f(cr, cg, cb, 0f);
        for (int i = 0; i <= 32; i++) {
            double a = i * 2.0 * Math.PI / 32;
            GL11.glVertex2f((float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r));
        }
        GL11.glEnd();
        off();
    }

    // ------------------------------------------------------------ harf aralıklı yazı
    public static int spacedWidth(FontRenderer fr, String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += fr.getCharWidth(s.charAt(i)) + 1;
        return Math.max(0, w - 1);
    }

    public static void drawSpaced(FontRenderer fr, String s, float x, float y, int argb, boolean shadow) {
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            fr.drawString(String.valueOf(c), cx, y, argb, shadow);
            cx += fr.getCharWidth(c) + 1;
        }
    }

    public static void drawSpacedCentered(FontRenderer fr, String s, float cx, float y, int argb, boolean shadow) {
        drawSpaced(fr, s, cx - spacedWidth(fr, s) / 2f, y, argb, shadow);
    }
}
