package net.david.mod.mixin;

import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.util.ProtectionDataManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderpearlItem.class)
public abstract class EnderpearlMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void preventEnderpearl(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (level.isClientSide()) return;

        // Detecta si el ORIGEN del lanzamiento está protegido
        var entry = ProtectionDataManager.get(level).getCoreAt(player.blockPosition());

        if (entry != null && level.getBlockEntity(entry.pos()) instanceof ProtectionCoreBlockEntity coreBE) {
            if (!coreBE.getFlag("enderpearl") && !coreBE.isTrusted(player)) {
                player.displayClientMessage(Component.literal("§c[!] No puedes lanzar perlas desde esta zona."), true);
                cir.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
            }
        }
    }
}
