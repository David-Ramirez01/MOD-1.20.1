package net.david.mod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;

public class ClanManager {

    /**
     * Intenta crear un clan validando disponibilidad de nombre y estado del jugador.
     */
    public static boolean createClan(MinecraftServer server, String name, UUID owner, String ownerName, BlockPos pos) {
        ClanSavedData data = ClanSavedData.get(server);

        // 1. Validar nombre (no nulo y longitud mínima)
        if (name == null || name.length() < 3) return false;

        // 2. Verificar si el nombre ya existe
        if (data.getClanByName(name) != null) {
            return false;
        }

        // 3. Verificar si el jugador ya pertenece a algún clan (como líder o miembro)
        if (data.getClanByMember(owner) != null) {
            return false;
        }

        // 4. Lógica de creación
        return data.tryCreateClan(name, owner, ownerName, pos);
    }

    /**
     * Verifica si un jugador pertenece al clan que posee una protección.
     */
    public static boolean hasAccessToClan(MinecraftServer server, UUID playerUUID, String clanName) {
        ClanSavedData data = ClanSavedData.get(server);
        ClanSavedData.ClanInstance clan = data.getClanByName(clanName);

        if (clan == null) return false;

        // El acceso se concede si es líder o miembro registrado
        return clan.leaderUUID.equals(playerUUID) || clan.members.contains(playerUUID);
    }

    /**
     * Verifica si dos jugadores pertenecen al mismo clan (útil para desactivar Friendly Fire).
     */
    public static boolean areInSameClan(MinecraftServer server, UUID player1, UUID player2) {
        ClanSavedData data = ClanSavedData.get(server);
        ClanSavedData.ClanInstance clan1 = data.getClanByMember(player1);

        if (clan1 == null) return false;

        ClanSavedData.ClanInstance clan2 = data.getClanByMember(player2);
        return clan1.equals(clan2);
    }

    /**
     * Disuelve un clan (Solo si es el líder).
     */
    public static boolean dissolveClan(MinecraftServer server, UUID leaderUUID) {
        ClanSavedData data = ClanSavedData.get(server);
        if (data.getClanByLeader(leaderUUID) != null) {
            data.deleteClan(leaderUUID);
            return true;
        }
        return false;
    }
}