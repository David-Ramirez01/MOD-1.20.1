package net.david.mod.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class NbtUtil {

    /**
     * Guarda un UUID de forma segura.
     */
    public static void putUUID(CompoundTag tag, String key, UUID uuid) {
        if (uuid != null) {
            tag.putUUID(key, uuid);
        }
    }

    /**
     * Recupera un UUID, devolviendo null si no existe.
     */
    public static UUID getUUID(CompoundTag tag, String key) {
        return (tag != null && tag.hasUUID(key)) ? tag.getUUID(key) : null;
    }

    /**
     * Guarda una colección de UUIDs (Set o List) en un ListTag.
     * He cambiado Set por Collection para hacerlo más versátil.
     */
    public static void putUUIDCollection(CompoundTag tag, String key, Collection<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) return;

        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            CompoundTag uuidEntry = new CompoundTag();
            uuidEntry.putUUID("id", uuid);
            list.add(uuidEntry);
        }
        tag.put(key, list);
    }

    /**
     * Recupera una lista de UUIDs desde un ListTag.
     * Utiliza constantes de Tag para mayor legibilidad y seguridad.
     */
    public static List<UUID> getUUIDList(CompoundTag tag, String key) {
        List<UUID> uuids = new ArrayList<>();

        // Tag.TAG_LIST = 9, Tag.TAG_COMPOUND = 10
        if (tag != null && tag.contains(key, Tag.TAG_LIST)) {
            ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (entry.hasUUID("id")) {
                    uuids.add(entry.getUUID("id"));
                }
            }
        }
        return uuids;
    }
}