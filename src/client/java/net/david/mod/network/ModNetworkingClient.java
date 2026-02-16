package net.david.mod.network;

import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.client.ProtectionAreaEffect;
import net.david.mod.util.ProtectionDataManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft; // Importante para tickEffects
import net.minecraft.core.BlockPos;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class ModNetworkingClient {

    public static final List<ProtectionAreaEffect> ACTIVE_EFFECTS = new ArrayList<>();

    public static void registerClientPackets() {

        // 1. SINCRONIZACIÓN GLOBAL
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SYNC_CORES_ID, (client, handler, buf, responseSender) -> {
            int size = buf.readInt();
            List<ProtectionDataManager.CoreEntry> receivedCores = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                BlockPos pos = buf.readBlockPos();
                UUID owner = buf.readUUID();
                int radius = buf.readInt();
                boolean isAdmin = buf.readBoolean();
                boolean allowExplosions = buf.readBoolean();
                receivedCores.add(new ProtectionDataManager.CoreEntry(pos, owner, radius, isAdmin, allowExplosions));
            }

            client.execute(() -> {
                if (client.level != null) {
                    ProtectionDataManager data = ProtectionDataManager.get(client.level);
                    data.getAllCores().clear();
                    for (ProtectionDataManager.CoreEntry entry : receivedCores) {
                        data.addCore(entry.pos(), entry.owner(), entry.radius(), entry.isAdmin(), entry.allowExplosions());
                    }
                }
            });
        });

        // 2. SINCRONIZACIÓN DE NIVEL
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SYNC_LEVEL_ID, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int level = buf.readInt();

            client.execute(() -> {
                if (client.level != null && client.level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                    core.setCoreLevelClient(level);
                }
            });
        });

        // 3. GENERAR PARTÍCULAS (Refactorizado con isAdmin)
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SPAWN_BOUNDARY_PARTICLES, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int radius = buf.readInt();

            client.execute(() -> {
                if (client.level == null) return;

                ACTIVE_EFFECTS.removeIf(effect -> effect.getPos().equals(pos));

                // Obtenemos la BlockEntity y verificamos si es Admin
                boolean isAdmin = false;
                if (client.level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                    isAdmin = core.isAdmin();
                }

                ACTIVE_EFFECTS.add(new ProtectionAreaEffect(pos, radius, 200, isAdmin));
            });
        });
    }

    /**
     * CORRECCIÓN: Ahora pasamos el level al método tick
     */
    public static void tickEffects() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Iterator<ProtectionAreaEffect> iterator = ACTIVE_EFFECTS.iterator();
        while (iterator.hasNext()) {
            ProtectionAreaEffect effect = iterator.next();

            // Pasamos client.level porque el método tick lo requiere para spawnear partículas
            if (!effect.tick(client.level)) {
                iterator.remove();
            }
        }
    }
}