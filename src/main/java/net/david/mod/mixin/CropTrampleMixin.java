package net.david.mod.mixin;

import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmBlock.class)
public abstract class CropTrampleMixin {

    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void cancelTrample(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
        // 1. Solo procesamos en el servidor para evitar desincronizaciones visuales
        if (level.isClientSide()) return;

        // 2. Buscamos la protección en la posición exacta del bloque de cultivo (pos)
        var entry = ProtectionDataManager.get(level).getCoreAt(pos);

        if (entry != null && level.getBlockEntity(entry.pos()) instanceof ProtectionCoreBlockEntity coreBE) {

            // 3. Si el flag 'crop-trample' es falso, aplicamos la restricción
            if (!coreBE.getFlag("crop-trample")) {

                // Lógica de validación:
                // - Si NO es un jugador (es un mob como un Creeper o un Cerdo): Bloqueado.
                // - Si ES un jugador pero NO está en la lista de confianza: Bloqueado.
                if (!(entity instanceof Player player) || !coreBE.isTrusted(player)) {
                    // Cancelamos el método original para que la tierra no se rompa
                    ci.cancel();
                }
            }
        }
    }
}
