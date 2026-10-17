package dev.mevdz.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.util.LinkedList;

/** HUD çizimi, CPS sayacı, otomatik koşu ve ana menü değişimi. */
public class Hud {
    private static final LinkedList<Long> LEFT = new LinkedList<Long>();
    private static final LinkedList<Long> RIGHT = new LinkedList<Long>();

    // ---------------------------------------------------------- olaylar
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent e) {
        if (e.gui instanceof GuiMainMenu) {
            e.gui = new MevdZMainMenu();
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (!e.buttonstate) return;
        long now = System.currentTimeMillis();
        if (e.button == 0) LEFT.add(now);
        else if (e.button == 1) RIGHT.add(now);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (MevdZClient.keyMenu.isPressed()) {
            mc.displayGuiScreen(new MevdZMenu(null));
        }
        if (MevdZClient.keySprint.isPressed()) {
            Config.autoSprint = !Config.autoSprint;
            Config.save();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (Config.autoSprint && p != null && mc.currentScreen == null) {
            if (p.movementInput.moveForward > 0
                    && !p.isSneaking()
                    && !p.isCollidedHorizontally
                    && !p.isUsingItem()
                    && p.getFoodStats().getFoodLevel() > 6) {
                p.setSprinting(true);
            }
        }
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.hideGUI) return;
        renderAll();
    }

    // ---------------------------------------------------------- çizim
    private static int cps(LinkedList<Long> l) {
        long now = System.currentTimeMillis();
        while (!l.isEmpty() && now - l.getFirst() > 1000L) l.removeFirst();
        return l.size();
    }

    public static void renderAll() {
        Minecraft mc = Minecraft.getMinecraft();
        boolean edit = mc.currentScreen instanceof MevdZSettingsGui
                || (mc.currentScreen instanceof MevdZMenu && MevdZMenu.editingHud);
        for (Config.HudElement el : Config.ELEMENTS) {
            if (!el.enabled) continue;
            if (el.id.equals("combo") && !ExtraModule.comboVisible(edit)) continue;
            draw(mc, el);
            if (edit) {
                boolean sel = el == MevdZSettingsGui.selected;
                Ui.outline(el.x - 1, el.y - 1, el.boxW + 2, el.boxH + 2,
                        sel ? 0xFF19C3FF : 0x6619C3FF);
            }
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static String textFor(Minecraft mc, Config.HudElement el) {
        if (el.id.equals("fps")) {
            return "FPS: " + Minecraft.getDebugFPS();
        } else if (el.id.equals("ping")) {
            if (mc.thePlayer == null || mc.getNetHandler() == null) return "Ping: -";
            if (mc.isSingleplayer()) return "Ping: yerel";
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            return "Ping: " + (info != null ? info.getResponseTime() + " ms" : "-");
        } else if (el.id.equals("cps")) {
            return "CPS: " + cps(LEFT) + " | " + cps(RIGHT);
        } else if (el.id.equals("combo")) {
            String s = "Combo: " + ExtraModule.combo;
            if (Features.COMBO_BEST.value) s += "  (En iyi: " + ExtraModule.best + ")";
            return s;
        } else if (el.id.equals("sprint")) {
            return "Otomatik Koşu: " + (Config.autoSprint ? "AÇIK" : "KAPALI");
        }
        return el.label;
    }

    private static void draw(Minecraft mc, Config.HudElement el) {
        FontRenderer fr = mc.fontRendererObj;
        int col = el.currentColor();
        int lw, lh;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) el.x, (float) el.y, 0f);
        GlStateManager.scale(el.scale, el.scale, 1f);

        if (el.id.equals("keys")) {
            lw = 64;
            lh = 60;
            GameSettings gs = mc.gameSettings;
            drawKey(fr, "W", 22, 0, 20, 20, gs.keyBindForward.isKeyDown(), col, el.background);
            drawKey(fr, "A", 0, 22, 20, 20, gs.keyBindLeft.isKeyDown(), col, el.background);
            drawKey(fr, "S", 22, 22, 20, 20, gs.keyBindBack.isKeyDown(), col, el.background);
            drawKey(fr, "D", 44, 22, 20, 20, gs.keyBindRight.isKeyDown(), col, el.background);
            drawKey(fr, "LMB", 0, 44, 31, 16, Mouse.isButtonDown(0), col, el.background);
            drawKey(fr, "RMB", 33, 44, 31, 16, Mouse.isButtonDown(1), col, el.background);
        } else {
            String text = textFor(mc, el);
            lw = fr.getStringWidth(text) + 9;
            lh = 13;
            if (el.background) {
                Gui.drawRect(0, 0, lw, lh, 0x90000000);
                Gui.drawRect(0, 0, 2, lh, 0xFF000000 | col);
            }
            fr.drawStringWithShadow(text, 5, 3, 0xFF000000 | col);
        }

        GlStateManager.popMatrix();
        el.boxW = Math.round(lw * el.scale);
        el.boxH = Math.round(lh * el.scale);
    }

    private static void drawKey(FontRenderer fr, String label, int x, int y, int w, int h,
                                boolean down, int col, boolean bg) {
        int base = down ? 0xA0FFFFFF : (bg ? 0x80000000 : 0x00000000);
        Gui.drawRect(x, y, x + w, y + h, base);
        Ui.outline(x, y, w, h, ((down ? 0xFF : 0x99) << 24) | col);
        int tc = down ? 0xFF000000 : (0xFF000000 | col);
        fr.drawString(label, x + (w - fr.getStringWidth(label)) / 2, y + (h - 8) / 2, tc);
    }
}
