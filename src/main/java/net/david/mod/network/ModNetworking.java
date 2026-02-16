package net.david.mod.network;

import net.david.mod.ProtectorMod;
import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.command.ClanCommands;
import net.david.mod.util.InviteManager;
import net.david.mod.util.ProtectionDataManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class ModNetworking {

    public static final ResourceLocation SYNC_LEVEL_ID = new ResourceLocation(ProtectorMod.MOD_ID, "sync_level");
    public static final ResourceLocation SYNC_CORES_ID = new ResourceLocation(ProtectorMod.MOD_ID, "sync_cores");
    public static final ResourceLocation SPAWN_BOUNDARY_PARTICLES = new ResourceLocation(ProtectorMod.MOD_ID, "spawn_boundary_particles");

    // C2S
    public static final ResourceLocation UPGRADE_ID = new ResourceLocation(ProtectorMod.MOD_ID, "upgrade_core");
    public static final ResourceLocation GUI_SHOW_AREA_ID = new ResourceLocation(ProtectorMod.MOD_ID, "gui_show_area");
    public static final ResourceLocation UPDATE_ADMIN_ID = new ResourceLocation(ProtectorMod.MOD_ID, "update_admin_core");
    public static final ResourceLocation PERMISSION_ID = new ResourceLocation(ProtectorMod.MOD_ID, "change_permission");
    public static final ResourceLocation UPDATE_FLAG_ID = new ResourceLocation(ProtectorMod.MOD_ID, "update_flag");
    public static final ResourceLocation TOGGLE_VISUALIZER_ID = new ResourceLocation(ProtectorMod.MOD_ID, "toggle_visualizer");
    public static final ResourceLocation CREATE_CLAN_ID = new ResourceLocation(ProtectorMod.MOD_ID, "create_clan");
    public static final ResourceLocation REMOVE_PLAYER_ID = new ResourceLocation(ProtectorMod.MOD_ID, "remove_player");
    public static final ResourceLocation INVITE_PLAYER_ID = new ResourceLocation(ProtectorMod.MOD_ID, "invite_player");

    public static void registerServerPackets() {

        // 1. MEJORAR NÚCLEO
        ServerPlayNetworking.registerGlobalReceiver(UPGRADE_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (player.level().getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                    if (core.isTrusted(player)) {
                        core.upgrade(player);

                        // Enviamos el nuevo nivel específicamente al jugador para actualizar su GUI
                        sendLevelSync(player, pos, core.getCoreLevel());

                        updateManagerAndSync(player.serverLevel(), pos, core);
                        player.displayClientMessage(Component.literal("§aCore mejorado al nivel " + core.getCoreLevel()), true);
                    }
                }
            });
        });

        // 2. MOSTRAR ÁREA DESDE GUI
        ServerPlayNetworking.registerGlobalReceiver(GUI_SHOW_AREA_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (player.level().getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                    FriendlyByteBuf outBuf = PacketByteBufs.create();
                    outBuf.writeBlockPos(pos);
                    outBuf.writeInt(core.getRadius());
                    ServerPlayNetworking.send(player, SPAWN_BOUNDARY_PARTICLES, outBuf);
                    player.closeContainer();
                }
            });
        });

        // 3. ACTUALIZAR ADMIN CORE
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_ADMIN_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int newRadius = buf.readInt();
            boolean pvp = buf.readBoolean();
            boolean explosions = buf.readBoolean();
            boolean build = buf.readBoolean();

            server.execute(() -> {
                if (player.hasPermissions(2) && player.level().getBlockEntity(pos) instanceof ProtectionCoreBlockEntity adminCore) {
                    adminCore.setAdminRadius(newRadius);
                    adminCore.setFlag("pvp", pvp);
                    adminCore.setFlag("explosions", explosions); // Aquí se actualiza el bloque
                    adminCore.setFlag("build", build);

                    // Al llamar a esto, usará el método que corregimos arriba y enviará 'explosions' al cliente
                    updateManagerAndSync(player.serverLevel(), pos, adminCore);

                    adminCore.markDirtyAndUpdate();
                    player.displayClientMessage(Component.literal("§d[Admin]§a Configuración aplicada."), true);
                }
            });
        });

        // 4. GESTIÓN DE PERMISOS
        ServerPlayNetworking.registerGlobalReceiver(PERMISSION_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String targetName = buf.readUtf();
            String actionType = buf.readUtf();
            boolean value = buf.readBoolean();

            server.execute(() -> {
                if (player.level().getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                    if (core.getOwnerUUID().equals(player.getUUID()) || player.hasPermissions(2)) {
                        ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(targetName);
                        UUID targetUUID = (targetPlayer != null) ? targetPlayer.getUUID() : UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes());
                        core.updatePermission(targetUUID, targetName, actionType, value);
                        core.markDirtyAndUpdate();
                    }
                }
            });
        });

        // 5. ACTUALIZAR FLAGS
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_FLAG_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String flagId = buf.readUtf();
            server.execute(() -> {
                if (player.level().getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                    if (core.canPlayerEditFlag(player, flagId)) {
                        core.setFlag(flagId, !core.getFlag(flagId));
                        updateManagerAndSync(player.serverLevel(), pos, core);

                        core.markDirtyAndUpdate();
                    }
                }
            });
        });

        // 6. TOGGLE VISUALIZADOR
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_VISUALIZER_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                UUID uuid = player.getUUID();
                if (ClanCommands.VISUALIZER_ENABLED.contains(uuid)) {
                    ClanCommands.VISUALIZER_ENABLED.remove(uuid);
                    player.displayClientMessage(Component.literal("§cVisualizador desactivado."), true);
                } else {
                    ClanCommands.VISUALIZER_ENABLED.add(uuid);
                    player.displayClientMessage(Component.literal("§aVisualizador activado."), true);
                }
            });
        });

        // 7. BORRAR JUGADOR (Desde la [X] de la lista lateral)
        ServerPlayNetworking.registerGlobalReceiver(REMOVE_PLAYER_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String targetName = buf.readUtf();
            server.execute(() -> {
                if (player.level().getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                    if (core.getOwnerUUID().equals(player.getUUID()) || player.hasPermissions(2)) {
                        core.removePlayerPermissions(targetName);
                        core.markDirtyAndUpdate();
                        player.displayClientMessage(Component.literal("§c[-] " + targetName + " eliminado de la protección."), true);
                    }
                }
            });
        });

        // 8. INVITAR JUGADOR (Sistema de chat interactivo)
        ServerPlayNetworking.registerGlobalReceiver(INVITE_PLAYER_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String targetName = buf.readUtf();
            server.execute(() -> {
                ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(targetName);
                if (targetPlayer != null) {
                    // Guardamos la invitación en el manager
                    InviteManager.addInvite(targetPlayer.getUUID(), pos, player.getUUID());

                    // Mensaje interactivo para el invitado
                    Component inviteMsg = Component.literal("§b§l[!] §f" + player.getName().getString() + " §7te invita a su protección. ")
                            .append(Component.literal("§a§l[ACEPTAR]")
                                    .withStyle(style -> style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/protector accept"))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7Click para unirte")))));

                    targetPlayer.sendSystemMessage(inviteMsg);
                    player.displayClientMessage(Component.literal("§eInvitación enviada a " + targetName), true);
                } else {
                    player.displayClientMessage(Component.literal("§cEl jugador no está conectado."), true);
                }
            });
        });

    }

    /**
     * Envía de forma manual la sincronización de nivel al cliente
     */
    public static void sendLevelSync(ServerPlayer player, BlockPos pos, int level) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeInt(level);
        ServerPlayNetworking.send(player, SYNC_LEVEL_ID, buf);
    }

    private static void updateManagerAndSync(ServerLevel level, BlockPos pos, ProtectionCoreBlockEntity core) {
        ProtectionDataManager manager = ProtectionDataManager.get(level);

        // CRUCIAL: Leer la flag directamente del CoreEntity, no del manager viejo
        // Asumiendo que tu ProtectionCoreBlockEntity tiene un método para obtener la flag de explosiones
        boolean currentExplosionStatus = core.getFlag("explosions");

        manager.addCore(
                pos,
                core.getOwnerUUID(),
                core.getRadius(),
                core.isAdmin(),
                currentExplosionStatus // Ahora pasamos el valor REAL del bloque
        );

        manager.setDirty();
        manager.syncToAll(level);
    }

    // Lado Servidor
    public static void handleRemovePlayer(ServerLevel level, BlockPos pos, UUID uuidToRemove) {
        if (level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
            core.removeGuestFully(uuidToRemove);
            ProtectionDataManager.get(level).syncToAll(level);
        }
    }
}
