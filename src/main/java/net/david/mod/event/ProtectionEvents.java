package net.david.mod.event;

import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.registry.ModBlocks;
import net.david.mod.registry.ModItems;
import net.david.mod.util.ProtectionDataManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProtectionEvents {

    private static final Map<UUID, BlockPos> PLAYER_CORE_CACHE = new HashMap<>();

    public static void register() {
        // 1. Interacciones y PREVENCIÓN DE SUPERPOSICIÓN
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide) return InteractionResult.PASS;
            ServerLevel serverLevel = (ServerLevel) level;
            ItemStack stack = player.getItemInHand(hand);
            BlockPos pos = hitResult.getBlockPos();

            // A. VALIDAR SUPERPOSICIÓN AL COLOCAR CORE
            if (stack.is(ModBlocks.PROTECTION_CORE.asItem()) || stack.is(ModBlocks.ADMIN_PROTECTOR.asItem())) {
                BlockPos placePos = pos.relative(hitResult.getDirection());
                ProtectionDataManager manager = ProtectionDataManager.get(serverLevel);

                // Suponemos radio inicial de 16 para el nivel 1
                if (manager.isAreaOccupied(placePos, 16, null)) {
                    player.displayClientMessage(Component.literal("§c[!] No puedes colocarlo aquí, choca con otra protección."), true);
                    return InteractionResult.FAIL;
                }
            }

            // B. VALIDAR CONSTRUCCIÓN (Si coloca cualquier otro bloque)
            if (!stack.isEmpty()) {
                BlockPos placePos = pos.relative(hitResult.getDirection());
                if (isBuildRestricted(player, serverLevel, placePos)) return InteractionResult.FAIL;
            }

            return handleBlockInteraction(player, serverLevel, pos);
        });

        // 2. Rotura de bloques (Lógica Bedrock y Drops)
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
            if (level.isClientSide) return true;
            ServerLevel serverLevel = (ServerLevel) level;

            // ADMIN CORE: Irrompible como Bedrock (Solo OP puede quitarlo)
            if (state.is(ModBlocks.ADMIN_PROTECTOR)) {
                if (!player.hasPermissions(2)) {
                    player.displayClientMessage(Component.literal("§c[!] El Núcleo Administrativo es irrompible."), true);
                    return false;
                }
                return true;
            }

            // CORE NORMAL: Drop Nivel 1 al romper
            if (state.is(ModBlocks.PROTECTION_CORE)) {
                if (isBuildRestricted(player, serverLevel, pos)) return false;

                if (!player.isCreative()) {
                    ItemStack tool = player.getMainHandItem();
                    // Requiere nivel de herramienta 2 (Hierro) o superior
                    if (tool.isCorrectToolForDrops(state)) {
                        Block.popResource(level, pos, new ItemStack(ModItems.PROTECTION_CORE));
                    }
                }
                return true;
            }

            return !isBuildRestricted(player, serverLevel, pos);
        });

        // 3. Interacción con Entidades
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide) return InteractionResult.PASS;
            ProtectionCoreBlockEntity core = findCoreAt((ServerLevel) level, entity.blockPosition());

            if (core != null && !core.isTrusted(player)) {
                if (entity instanceof Villager && !core.getFlag("villager-trade")) {
                    player.displayClientMessage(Component.literal("§c[!] Tradeo bloqueado por la protección."), true);
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) return true;

            ServerLevel serverLevel = player.serverLevel();
            // Usamos blockPosition() o directamente las coordenadas
            var core = findCoreAt(serverLevel, player.blockPosition());

            if (core != null) {
                // PVP
                if (source.getEntity() instanceof Player && !core.getFlag("pvp")) return false;

                // FUEGO (Ruta completa para evitar errores de importación)
                if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) && !core.getFlag("fire-damage")) return false;

                // CAÍDA (Ruta completa)
                if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) && !core.getFlag("fall-damage")) return false;
            }
            return true;
        });

    }

    private static InteractionResult handleBlockInteraction(Player player, ServerLevel level, BlockPos pos) {
        ProtectionCoreBlockEntity core = findCoreAt(level, pos);
        if (core == null || pos.equals(core.getBlockPos())) return InteractionResult.PASS;

        // Si es el dueño o es OP, permitimos todo
        if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2)) return InteractionResult.PASS;


        boolean canBreak = core.hasPermission(player.getUUID(), "build");
        boolean canOpenChests = core.hasPermission(player.getUUID(), "chests");
        boolean canInteract = core.hasPermission(player.getUUID(), "interact");

        BlockEntity be = level.getBlockEntity(pos);

        // Validación de Cofres
        if (be instanceof Container) {
            if (canOpenChests) return InteractionResult.PASS;
            player.displayClientMessage(Component.literal("§c[!] No tienes permiso para abrir cofres."), true);
            return InteractionResult.FAIL;
        }

        // Validación de Interacción (Botones, puertas)
        if (!canInteract) {
            // Si tiene permiso de build, por cortesía le dejamos interactuar
            if (canBreak) return InteractionResult.PASS;

            player.displayClientMessage(Component.literal("§c[!] No tienes permiso para interactuar."), true);
            return InteractionResult.FAIL;
        }

        return InteractionResult.PASS;
    }

    private static boolean isBuildRestricted(Player player, ServerLevel level, BlockPos pos) {
        ProtectionCoreBlockEntity core = findCoreAt(level, pos);
        if (core == null || core.isTrusted(player)) return false;

        if (!core.getFlag("build")) {
            String owner = core.isAdmin() ? "la Administración" : core.getOwnerName();
            player.displayClientMessage(Component.literal("§c[!] No puedes construir en la zona de " + owner), true);
            return true;
        }
        return false;
    }

    public static void onPlayerTick(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        ProtectionCoreBlockEntity core = findCoreAt(level, playerPos);

        updateEntryMessage(player, core);

        if (core != null) {
            // Congelar hambre
            if (!core.getFlag("hunger")) {
                player.getFoodData().setFoodLevel(20);
            }

            // Eyectar intrusos
            if (!core.getFlag("entry") && !canBypass(player, core)) {
                ejectPlayer(player, core);
            }
        }
    }

    private static void updateEntryMessage(ServerPlayer player, ProtectionCoreBlockEntity core) {
        UUID uuid = player.getUUID();

        // Si el jugador tiene los avisos desactivados por comando (/protector presentation off)
        if (net.david.mod.command.ClanCommands.PRESENTATION_DISABLED.contains(uuid)) {
            PLAYER_CORE_CACHE.put(uuid, (core != null) ? core.getBlockPos() : null);
            return;
        }

        BlockPos currentCorePos = (core != null) ? core.getBlockPos() : null;
        BlockPos lastCorePos = PLAYER_CORE_CACHE.get(uuid);

        // Solo actuar si la posición del núcleo ha cambiado (entrar, salir o pasar de un core a otro)
        if (!java.util.Objects.equals(lastCorePos, currentCorePos)) {

            // CASO: ENTRANDO a una zona
            if (currentCorePos != null) {
                if (core.isAdmin()) {
                    player.displayClientMessage(Component.literal("§d§l» §fEntrando en: §d§lZONA PROTEGIDA §7(Global)"), true);
                } else {
                    player.displayClientMessage(Component.literal("§b§l» §fTerritorio de: §e" + core.getOwnerName()), true);
                }
                // Sonido opcional de notificación
                player.playNotifySound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.AMBIENT, 0.5f, 1.5f);
            }
            // CASO: SALIENDO a zona libre
            else if (lastCorePos != null) {
                player.displayClientMessage(Component.literal("§c§l« §7Saliendo a zona libre"), true);
            }

            // Actualizar caché
            PLAYER_CORE_CACHE.put(uuid, currentCorePos);
        }
    }

    private static void ejectPlayer(ServerPlayer player, ProtectionCoreBlockEntity core) {
        BlockPos corePos = core.getBlockPos();
        int radius = core.getRadius();

        double dx = player.getX() - (corePos.getX() + 0.5);
        double dz = player.getZ() - (corePos.getZ() + 0.5);
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance < 0.1) dx = 1.0;

        double multiplier = (radius + 2.5) / Math.max(distance, 1.0);
        int tx = (int) (corePos.getX() + 0.5 + (dx * multiplier));
        int tz = (int) (corePos.getZ() + 0.5 + (dz * multiplier));
        int ty = player.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz);

        player.teleportTo(tx + 0.5, ty + 1.0, tz + 0.5);
        player.displayClientMessage(Component.literal("§c§l[!] Entrada restringida."), true);
    }

    public static ProtectionCoreBlockEntity findCoreAt(ServerLevel level, BlockPos pos) {
        var entry = ProtectionDataManager.get(level).getCoreAt(pos);
        if (entry != null && level.getBlockEntity(entry.pos()) instanceof ProtectionCoreBlockEntity core) {
            return core;
        }
        return null;
    }

    private static boolean canBypass(Player player, ProtectionCoreBlockEntity core) {
        return player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2) || core.isTrusted(player);
    }
}
