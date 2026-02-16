package net.david.mod.mixin;

import net.david.mod.event.ProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireSpreadMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        var core = ProtectionEvents.findCoreAt(level, pos);
        if (core != null && !core.getFlag("fire-spread")) {
            level.removeBlock(pos, false); // Apaga el fuego si intenta propagarse
            ci.cancel();
        }
    }
}
