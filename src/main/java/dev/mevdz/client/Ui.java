package dev.mevdz.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.awt.Color;

/** Küçük çizim yardımcıları (renk geçişi, çerçeve, animasyonlu yazı). */
public class Ui {
    public static final int CYAN = 0x19C3FF;
    public static final int VIOLET = 0x7C5CFF;
    public static final int MUTED = 0x6F7896;

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** İki RGB rengi arasında geçiş (alfa yok). */
    public static int lerp(int a, int b, float t) {
        t = clamp(t, 0f, 1f);
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
        return (r << 16) | (g << 8) | bl;
    }

    public static int hsb(float h, float s, float b) {
        return Color.HSBtoRGB(h, s, b) & 0xFFFFFF;
    }

    /** Yatay renk geçişli dikdörtgen. */
    public static void hGrad(int x, int y, int w, int h, int c1, int c2, int alpha) {
        for (int i = 0; i < w; i += 2) {
            int cw = Math.min(2, w - i);
            Gui.drawRect(x + i, y, x + i + cw, y + h, (alpha << 24) | lerp(c1, c2, (float) i / w));
        }
    }

    public static void outline(int x, int y, int w, int h, int argb) {
        Gui.drawRect(x, y, x + w, y + 1, argb);
        Gui.drawRect(x, y + h - 1, x + w, y + h, argb);
        Gui.drawRect(x, y, x + 1, y + h, argb);
        Gui.drawRect(x + w - 1, y, x + w, y + h, argb);
    }

    public static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static boolean in(int mx, int my, int[] r) {
        return in(mx, my, r[0], r[1], r[2], r[3]);
    }

    /** Harf harf cyan-mor dalgalanan animasyonlu yazı. */
    public static void animText(FontRenderer fr, String s, float x, float y, float time) {
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            float t = 0.5f + 0.5f * (float) Math.sin(time * 2.2f - i * 0.55f);
            fr.drawStringWithShadow(String.valueOf(c), cx, y, 0xFF000000 | lerp(CYAN, VIOLET, t));
            cx += fr.getCharWidth(c);
        }
    }
}
