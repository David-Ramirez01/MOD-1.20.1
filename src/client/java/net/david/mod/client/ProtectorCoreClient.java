package net.david.mod.client;

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

public class ProtectorCoreClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 1. Registro de Interfaces (Screens)
		// Esto asocia cada MenuType con su interfaz visual
		MenuScreens.register(ModMenus.PROTECTION_CORE_MENU, ProtectionCoreScreen::new);
		MenuScreens.register(ModMenus.ADMIN_CORE_MENU, AdminCoreScreen::new);

		// 2. Registro de Red del Cliente
		// Esto permite que el cliente reciba paquetes (como la sincronización de datos)
		ModNetworkingClient.registerClientPackets();

		// 3. Capas de Renderizado
		// Necesario para que los bloques con texturas transparentes/recortadas se vean bien
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.PROTECTION_CORE, RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ADMIN_PROTECTOR, RenderType.cutout());

		// 4. Tick para procesar efectos de partículas
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null) {
				// Removemos efectos terminados mientras los ejecutamos
				ModNetworkingClient.ACTIVE_EFFECTS.removeIf(effect -> !effect.tick(client.level));
			}
		});
	}
}