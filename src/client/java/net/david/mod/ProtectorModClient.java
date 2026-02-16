package net.david.mod;

import net.david.mod.client.screen.AdminCoreScreen;
import net.david.mod.client.screen.ProtectionCoreScreen;
import net.david.mod.network.ModNetworkingClient;
import net.david.mod.registry.ModBlocks;
import net.david.mod.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;

public class ProtectorModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 1. Registro de Interfaces (Screens)
        MenuScreens.register(ModMenus.PROTECTION_CORE_MENU, ProtectionCoreScreen::new);
        MenuScreens.register(ModMenus.ADMIN_CORE_MENU, AdminCoreScreen::new);

        // 2. Registro de Redes (S2C Packets)
        ModNetworkingClient.registerClientPackets();

        // 3. Configuración de Renderizado (Transparencias/Recortes)
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.PROTECTION_CORE, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ADMIN_PROTECTOR, RenderType.cutout());

        // 4. REGISTRO DEL TICK DE CLIENTE
        // Esto es vital para que las partículas de los bordes se muevan y desaparezcan
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) {
                ModNetworkingClient.tickEffects();
            }
        });
    }
}