package net.david.mod.mixin;

import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.util.ProtectionDataManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class ItemDropMixin {

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true)
    private void canDrop(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        // Casteo seguro al objeto Mixin
        Player player = (Player) (Object) this;

        // 1. Verificación del lado del servidor
        if (player.level().isClientSide()) return;

        // 2. Buscamos la protección en la posición del jugador
        var dataManager = ProtectionDataManager.get(player.level());
        var entry = dataManager.getCoreAt(player.blockPosition());

        if (entry != null) {
            // 3. Accedemos al BlockEntity a través de la posición guardada en la entrada
            if (player.level().getBlockEntity(entry.pos()) instanceof ProtectionCoreBlockEntity coreBE) {

                // 4. Lógica de permisos: si el flag "item-drop" es falso y no es de confianza
                if (!coreBE.getFlag("item-drop") && !coreBE.isTrusted(player)) {

                    player.displayClientMessage(
                            Component.literal("§c[!] No tienes permiso para tirar objetos en esta zona."),
                            true
                    );

                    // Importante: El método original devuelve un ItemEntity.
                    // Al cancelar, debemos devolver null para que el juego sepa que no se creó la entidad.
                    cir.setReturnValue(null);
                }
            }
        }
    }
}
