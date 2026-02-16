package net.david.mod.mixin;

import net.david.mod.event.ProtectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class HungerMixin {
    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
    private void onExhaustion(float exhaustion, CallbackInfo ci) {
        Player player = (Player)(Object)this;
        if (!player.level().isClientSide) {
            var core = ProtectionEvents.findCoreAt((ServerLevel)player.level(), player.blockPosition());
            if (core != null && !core.getFlag("hunger")) {
                ci.cancel(); // No hay desgaste de comida
            }
        }
    }
}
