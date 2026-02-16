package net.david.mod.blockentity;

import net.david.mod.registry.ModBlockEntities;
import net.david.mod.util.ClanSavedData;
import net.david.mod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public class AdminProtectorBlockEntity extends ProtectionCoreBlockEntity {

    public AdminProtectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADMIN_PROTECTOR_BE, pos, state);
    }

    private boolean canBuildGlobal = false;

    @Override
    public int getRadius() {
        return this.adminRadius;
    }

    public void syncToManager() {
        if (this.level instanceof ServerLevel serverLevel) {
            ProtectionDataManager manager = ProtectionDataManager.get(serverLevel);

            // Recuperamos el estado actual para no resetear la flag accidentalmente
            boolean currentExplosions = false;
            var existing = manager.getCoreAt(this.worldPosition);
            if (existing != null) {
                currentExplosions = existing.allowExplosions();
            }

            // Ahora pasamos los 5 parámetros requeridos
            manager.addCore(this.worldPosition, getOwnerUUID(), getRadius(), true, currentExplosions);
        }
    }

    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            // Al cargar el nivel, aseguramos que el manager tenga el registro actualizado
            syncToManager();
        }
    }

    @Override
    public boolean isTrusted(Player player) {
        if (player.hasPermissions(2) || (getOwnerUUID() != null && player.getUUID().equals(getOwnerUUID()))) {
            return true;
        }

        PlayerPermissions perms = this.permissionsMap.get(player.getUUID());
        if (perms != null && perms.canBuild()) return true;

        if (this.level instanceof ServerLevel serverLevel && this.clanName != null && !this.clanName.isEmpty()) {
            var clan = ClanSavedData.get(serverLevel.getServer()).getClanByMember(player.getUUID());
            if (clan != null && clan.name.equalsIgnoreCase(this.clanName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAdmin() {
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("CanBuildGlobal", this.canBuildGlobal);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.canBuildGlobal = tag.getBoolean("CanBuildGlobal");
    }

    /**
     * Mapeo de flags específicas para administración.
     * Ejemplo: "break" en admin cores se traduce a la lógica de "build".
     */
    @Override
    public boolean getFlag(String flag) {
        if ("break".equals(flag)) {
            return super.getFlag("build");
        }
        return super.getFlag(flag);
    }

    @Override
    public void setAdminRadius(int newRadius) {
        this.adminRadius = newRadius;
        if (this.level instanceof ServerLevel serverLevel) {
            ProtectionDataManager manager = ProtectionDataManager.get(serverLevel);

            // 1. Buscamos el estado actual de la flag para no resetearla a 'false'
            boolean currentExplosionStatus = false;
            var existing = manager.getCoreAt(this.worldPosition);
            if (existing != null) {
                currentExplosionStatus = existing.allowExplosions();
            }

            // 2. Ahora sí, enviamos los 5 argumentos (incluyendo la flag recuperada)
            manager.addCore(this.worldPosition, getOwnerUUID(), newRadius, true, currentExplosionStatus);

            // 3. Sincronizamos con todos los clientes
            manager.syncToAll(serverLevel);
        }
        this.markDirtyAndUpdate();
    }
}