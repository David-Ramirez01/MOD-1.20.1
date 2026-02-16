package net.david.mod.registry;

import net.david.mod.ProtectorMod;
import net.david.mod.blockentity.AdminProtectorBlockEntity;
import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

public class ModBlockEntities {

    public static BlockEntityType<ProtectionCoreBlockEntity> PROTECTION_CORE_BE;
    public static BlockEntityType<AdminProtectorBlockEntity> ADMIN_PROTECTOR_BE;

    public static void registerBlockEntities() {
        // Registro del Núcleo de Protección
        PROTECTION_CORE_BE = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                new ResourceLocation(ProtectorMod.MOD_ID, "protection_core_be"),
                FabricBlockEntityTypeBuilder.create(ProtectionCoreBlockEntity::new, ModBlocks.PROTECTION_CORE).build(null)
        );

        // Registro del Protector de Administrador
        ADMIN_PROTECTOR_BE = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                new ResourceLocation(ProtectorMod.MOD_ID, "admin_protector_be"),
                FabricBlockEntityTypeBuilder.create(AdminProtectorBlockEntity::new, ModBlocks.ADMIN_PROTECTOR).build(null)
        );
    }
}

