package net.david.mod.mixin;

import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.util.ProtectionDataManager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemPickupMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void canPickup(Player player, CallbackInfo ci) {
        // 1. Verificación básica: Si el item ya está marcado para ser removido o es cliente, ignorar.
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (player.level().isClientSide || itemEntity.isRemoved()) return;

        Level level = player.level();

        // 2. Usamos la posición del ItemEntity en lugar de la del jugador.
        // Esto evita que un jugador "fuera" de la zona protegida aspire items que están "dentro".
        var entry = ProtectionDataManager.get(level).getCoreAt(itemEntity.blockPosition());

        if (entry != null) {
            if (level.getBlockEntity(entry.pos()) instanceof ProtectionCoreBlockEntity coreBE) {
                // 3. Verificamos el flag y la confianza
                if (!coreBE.getFlag("item-pickup") && !coreBE.isTrusted(player)) {
                    // 4. Opcional: Podrías añadir un cooldown para que no spamee mensajes al jugador
                    ci.cancel();
                }
            }
        }
    }
}
