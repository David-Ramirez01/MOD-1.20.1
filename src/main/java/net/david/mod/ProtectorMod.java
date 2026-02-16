package net.david.mod;

import net.david.mod.command.ClanCommands;
import net.david.mod.event.ProtectionEvents;
import net.david.mod.network.ModNetworking;
import net.david.mod.registry.ModBlockEntities;
import net.david.mod.registry.ModBlocks;
import net.david.mod.registry.ModItems;
import net.david.mod.registry.ModMenus;
import net.david.mod.util.ProtectionDataManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtectorMod implements ModInitializer {
    public static final String MOD_ID = "protectormod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ResourceLocation PROTECTOR_TAB_ID = new ResourceLocation(MOD_ID, "protector_tab");
    public static final CreativeModeTab PROTECTOR_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            PROTECTOR_TAB_ID,
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup." + MOD_ID + ".protector_tab"))
                    .icon(() -> new ItemStack(ModBlocks.PROTECTION_CORE))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.PROTECTION_CORE);
                        output.accept(ModBlocks.ADMIN_PROTECTOR);
                        output.accept(ModItems.PROTECTION_UPGRADE);
                    }).build()
    );

    @Override
    public void onInitialize() {
        // 1. Registros Base
        ModBlocks.registerBlocks();
        ModItems.registerItems();
        ModBlockEntities.registerBlockEntities();
        ModMenus.registerMenus();

        // 2. Red y Comandos
        ModNetworking.registerServerPackets();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ClanCommands.register(dispatcher);
        });

        // 3. Eventos
        ProtectionEvents.register();

        // 4. Tick del Servidor (Visualizador Persistente)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Ejecutamos cada 20 ticks (1 segundo) para no saturar la red
            if (server.getTickCount() % 20 != 0) return;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (ClanCommands.VISUALIZER_ENABLED.contains(player.getUUID())) {
                    ProtectionDataManager manager = ProtectionDataManager.get(player.level());
                    BlockPos playerPos = player.blockPosition();

                    // Buscar el core más cercano en un radio de 64 bloques
                    manager.getAllCores().values().stream()
                            .filter(core -> core.pos().distSqr(playerPos) < 4096)
                            .findFirst()
                            .ifPresent(core -> {
                                FriendlyByteBuf buf = PacketByteBufs.create();
                                buf.writeBlockPos(core.pos());
                                buf.writeInt(core.radius());
                                // USAMOS EL ID DE PARTÍCULAS REFACTORIZADO
                                ServerPlayNetworking.send(player, ModNetworking.SPAWN_BOUNDARY_PARTICLES, buf);
                            });
                }
            }
        });

// 4. Tick del Servidor (Visualizador y Lógica de Protección)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            boolean isSecondTick = server.getTickCount() % 20 == 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // A. Lógica de Protección (Cada Tick): Mensajes, Hambre, Eyección
                ProtectionEvents.onPlayerTick(player);

                // B. Lógica del Visualizador (Cada 1 segundo): Partículas
                if (isSecondTick && ClanCommands.VISUALIZER_ENABLED.contains(player.getUUID())) {
                    sendVisualizerParticles(player);
                }
            }
        });

        LOGGER.info("Protector Mod inicializado correctamente.");
    }

    private void sendVisualizerParticles(ServerPlayer player) {
        ProtectionDataManager manager = ProtectionDataManager.get(player.serverLevel());
        BlockPos playerPos = player.blockPosition();

        manager.getAllCores().values().stream()
                .filter(core -> core.pos().distSqr(playerPos) < 4096) // Radio de 64 bloques
                .findFirst()
                .ifPresent(core -> {
                    FriendlyByteBuf buf = PacketByteBufs.create();
                    buf.writeBlockPos(core.pos());
                    buf.writeInt(core.radius());
                    buf.writeBoolean(core.isAdmin()); // Sincronizamos el color según el tipo de core
                    ServerPlayNetworking.send(player, ModNetworking.SPAWN_BOUNDARY_PARTICLES, buf);
                });
    }
}