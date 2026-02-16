package net.david.mod.menu;

import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.registry.ModBlocks;
import net.david.mod.registry.ModItems;
import net.david.mod.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ProtectionCoreMenu extends AbstractContainerMenu {
    private final ProtectionCoreBlockEntity core;
    private final ContainerLevelAccess access;
    private final Container inventory;
    private final ContainerData data;

    // Constructor para el Servidor
    public ProtectionCoreMenu(int id, Inventory playerInv, ProtectionCoreBlockEntity core) {
        super(core.isAdmin() ? ModMenus.ADMIN_CORE_MENU : ModMenus.PROTECTION_CORE_MENU, id);
        this.core = core;
        this.access = ContainerLevelAccess.create(core.getLevel(), core.getBlockPos());
        this.inventory = core; // Implementa ImplementedInventory

        // Sincronización de nivel (DataSlot en Fabric se suele manejar con ContainerData)
        this.data = new SimpleContainerData(1);
        this.data.set(0, core.getCoreLevel());
        this.addDataSlots(this.data);

        boolean isAdminCore = core.isAdmin();

        if (!isAdminCore) {
            // Slot 0: Mejora
            this.addSlot(new Slot(inventory, 0, 15, 105) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.PROTECTION_UPGRADE);
                }
            });

            // Slot 1: Materiales
            this.addSlot(new Slot(inventory, 1, 35, 105) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return switch (core.getCoreLevel()) {
                        case 1 -> stack.is(Items.IRON_INGOT);
                        case 2 -> stack.is(Items.GOLD_INGOT);
                        case 3 -> stack.is(Items.DIAMOND);
                        case 4 -> stack.is(Items.NETHERITE_INGOT);
                        default -> false;
                    };
                }
            });
        }

        addPlayerInventory(playerInv, 8, 140);
        addPlayerHotbar(playerInv, 8, 198);
    }

    // Constructor para el Cliente (Invocado por Fabric)
    public ProtectionCoreMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, (ProtectionCoreBlockEntity) playerInv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    private void addPlayerInventory(Inventory inv, int xStart, int yStart) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, xStart + col * 18, yStart + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory inv, int xStart, int yStart) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inv, col, xStart + col * 18, yStart));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.PROTECTION_CORE)
                || stillValid(this.access, player, ModBlocks.ADMIN_PROTECTOR);
    }

    public ProtectionCoreBlockEntity getBlockEntity() {
        return this.core;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        boolean isAdmin = core.isAdmin();
        int containerSlots = isAdmin ? 0 : 2;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < containerSlots) {
                if (!this.moveItemStackTo(stack, containerSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (containerSlots > 0) {
                    if (!this.moveItemStackTo(stack, 0, containerSlots, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }
}

