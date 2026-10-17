package dev.mevdz.client;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Yeni özelliklerin olay dinleyicileri: eşya konumu/1.7 blok (MevdZItemRenderer),
 * zoom, freelook, blok çerçevesi ve motion blur.
 */
public class FeatureModule {
    public static KeyBinding keyFeatures, keyZoom;

    private static boolean rendererTried;

    // zoom
    private float zoomCur = 1f;

    // freelook
    private boolean fl;
    private float camYaw, camPitch;
    private int savedView;

    // motion blur
    private int blurTex = -1, blurW, blurH;
    private boolean blurValid;

    public static void init() {
        Features.load();
        keyFeatures = new KeyBinding("MevdZ Özellikler Menüsü", Keyboard.KEY_RCONTROL, "MevdZ Client");
        keyZoom = new KeyBinding("Zoom (basılı tut)", Keyboard.KEY_C, "MevdZ Client");
        ClientRegistry.registerKeyBinding(keyFeatures);
        ClientRegistry.registerKeyBinding(keyZoom);
        MinecraftForge.EVENT_BUS.register(new FeatureModule());
    }

    // ================================================================ girdi
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (keyFeatures.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new MevdZMenu(null));
        }
    }

    /** Orta tuş freelook için kullanılıyor; "blok seç" davranışını engelle. */
    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (Features.FREELOOK_ON.value && e.button == 2) e.setCanceled(true);
    }

    // ================================================================ tick: eşya renderer kurulumu + zoom
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();

        if (!rendererTried && mc.thePlayer != null && mc.entityRenderer != null) {
            rendererTried = true;
            installItemRenderer(mc);
        }

        float target = 1f;
        if (Features.ZOOM_ON.value && mc.currentScreen == null && keyZoom.isKeyDown()) {
            target = Features.ZOOM_FACTOR.value;
        }
        zoomCur += (target - zoomCur) * Features.ZOOM_SPEED.value;
        if (Math.abs(target - zoomCur) < 0.005f) zoomCur = target;
    }

    private static void installItemRenderer(Minecraft mc) {
        try {
            MevdZItemRenderer r = new MevdZItemRenderer(mc);
            boolean a = setByType(Minecraft.class, mc, r);
            boolean b = setByType(EntityRenderer.class, mc.entityRenderer, r);
            if (!a || !b) System.err.println("[MevdZ] ItemRenderer degistirilemedi (" + a + "," + b + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** Alan adına (obf/deobf) bağımlı olmamak için türe göre bulur. */
    private static boolean setByType(Class<?> owner, Object inst, Object val) throws Exception {
        for (Field f : owner.getDeclaredFields()) {
            if (f.getType() == ItemRenderer.class) {
                f.setAccessible(true);
                f.set(inst, val);
                return true;
            }
        }
        return false;
    }

    // ================================================================ zoom
    @SubscribeEvent
    public void onFov(FOVUpdateEvent e) {
        if (zoomCur > 1.001f) e.newfov = e.newfov / zoomCur;
    }

    // ================================================================ freelook
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;

        boolean want = Features.FREELOOK_ON.value && p != null && mc.currentScreen == null
                && mc.inGameHasFocus && Mouse.isButtonDown(2);

        if (want && !fl) {
            fl = true;
            camYaw = p.rotationYaw;
            camPitch = p.rotationPitch;
            savedView = mc.gameSettings.thirdPersonView;
            mc.gameSettings.thirdPersonView = 1;
        } else if (!want && fl) {
            fl = false;
            mc.gameSettings.thirdPersonView = savedView;
        }

        if (fl) {
            // Fare hareketini biz tüketiriz; vanilla 0 görür, oyuncunun gerçek yönü değişmez.
            float s = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
            float f = s * s * s * 8f;
            float dx = Mouse.getDX() * f;
            float dy = Mouse.getDY() * f * (mc.gameSettings.invertMouse ? -1f : 1f);
            camYaw += dx * 0.15f;
            camPitch = Ui.clamp(camPitch - dy * 0.15f, -90f, 90f);
        }
    }

    @SubscribeEvent
    public void onCamera(EntityViewRenderEvent.CameraSetup e) {
        if (!fl) return;
        e.yaw = camYaw + 180f;
        e.pitch = camPitch;
    }

    // ================================================================ blok çerçevesi
    @SubscribeEvent
    public void onHighlight(DrawBlockHighlightEvent e) {
        if (!Features.OUTLINE_ON.value) return;
        MovingObjectPosition t = e.target;
        if (t == null || t.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        Minecraft mc = Minecraft.getMinecraft();
        WorldClient w = mc.theWorld;
        BlockPos bp = t.getBlockPos();
        Block b = w.getBlockState(bp).getBlock();
        if (b.getMaterial() == Material.air || !w.getWorldBorder().contains(bp)) return;

        e.setCanceled(true);
        b.setBlockBoundsBasedOnState(w, bp);
        EntityPlayer pl = e.player;
        double ox = pl.lastTickPosX + (pl.posX - pl.lastTickPosX) * e.partialTicks;
        double oy = pl.lastTickPosY + (pl.posY - pl.lastTickPosY) * e.partialTicks;
        double oz = pl.lastTickPosZ + (pl.posZ - pl.lastTickPosZ) * e.partialTicks;
        AxisAlignedBB bb = b.getSelectedBoundingBox(w, bp).expand(0.002D, 0.002D, 0.002D).offset(-ox, -oy, -oz);

        int rgb;
        if (Features.OUTLINE_CHROMA.value) {
            rgb = Ui.hsb((System.currentTimeMillis() % 3000L) / 3000f, 0.7f, 1f);
        } else {
            rgb = (Math.round(Features.OUTLINE_R.value) << 16)
                    | (Math.round(Features.OUTLINE_G.value) << 8)
                    | Math.round(Features.OUTLINE_B.value);
        }

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        GL11.glLineWidth(Features.OUTLINE_WIDTH.value);
        GlStateManager.color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f,
                (rgb & 255) / 255f, Features.OUTLINE_ALPHA.value);

        double x1 = bb.minX, y1 = bb.minY, z1 = bb.minZ, x2 = bb.maxX, y2 = bb.maxY, z2 = bb.maxZ;
        GL11.glBegin(GL11.GL_LINES);
        // alt
        edge(x1, y1, z1, x2, y1, z1);
        edge(x2, y1, z1, x2, y1, z2);
        edge(x2, y1, z2, x1, y1, z2);
        edge(x1, y1, z2, x1, y1, z1);
        // üst
        edge(x1, y2, z1, x2, y2, z1);
        edge(x2, y2, z1, x2, y2, z2);
        edge(x2, y2, z2, x1, y2, z2);
        edge(x1, y2, z2, x1, y2, z1);
        // dikey
        edge(x1, y1, z1, x1, y2, z1);
        edge(x2, y1, z1, x2, y2, z1);
        edge(x2, y1, z2, x2, y2, z2);
        edge(x1, y1, z2, x1, y2, z2);
        GL11.glEnd();

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void edge(double ax, double ay, double az, double bx, double by, double bz) {
        GL11.glVertex3d(ax, ay, az);
        GL11.glVertex3d(bx, by, bz);
    }

    // ================================================================ motion blur
    /**
     * Önceki karenin görüntüsünü şeffaflıkla mevcut karenin üstüne bindirir, sonra sonucu
     * bir sonraki kare için saklar (üstel ortalama = gerçek motion blur). HUD'dan önce çalışır.
     */
    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!Features.BLUR_ON.value || mc.theWorld == null) {
            blurValid = false;
            return;
        }

        int w = mc.getFramebuffer().framebufferWidth;
        int h = mc.getFramebuffer().framebufferHeight;
        if (blurTex == -1 || w != blurW || h != blurH) {
            if (blurTex != -1) GL11.glDeleteTextures(blurTex);
            blurTex = GL11.glGenTextures();
            GlStateManager.bindTexture(blurTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, w, h, 0, GL11.GL_RGB,
                    GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            blurW = w;
            blurH = h;
            blurValid = false;
        }

        if (blurValid) {
            // FPS'ten bağımsız aynı süre-sabiti: yüksek FPS'te önceki kare daha çok karışır.
            double a = Math.pow(Features.BLUR_AMOUNT.value, 60.0 / Math.max(30, Minecraft.getDebugFPS()));
            float alpha = (float) Math.min(0.95, a);
            ScaledResolution sr = e.resolution;
            float sw = sr.getScaledWidth(), sh = sr.getScaledHeight();

            GlStateManager.enableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.disableLighting();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.bindTexture(blurTex);
            GlStateManager.color(1f, 1f, 1f, alpha);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0f, 1f);
            GL11.glVertex2f(0f, 0f);
            GL11.glTexCoord2f(0f, 0f);
            GL11.glVertex2f(0f, sh);
            GL11.glTexCoord2f(1f, 0f);
            GL11.glVertex2f(sw, sh);
            GL11.glTexCoord2f(1f, 1f);
            GL11.glVertex2f(sw, 0f);
            GL11.glEnd();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }

        GlStateManager.bindTexture(blurTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        blurValid = true;
    }
}
