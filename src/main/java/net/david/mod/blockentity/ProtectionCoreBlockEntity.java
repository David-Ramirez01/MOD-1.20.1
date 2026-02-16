package net.david.mod.blockentity;

import net.david.mod.block.ProtectionCoreBlock;
import net.david.mod.menu.ProtectionCoreMenu;
import net.david.mod.network.ModNetworking;
import net.david.mod.registry.*;
import net.david.mod.util.ClanSavedData;
import net.david.mod.util.ImplementedInventory;
import net.david.mod.util.ProtectionDataManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ProtectionCoreBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory, ImplementedInventory {

    private int coreLevel = 1;
    private int range = 10;
    protected int adminRadius = 128;
    private UUID ownerUUID;
    private String ownerName = "Protector";
    protected String clanName = "";

    private final Map<String, Boolean> flags = new HashMap<>();
    public final Map<UUID, PlayerPermissions> permissionsMap = new HashMap<>();
    private final Map<UUID, String> nameCache = new HashMap<>();
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(2, ItemStack.EMPTY);

    public static final List<String> BASIC_FLAGS = List.of("pvp", "build", "chests", "interact", "villager-trade", "fire-damage", "hunger");
    public static final List<String> ADMIN_FLAGS = List.of("explosions", "mob-spawn", "entry", "fall-damage", "fire-spread", "lighter", "item-pickup", "mob-grief", "use-buckets", "item-drop", "crop-trample", "enderpearl");

    protected ProtectionCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        initializeDefaultFlags();
    }

    public ProtectionCoreBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.PROTECTION_CORE_BE, pos, state);
    }

    private void initializeDefaultFlags() {
        for (String f : BASIC_FLAGS) flags.put(f, false);
        for (String f : ADMIN_FLAGS) flags.put(f, false);
        flags.put("entry", true);
        flags.put("hunger", true);
        flags.put("item-pickup", true);
    }

    public List<UUID> getTrustedPlayers() {
        return new ArrayList<>(this.permissionsMap.keySet());
    }

    public boolean hasPermission(UUID playerUuid, String flag) {
        // 1. Dueño y Administración siempre tienen permiso total
        if (playerUuid.equals(this.ownerUUID) || this.isAdmin()) {
            return true;
        }

        // 2. ¿Es un invitado con permisos personalizados?
        PlayerPermissions perms = this.permissionsMap.get(playerUuid);
        if (perms != null) {
            return switch (flag.toLowerCase()) {
                case "build" -> perms.canBuild();
                case "interact" -> perms.canInteract();
                case "chests" -> perms.canOpenChests();
                case "admin" -> perms.isAdmin();
                default -> getFlag(flag); // Para flags que no están en PlayerPermissions (ej. pvp, hunger)
            };
        }

        return getFlag(flag);
    }

    public String getNameFromUUID(java.util.UUID uuid) {
        return nameCache.getOrDefault(uuid, "Desconocido");
    }

    public void removeGuestFully(UUID uuid) {
        this.permissionsMap.remove(uuid);
        this.nameCache.remove(uuid);
        this.markDirtyAndUpdate(); // Esto ya hace el setChanged y el sync
    }

    // --- SINCRONIZACIÓN Y NIVELES ---

    public void setCoreLevelClient(int level) {
        this.coreLevel = level;
        this.range = obtenerRadioPorNivel(level);
        if (this.level != null) {
            this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.setChanged();
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }


    public void syncLevelToClients() {
        if (this.level instanceof ServerLevel serverLevel) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(this.worldPosition);
            buf.writeInt(this.coreLevel);
            for (ServerPlayer player : serverLevel.players()) {
                ServerPlayNetworking.send(player, ModNetworking.SYNC_LEVEL_ID, buf);
            }
        }
    }

    private int obtenerRadioPorNivel(int nivel) {
        return switch (nivel) {
            case 1 -> 8; case 2 -> 16; case 3 -> 32; case 4 -> 64; case 5 -> 128;
            default -> 8;
        };
    }

    public void setAdminRadius(int newRadius) {
        this.adminRadius = newRadius;
        this.markDirtyAndUpdate();
    }

    public int getCoreLevel() {
        return this.coreLevel;
    }

    public List<String> getTrustedNames() {
        return new ArrayList<>(this.nameCache.values());
    }

    public NonNullList<ItemStack> getInventory() {
        return this.inventory;
    }

    public PlayerPermissions getPermissionsFor(String playerName) {
        for (Map.Entry<UUID, String> entry : nameCache.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(playerName)) {
                return permissionsMap.get(entry.getKey());
            }
        }
        return null;
    }

    public boolean canPlayerEditFlag(Player player, String flagId) {
        // Dueños y OPs pueden editar todo
        if (player.getUUID().equals(this.ownerUUID) || player.hasPermissions(2)) return true;

        // Los administradores del core (según tu permissionsMap) también podrían
        PlayerPermissions perms = this.permissionsMap.get(player.getUUID());
        return perms != null && perms.isAdmin();
    }

    // --- GESTIÓN DE PERMISOS ---

    public boolean isTrusted(Player player) {
        if (player.getUUID().equals(this.ownerUUID) || player.hasPermissions(2)) return true;

        if (this.level instanceof ServerLevel serverLevel) {
            var clan = ClanSavedData.get(serverLevel.getServer()).getClanByMember(player.getUUID());
            if (clan != null && clan.name.equalsIgnoreCase(this.clanName)) return true;
        }

        PlayerPermissions perms = this.permissionsMap.get(player.getUUID());
        return perms != null && perms.canBuild();
    }

    public void updatePermission(UUID uuid, String name, String type, boolean val) {
        PlayerPermissions perms = permissionsMap.computeIfAbsent(uuid, k -> new PlayerPermissions());
        nameCache.put(uuid, name); // Asegura que el nombre esté vinculado al UUID

        switch (type.toLowerCase()) {
            case "build" -> perms.setCanBuild(val);
            case "interact" -> perms.setCanInteract(val);
            case "chests" -> perms.setCanOpenChests(val);
            case "admin" -> perms.setAdmin(val);
        }

        // Al marcar dirty y enviar actualización, la GUI del cliente se refresca
        markDirtyAndUpdate();
    }

    // --- MEJORA (UPGRADE) ---

    public void upgrade(ServerPlayer player) {
        if (isAdmin() || coreLevel >= 5) return;
        if (!canUpgrade()) {
            player.displayClientMessage(Component.literal("§c[!] No tienes los materiales necesarios."), true);
            return;
        }

        // Consumir items
        this.removeItem(0, 1);
        this.removeItem(1, (coreLevel == 1) ? 64 : 32);

        this.coreLevel++;
        this.range = obtenerRadioPorNivel(coreLevel);

        if (this.level instanceof ServerLevel sLevel) {
            // 1. SONIDO Y PARTÍCULAS EN EL MUNDO (En la posición del Core)
            sLevel.playSound(null, worldPosition, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.0f, 1.0f);

            //2. Generar una explosión de partículas esmeralda y tótem
            sLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5,
                    50, 0.4, 0.4, 0.4, 0.15);

            sLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5,
                    20, 0.5, 0.5, 0.5, 0.1);

            ProtectionDataManager manager = ProtectionDataManager.get(sLevel);

            boolean currentExplosionStatus = false;
            if (manager.getCoreAt(this.worldPosition) != null) {
                currentExplosionStatus = manager.getCoreAt(this.worldPosition).allowExplosions();
            }

            // 3. Sincronización
            actualizarEstadosBloque();
            ProtectionDataManager.get(sLevel).addCore(this.worldPosition, getOwnerUUID(), getRadius(), isAdmin(), currentExplosionStatus );
            syncLevelToClients();
            player.closeContainer();
            player.displayClientMessage(Component.literal("§6§l[!] §e¡Núcleo mejorado al Nivel " + coreLevel + "!"), true);
        }

        markDirtyAndUpdate();
    }

    public boolean canUpgrade() {
        ItemStack upgradeItem = this.getItem(0);
        ItemStack materials = this.getItem(1);
        if (!upgradeItem.is(ModItems.PROTECTION_UPGRADE)) return false;

        return switch (coreLevel) {
            case 1 -> materials.is(Items.IRON_INGOT) && materials.getCount() >= 64;
            case 2 -> materials.is(Items.GOLD_INGOT) && materials.getCount() >= 32;
            case 3 -> materials.is(Items.DIAMOND) && materials.getCount() >= 32;
            case 4 -> materials.is(Items.NETHERITE_INGOT) && materials.getCount() >= 32;
            default -> false;
        };
    }

    // --- PERSISTENCIA (NBT) ---

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("CoreLevel", this.coreLevel);
        tag.putInt("Range", this.range);
        tag.putBoolean("IsAdminCore", isAdmin());
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putString("OwnerName", ownerName);
        tag.putString("ClanName", clanName);
        tag.putInt("AdminRadius", this.adminRadius);

        ListTag permsList = new ListTag();
        permissionsMap.forEach((uuid, perms) -> {
            CompoundTag pTag = new CompoundTag();
            pTag.putUUID("uuid", uuid);
            pTag.putString("name", nameCache.getOrDefault(uuid, "Desconocido"));
            pTag.putBoolean("build", perms.canBuild());
            pTag.putBoolean("interact", perms.canInteract());
            pTag.putBoolean("chests", perms.canOpenChests());
            pTag.putBoolean("admin", perms.isAdmin());
            permsList.add(pTag);
        });
        tag.put("PermissionsList", permsList);

        CompoundTag flagsTag = new CompoundTag();
        flags.forEach(flagsTag::putBoolean);
        tag.put("Flags", flagsTag);

        ContainerHelper.saveAllItems(tag, this.inventory);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.coreLevel = tag.getInt("CoreLevel");
        this.range = tag.getInt("Range");
        if (tag.hasUUID("Owner")) this.ownerUUID = tag.getUUID("Owner");
        this.ownerName = tag.getString("OwnerName");
        this.clanName = tag.getString("ClanName");
        if (tag.contains("AdminRadius")) {
            this.adminRadius = tag.getInt("AdminRadius"); // Faltaba esto
        }

        ListTag permsList = tag.getList("PermissionsList", 10);
        permissionsMap.clear();
        for (int i = 0; i < permsList.size(); i++) {
            CompoundTag pTag = permsList.getCompound(i);
            UUID uuid = pTag.getUUID("uuid");
            nameCache.put(uuid, pTag.getString("name"));
            permissionsMap.put(uuid, new PlayerPermissions(
                    pTag.getBoolean("build"),
                    pTag.getBoolean("interact"),
                    pTag.getBoolean("chests"),
                    pTag.getBoolean("admin")
            ));
        }

        CompoundTag flagsTag = tag.getCompound("Flags");
        for (String key : flagsTag.getAllKeys()) {
            flags.put(key, flagsTag.getBoolean(key));
        }

        ContainerHelper.loadAllItems(tag, this.inventory);
    }

    // --- INTERFACES Y MÉTODOS DE BLOQUE ---

    public int getRadius() { return isAdmin() ? adminRadius : range; }
    public boolean isAdmin() { return getBlockState().is(ModBlocks.ADMIN_PROTECTOR); }
    public void markDirtyAndUpdate() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public boolean getFlag(String flag) { return flags.getOrDefault(flag, false); }
    public void setFlag(String flag, boolean val) { flags.put(flag, val); markDirtyAndUpdate(); }

    private void actualizarEstadosBloque() {
        if (this.level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(ProtectionCoreBlock.LEVEL)) {
                level.setBlock(worldPosition, state.setValue(ProtectionCoreBlock.LEVEL, coreLevel), 3);
            }
        }
    }

    public void removePlayerPermissions(String playerName) {
        UUID toRemove = null;
        for (Map.Entry<UUID, String> entry : nameCache.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(playerName)) {
                toRemove = entry.getKey();
                break;
            }
        }
        if (toRemove != null) {
            permissionsMap.remove(toRemove);
            nameCache.remove(toRemove);
            markDirtyAndUpdate();
        }
    }


    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        buf.writeBlockPos(this.worldPosition);
        buf.writeInt(isAdmin() ? this.adminRadius : this.range);
        buf.writeInt(this.coreLevel);
        buf.writeBoolean(this.isAdmin());
    }


    @Override public NonNullList<ItemStack> getItems() { return inventory; }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new ProtectionCoreMenu(id, inv, this); }
    @Override public Component getDisplayName() { return Component.literal("Núcleo de Protección"); }
    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public void setOwner(Player player) {
        this.ownerUUID = player.getUUID();
        this.ownerName = player.getName().getString();
        markDirtyAndUpdate();
    }

    public static class PlayerPermissions {
        private boolean build, interact, chests, admin;

        public PlayerPermissions() {}

        public PlayerPermissions(boolean build, boolean interact, boolean chests, boolean admin) {
            this.build = build;
            this.interact = interact;
            this.chests = chests;
            this.admin = admin;
        }

        // Getters que la GUI (Screen) necesita
        public boolean canBuild() { return build; }
        public boolean canInteract() { return interact; }
        public boolean canOpenChests() { return chests; }
        public boolean isAdmin() { return admin; }

        // Setters para ModNetworking
        public void setCanBuild(boolean v) { build = v; }
        public void setCanInteract(boolean v) { interact = v; }
        public void setCanOpenChests(boolean v) { chests = v; }
        public void setAdmin(boolean v) { admin = v; }
    }

}

