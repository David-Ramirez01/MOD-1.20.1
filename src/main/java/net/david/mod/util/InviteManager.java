package net.david.mod.util;

import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InviteManager {

    // Almacén de invitaciones pendientes
    private static final Map<UUID, PendingInvite> PENDING_INVITES = new ConcurrentHashMap<>();

    // Duración por defecto: 60 segundos
    private static final long INVITE_DURATION = 60_000L;

    /**
     * Registro de la invitación
     */
    public record PendingInvite(BlockPos corePos, UUID requesterUUID, long expiry) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiry;
        }
    }



    /**
     * Registra una invitación para un jugador.
     */
    public static void addInvite(UUID targetUUID, BlockPos pos, UUID requester) {
        PENDING_INVITES.put(targetUUID, new PendingInvite(pos, requester, System.currentTimeMillis() + INVITE_DURATION));
    }

    /**
     * Obtiene la invitación válida. Si expiró, la elimina automáticamente.
     */
    public static PendingInvite getInvite(UUID uuid) {
        PendingInvite invite = PENDING_INVITES.get(uuid);

        if (invite != null) {
            if (invite.isExpired()) {
                PENDING_INVITES.remove(uuid);
                return null;
            }
        }
        return invite;
    }

    /**
     * Elimina la invitación (uso al aceptar/rechazar).
     */
    public static void removeInvite(UUID uuid) {
        PENDING_INVITES.remove(uuid);
    }

    /**
     * Limpieza preventiva. Se puede llamar desde un evento de guardado del servidor
     * para eliminar basura acumulada de jugadores que nunca respondieron.
     */
    public static void cleanExpiredInvites() {
        PENDING_INVITES.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
