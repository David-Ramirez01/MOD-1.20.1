package net.david.mod.mixin;

import net.david.mod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {

    @Shadow @Final private Level level;
    @Shadow @Final private double x;
    @Shadow @Final private double y;
    @Shadow @Final private double z;

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void onExplode(CallbackInfo ci) {
        BlockPos explosionPos = BlockPos.containing(this.x, this.y, this.z);
        ProtectionDataManager data = ProtectionDataManager.get(this.level);

        ProtectionDataManager.CoreEntry entry = data.getCoreAt(explosionPos);

        if (entry != null && !entry.allowExplosions()) {
            ci.cancel();
        }
    }
}
