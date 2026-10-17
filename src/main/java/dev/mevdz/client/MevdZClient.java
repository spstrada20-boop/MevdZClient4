package dev.mevdz.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = "mevdzclient", name = "MevdZ Client", version = "1.0", acceptableRemoteVersions = "*")
public class MevdZClient {
    public static KeyBinding keyMenu;
    public static KeyBinding keySprint;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Config.load();
        keyMenu = new KeyBinding("MevdZ Ayarları", Keyboard.KEY_RSHIFT, "MevdZ Client");
        keySprint = new KeyBinding("Otomatik Koşu Aç/Kapat", Keyboard.KEY_G, "MevdZ Client");
        ClientRegistry.registerKeyBinding(keyMenu);
        ClientRegistry.registerKeyBinding(keySprint);
        MinecraftForge.EVENT_BUS.register(new Hud());
        FeatureModule.init();
        ExtraModule.init();
    }
}
