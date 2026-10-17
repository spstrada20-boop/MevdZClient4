package dev.mevdz.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Yeni özellikler: Insert ana menü tuşu, özel hitbox rengi, özel nametag, combo sayacı.
 * Ayarlar Features.java içinde (Hitbox / Nametag / Combo kategorileri).
 */
public class ExtraModule {
    public static KeyBinding keyMain;

    public static int combo, best;
    private static long lastHit;
    private boolean wasHurt;

    public static void init() {
        keyMain = new KeyBinding("MevdZ Ana Menü", Keyboard.KEY_INSERT, "MevdZ Client");
        ClientRegistry.registerKeyBinding(keyMain);
        MinecraftForge.EVENT_BUS.register(new ExtraModule());
    }

    /** HUD'daki combo öğesi çizilsin mi? (ayar ekranındayken hep çizilir ki sürüklenebilsin) */
    public static boolean comboVisible(boolean edit) {
        if (edit) return true;
        return Features.COMBO_ON.value && (combo > 0 || !Features.COMBO_HIDE0.value);
    }

    // ================================================================ girdi
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (keyMain.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new MevdZMenu(null));
        }
    }

    // ================================================================ combo
    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!Features.COMBO_ON.value || e.entityPlayer != mc.thePlayer) return;
        if (!(e.target instanceof EntityLivingBase)) return;
        EntityLivingBase t = (EntityLivingBase) e.target;
        // hedef hâlâ dokunulmazsa (hurt cooldown) vuruş sayılmaz
        if (t.hurtResistantTime > t.maxHurtResistantTime / 2) return;
        combo++;
        if (combo > best) best = combo;
        lastHit = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) {
            combo = 0;
            wasHurt = false;
            return;
        }
        boolean hurt = p.hurtTime > 0;
        if (hurt && !wasHurt) combo = 0; // hasar aldın -> combo bozuldu
        wasHurt = hurt;
        if (combo > 0 && System.currentTimeMillis() - lastHit > (long) (Features.COMBO_TIMEOUT.value * 1000f)) {
            combo = 0;
        }
    }

    // ================================================================ hitbox
    @SubscribeEvent
    public void onWorldLast(RenderWorldLastEvent e) {
        if (!Features.HB_ON.value) return;
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (mc.theWorld == null || view == null) return;

        float pt = e.partialTicks;
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        int alpha = Math.round(Features.HB_ALPHA.value * 255f);
        int fixed = (Math.round(Features.HB_R.value) << 16)
                | (Math.round(Features.HB_G.value) << 8)
                | Math.round(Features.HB_B.value);
        float time = (System.currentTimeMillis() % 3000L) / 3000f;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GL11.glLineWidth(Features.HB_WIDTH.value);

        List<Entity> list = mc.theWorld.loadedEntityList;
        for (int i = 0; i < list.size(); i++) {
            Entity ent = list.get(i);
            if (ent == view || ent.isDead) continue;
            if (Features.HB_PLAYERS.value) {
                if (!(ent instanceof EntityPlayer)) continue;
            } else if (!(ent instanceof EntityLivingBase)) {
                continue;
            }
            if (ent.getDistanceSqToEntity(view) > 96.0 * 96.0) continue;

            int rgb;
            if (Features.HB_FLASH.value && ((EntityLivingBase) ent).hurtTime > 0) {
                rgb = 0xFF2828;
            } else if (Features.HB_CHROMA.value) {
                float hue = ((time + (ent.getEntityId() % 10) * 0.03f) % 1f + 1f) % 1f;
                rgb = Ui.hsb(hue, 0.7f, 1f);
            } else {
                rgb = fixed;
            }

            double dx = ent.lastTickPosX + (ent.posX - ent.lastTickPosX) * pt - ent.posX - cx;
            double dy = ent.lastTickPosY + (ent.posY - ent.lastTickPosY) * pt - ent.posY - cy;
            double dz = ent.lastTickPosZ + (ent.posZ - ent.lastTickPosZ) * pt - ent.posZ - cz;
            AxisAlignedBB base = ent.getEntityBoundingBox().offset(dx, dy, dz);
            float b = ent.getCollisionBorderSize();

            RenderGlobal.drawOutlinedBoundingBox(base.expand(b, b, b),
                    (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha);

            if (Features.HB_EYE.value) {
                double ey = base.minY + ent.getEyeHeight();
                RenderGlobal.drawOutlinedBoundingBox(
                        new AxisAlignedBB(base.minX, ey, base.minZ, base.maxX, ey, base.maxZ),
                        255, 60, 60, alpha);
            }
        }

        GL11.glLineWidth(1f);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ================================================================ nametag
    @SuppressWarnings("rawtypes")
    @SubscribeEvent
    public void onNametag(RenderLivingEvent.Specials.Pre e) {
        if (!Features.NT_ON.value) return;
        if (!(e.entity instanceof EntityPlayer)) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer pl = (EntityPlayer) e.entity;
        // vanilla zaten çizmeyecekse karışma
        if (mc.thePlayer == null || pl == mc.getRenderViewEntity() || pl.isInvisibleToPlayer(mc.thePlayer)
                || !Minecraft.isGuiEnabled() || pl.riddenByEntity != null) return;

        e.setCanceled(true); // vanilla nametag yerine bizimki
        double d2 = pl.getDistanceSqToEntity(mc.thePlayer);
        double max = pl.isSneaking() ? 32.0 : 64.0;
        if (d2 > max * max) return;
        drawTag(mc, pl, e.x, e.y, e.z, Math.sqrt(d2));
    }

    private static void drawTag(Minecraft mc, EntityPlayer pl, double x, double y, double z, double dist) {
        FontRenderer fr = mc.fontRendererObj;
        RenderManager rm = mc.getRenderManager();
        boolean rainbow = Features.NT_RAINBOW.value;
        String name = rainbow ? pl.getName() : pl.getDisplayName().getFormattedText();

        StringBuilder ex = new StringBuilder();
        if (Features.NT_HEALTH.value) {
            float hp = pl.getHealth() + pl.getAbsorptionAmount();
            float ratio = hp / Math.max(1f, pl.getMaxHealth());
            String col = ratio > 0.66f ? "\u00a7a" : (ratio > 0.33f ? "\u00a7e" : "\u00a7c");
            ex.append(' ').append(col).append("\u2764 ").append(Math.round(hp * 10f) / 10f);
        }
        if (Features.NT_DIST.value) ex.append(" \u00a77[").append((int) dist).append("m]");
        String extra = ex.toString();

        float s = 0.016666668F * 1.6F * Features.NT_SCALE.value;
        if (Features.NT_FAR.value && dist > 6.0) s *= (float) Math.min(dist / 6.0, 4.0);

        int total = fr.getStringWidth(name) + fr.getStringWidth(extra);
        int half = total / 2;
        int bgA = Math.round(Features.NT_BG.value * 255f);
        boolean sh = Features.NT_SHADOW.value;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + pl.height + 0.5f, (float) z);
        GL11.glNormal3f(0f, 1f, 0f);
        GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
        GlStateManager.scale(-s, -s, s);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        if (bgA > 0) Gui.drawRect(-half - 3, -2, half + 3, 10, bgA << 24);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        drawText(fr, name, extra, -half, rainbow, sh, 0x20); // duvar arkasında soluk
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        drawText(fr, name, extra, -half, rainbow, sh, 0xFF); // görünürken tam
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private static void drawText(FontRenderer fr, String name, String extra, int x, boolean rainbow,
                                 boolean shadow, int a) {
        int base = (a << 24) | 0xFFFFFF;
        if (!rainbow) {
            fr.drawString(name, x, 0, base, shadow);
        } else {
            float cx = x;
            float time = (System.currentTimeMillis() % 3000L) / 3000f;
            int i = 0;
            for (char c : name.toCharArray()) {
                float hue = ((time - i * 0.06f) % 1f + 1f) % 1f;
                fr.drawString(String.valueOf(c), cx, 0f, (a << 24) | Ui.hsb(hue, 0.65f, 1f), shadow);
                cx += fr.getCharWidth(c);
                i++;
            }
        }
        if (extra.length() > 0) fr.drawString(extra, x + fr.getStringWidth(name), 0, base, shadow);
    }
}
