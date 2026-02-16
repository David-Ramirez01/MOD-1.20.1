package net.david.mod.util;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Una interfaz de inventario simple para BlockEntities.
 * Basada en las implementaciones estándar de la comunidad de Fabric.
 */
@FunctionalInterface
public interface ImplementedInventory extends Container {

    /**
     * Recupera la lista de items de este inventario.
     * Debe implementarse para retornar el campo NonNullList de la BlockEntity.
     */
    NonNullList<ItemStack> getItems();

    /**
     * Crea una implementación rápida basada en una lista.
     */
    static ImplementedInventory of(NonNullList<ItemStack> items) {
        return () -> items;
    }

    @Override
    default int getContainerSize() {
        return getItems().size();
    }

    @Override
    default boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    default ItemStack getItem(int slot) {
        return getItems().get(slot);
    }

    @Override
    default ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(getItems(), slot, count);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    default ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(getItems(), slot);
    }

    @Override
    default void setItem(int slot, ItemStack stack) {
        getItems().set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    default void clearContent() {
        getItems().clear();
        setChanged();
    }

    @Override
    default void setChanged() {
        // Por defecto no hace nada, la BlockEntity debe sobrescribirlo si necesita marcar persistencia
    }

    @Override
    default boolean stillValid(Player player) {
        return true;
    }
}