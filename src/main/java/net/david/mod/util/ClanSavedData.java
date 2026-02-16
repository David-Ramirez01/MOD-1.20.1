package net.david.mod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public class ClanSavedData extends SavedData {
    private static final String DATA_ID = "protector_clans";

    public int serverMaxCores = 3;

    // Mapa principal: ID (minúsculas) -> Clan
    private final Map<String, ClanInstance> clans = new HashMap<>();

    // Índices rápidos para optimización (No se guardan en NBT)
    private final Map<UUID, String> memberToClanMap = new HashMap<>();
    private final Map<UUID, String> leaderToClanMap = new HashMap<>();

    public ClanSavedData() {}

    public static ClanSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ClanSavedData::load,
                ClanSavedData::new,
                DATA_ID
        );
    }

    // --- LÓGICA DE NEGOCIO ---

    public boolean tryCreateClan(String name, UUID leaderUUID, String leaderName, BlockPos pos) {
        String id = name.toLowerCase();

        // Validaciones: No duplicados de nombre ni líderes que ya tengan clan
        if (this.clans.containsKey(id) || memberToClanMap.containsKey(leaderUUID)) {
            return false;
        }

        ClanInstance newClan = new ClanInstance(name, leaderUUID, leaderName, pos);
        this.clans.put(id, newClan);
        registerInCache(id, newClan);

        this.setDirty();
        return true;
    }

    public void deleteClan(UUID leaderUUID) {
        String clanId = leaderToClanMap.get(leaderUUID);
        if (clanId != null) {
            ClanInstance clan = clans.remove(clanId);
            if (clan != null) {
                // Limpiar índices de todos los miembros
                leaderToClanMap.remove(clan.leaderUUID);
                clan.members.forEach(memberToClanMap::remove);
                this.setDirty();
            }
        }
    }

    public void addMemberToClan(String clanId, UUID playerUUID) {
        ClanInstance clan = clans.get(clanId.toLowerCase());
        if (clan != null && clan.members.size() < clan.maxMembers) {
            clan.members.add(playerUUID);
            memberToClanMap.put(playerUUID, clanId.toLowerCase());
            this.setDirty();
        }
    }

    public void removeMemberFromClan(UUID playerUUID) {
        String clanId = memberToClanMap.get(playerUUID);
        if (clanId != null) {
            ClanInstance clan = clans.get(clanId);
            if (clan != null && !clan.leaderUUID.equals(playerUUID)) {
                clan.members.remove(playerUUID);
                memberToClanMap.remove(playerUUID);
                this.setDirty();
            }
        }
    }

    // --- BÚSQUEDAS RÁPIDAS ---

    public ClanInstance getClanByMember(UUID playerUUID) {
        String clanId = memberToClanMap.get(playerUUID);
        return clanId != null ? clans.get(clanId) : null;
    }

    public ClanInstance getClanByLeader(UUID leaderUUID) {
        String clanId = leaderToClanMap.get(leaderUUID);
        return clanId != null ? clans.get(clanId) : null;
    }

    public ClanInstance getClanByName(String name) {
        return name == null ? null : clans.get(name.toLowerCase());
    }

    private void registerInCache(String clanId, ClanInstance clan) {
        leaderToClanMap.put(clan.leaderUUID, clanId);
        for (UUID member : clan.members) {
            memberToClanMap.put(member, clanId);
        }
    }

    // --- PERSISTENCIA ---

    public static ClanSavedData load(CompoundTag tag) {
        ClanSavedData data = new ClanSavedData();
        data.serverMaxCores = tag.contains("MaxCoresLimit") ? tag.getInt("MaxCoresLimit") : 3;

        CompoundTag clanListTag = tag.getCompound("Clans");
        for (String id : clanListTag.getAllKeys()) {
            CompoundTag cTag = clanListTag.getCompound(id);

            ClanInstance clan = new ClanInstance(
                    cTag.getString("Name"),
                    cTag.getUUID("Leader"),
                    cTag.getString("LeaderName"),
                    BlockPos.of(cTag.getLong("Pos"))
            );
            clan.maxMembers = cTag.getInt("MaxMembers");

            ListTag membersTag = cTag.getList("Members", Tag.TAG_STRING);
            for (int i = 0; i < membersTag.size(); i++) {
                try {
                    clan.members.add(UUID.fromString(membersTag.getString(i)));
                } catch (Exception ignored) {}
            }

            data.clans.put(id, clan);
            data.registerInCache(id, clan);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("MaxCoresLimit", serverMaxCores);

        CompoundTag clanListTag = new CompoundTag();
        clans.forEach((id, clan) -> {
            CompoundTag cTag = new CompoundTag();
            cTag.putString("Name", clan.name);
            cTag.putUUID("Leader", clan.leaderUUID);
            cTag.putString("LeaderName", clan.leaderName);
            cTag.putLong("Pos", clan.corePos.asLong());
            cTag.putInt("MaxMembers", clan.maxMembers);

            ListTag membersTag = new ListTag();
            for (UUID m : clan.members) {
                membersTag.add(StringTag.valueOf(m.toString()));
            }
            cTag.put("Members", membersTag);
            clanListTag.put(id, cTag);
        });

        tag.put("Clans", clanListTag);
        return tag;
    }

    // --- CLASE DE INSTANCIA ---
    public static class ClanInstance {
        public final String name;
        public final UUID leaderUUID;
        public String leaderName;
        public BlockPos corePos;
        public int maxMembers = 8;
        public final Set<UUID> members = new HashSet<>();

        public ClanInstance(String name, UUID leaderUUID, String leaderName, BlockPos pos) {
            this.name = name;
            this.leaderUUID = leaderUUID;
            this.leaderName = leaderName;
            this.corePos = pos;
            this.members.add(leaderUUID);
        }
    }
}