package net.david.mod.registry;

import net.david.mod.ProtectorMod;
import net.david.mod.block.AdminProtectorBlock;
import net.david.mod.block.ProtectionCoreBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class ModBlocks {

    // Usamos FabricBlockSettings para acceder a métodos como nonOpaque()
    public static final Block PROTECTION_CORE = new ProtectionCoreBlock(
            FabricBlockSettings.copyOf(Blocks.IRON_BLOCK)
                    .strength(5.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final Block ADMIN_PROTECTOR = new AdminProtectorBlock(
            FabricBlockSettings.copyOf(Blocks.BEDROCK)
                    .strength(-1.0f, 3600000.0f)
                    .nonOpaque()
                    .noOcclusion()
    );

    public static void registerBlocks() {
        registerBlock("protection_core", PROTECTION_CORE);
        registerBlock("admin_protector", ADMIN_PROTECTOR);
    }

    private static void registerBlock(String name, Block block) {
        Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(ProtectorMod.MOD_ID, name), block);
    }
}