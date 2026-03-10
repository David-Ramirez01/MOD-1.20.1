package net.david.mod.mixin;

import net.david.mod.util.ProtectionDataManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class MobGriefMixin {

    @Inject(method = "canHoldItem", at = @At("HEAD"), cancellable = true)
    private void preventMobPickup(net.minecraft.world.item.ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Mob mob = (Mob) (Object) this;
        Level level = mob.level();
        if (level.isClientSide) return;

        if (ProtectionDataManager.get(level).getCoreAt(mob.blockPosition()) != null) {
            cir.setReturnValue(false);
        }
    }
}