package dev.mevdz.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/**
 * Vanilla ItemRenderer'ın yerine geçer (FeatureModule kurar).
 *  - Eşya konumu / boyutu: vanilla çizimden ÖNCE GL matrisine translate + scale uygular.
 *  - 1.7 bloklama: bloklarken vanilla'yı "kullanım yok" yoluna sokar (kol sallanması görünür),
 *    sonra eşya çizilirken blok pozunu ekler.
 */
public class MevdZItemRenderer extends ItemRenderer {
    private final Minecraft mcRef;
    private boolean blockPose;
    private static Field useCount;
    private static boolean useCountTried;

    public MevdZItemRenderer(Minecraft mc) {
        super(mc);
        this.mcRef = mc;
    }

    private static Field useCountField() {
        if (!useCountTried) {
            useCountTried = true;
            try {
                useCount = ReflectionHelper.findField(EntityPlayer.class, "itemInUseCount", "field_71072_f");
            } catch (Throwable t) {
                useCount = null;
                System.err.println("[MevdZ] itemInUseCount alani bulunamadi, 1.7 blok kapali.");
            }
        }
        return useCount;
    }

    @Override
    public void renderItemInFirstPerson(float partialTicks) {
        EntityPlayerSP p = mcRef.thePlayer;
        boolean old = Features.OLD_BLOCK.value && p != null && p.isBlocking() && useCountField() != null;
        int saved = 0;

        GlStateManager.pushMatrix();
        if (Features.ITEM_CUSTOM.value) {
            GlStateManager.translate(Features.ITEM_X.value, Features.ITEM_Y.value, Features.ITEM_Z.value);
            float s = Features.ITEM_SCALE.value;
            GlStateManager.scale(s, s, s);
        }
        try {
            if (old) {
                saved = p.getItemInUseCount();
                useCount.setInt(p, 0);
                blockPose = true;
            }
            super.renderItemInFirstPerson(partialTicks);
        } catch (Throwable t) {
            // hata olursa oyunu çökertme
        } finally {
            if (old) {
                try {
                    useCount.setInt(p, saved);
                } catch (Throwable ignored) {
                }
                blockPose = false;
            }
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void renderItem(EntityLivingBase entity, ItemStack stack, ItemCameraTransforms.TransformType type) {
        if (blockPose && type == ItemCameraTransforms.TransformType.FIRST_PERSON) {
            GlStateManager.translate(-0.5F, 0.2F, 0.0F);
            GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
        }
        super.renderItem(entity, stack, type);
    }
}
