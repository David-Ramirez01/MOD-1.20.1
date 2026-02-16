package net.david.mod.util;

import net.david.mod.network.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProtectionDataManager extends SavedData {
    private static final String DATA_ID = "protection_data";
    private static final UUID SYSTEM_ID = new UUID(0L, 0L);

    private final Map<BlockPos, CoreEntry> cores = new HashMap<>();
    private static final ProtectionDataManager CLIENT_INSTANCE = new ProtectionDataManager();
    private int globalLimit = 3;

    public record CoreEntry(BlockPos pos, UUID owner, int radius, boolean isAdmin, boolean allowExplosions) {}

    public ProtectionDataManager() {}

    // --- ACCESO Y GESTIÓN ---

    public void addCore(BlockPos pos, UUID owner, int radius, boolean isAdmin, boolean allowExplosions) {
        cores.put(pos, new CoreEntry(pos, owner == null ? SYSTEM_ID : owner, radius, isAdmin, allowExplosions));
        this.setDirty();
    }

    public void removeCore(BlockPos pos) {
        if (cores.remove(pos) != null) {
            this.setDirty();
        }
    }

    public boolean isAdmin(BlockPos pos) {
        CoreEntry entry = cores.get(pos);
        return entry != null && entry.isAdmin();
    }

    public CoreEntry getCoreAt(BlockPos targetPos) {
        for (CoreEntry entry : cores.values()) {
            int minX = entry.pos().getX() - entry.radius();
            int maxX = entry.pos().getX() + entry.radius();
            int minZ = entry.pos().getZ() - entry.radius();
            int maxZ = entry.pos().getZ() + entry.radius();

            if (targetPos.getX() >= minX && targetPos.getX() <= maxX &&
                    targetPos.getZ() >= minZ && targetPos.getZ() <= maxZ) {
                return entry;
            }
        }
        return null;
    }

    public boolean isAreaOccupied(BlockPos center, int radius, UUID currentCoreId) {
        for (CoreEntry entry : cores.values()) {
            if (entry.pos().equals(center)) continue;

            int distanceX = Math.abs(center.getX() - entry.pos().getX());
            int distanceZ = Math.abs(center.getZ() - entry.pos().getZ());

            if (distanceX <= (radius + entry.radius()) && distanceZ <= (radius + entry.radius())) {
                return true;
            }
        }
        return false;
    }

    // --- PERSISTENCIA (Servidor) ---

    public static ProtectionDataManager load(CompoundTag tag) {
        ProtectionDataManager data = new ProtectionDataManager();
        data.globalLimit = tag.getInt("globalLimit");

        ListTag list = tag.getList("Cores", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            BlockPos pos = BlockPos.of(entryTag.getLong("pos"));
            UUID owner = entryTag.hasUUID("owner") ? entryTag.getUUID("owner") : SYSTEM_ID;
            int radius = entryTag.getInt("radius");
            boolean isAdmin = entryTag.getBoolean("isAdmin");

            // Cargamos la flag de explosiones (por defecto false si no existe)
            boolean allowExplosions = entryTag.getBoolean("allowExplosions");

            data.cores.put(pos, new CoreEntry(pos, owner, radius, isAdmin, allowExplosions));
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        cores.forEach((pos, entry) -> {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("pos", pos.asLong());
            entryTag.putUUID("owner", entry.owner() != null ? entry.owner() : SYSTEM_ID);
            entryTag.putInt("radius", entry.radius());
            entryTag.putBoolean("isAdmin", entry.isAdmin());

            // Guardamos la flag de explosiones
            entryTag.putBoolean("allowExplosions", entry.allowExplosions());

            list.add(entryTag);
        });
        tag.put("Cores", list);
        tag.putInt("globalLimit", globalLimit);
        return tag;
    }

    // --- SINCRONIZACIÓN ---

    public static ProtectionDataManager get(Level level) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().overworld().getDataStorage().computeIfAbsent(
                    ProtectionDataManager::load,
                    ProtectionDataManager::new,
                    DATA_ID
            );
        }
        return CLIENT_INSTANCE;
    }

    public void syncToAll(ServerLevel level) {
        FriendlyByteBuf buf = PacketByteBufs.create();

        // 1. Escribimos la cantidad de núcleos
        buf.writeInt(cores.size());

        // 2. Por cada núcleo, enviamos EXACTAMENTE lo que el cliente lee
        cores.forEach((pos, entry) -> {
            buf.writeBlockPos(pos);       // 1. Posición
            buf.writeUUID(entry.owner()); // 2. Dueño
            buf.writeInt(entry.radius()); // 3. Radio
            buf.writeBoolean(entry.isAdmin()); // 4. Es Admin
            buf.writeBoolean(entry.allowExplosions()); // 5. <--- ¡FALTABA ESTO!
        });

        // Enviar a todos los jugadores
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, ModNetworking.SYNC_CORES_ID, buf);
        }
    }

    // --- UTILIDADES ---

    public Map<BlockPos, CoreEntry> getAllCores() {
        return cores;
    }

    public void clearAllCores() {
        cores.clear();
        this.setDirty();
    }

    public void setGlobalLimit(int limit) {
        this.globalLimit = limit;
    }

    public int getGlobalLimit() {
        return this.globalLimit;
    }

}