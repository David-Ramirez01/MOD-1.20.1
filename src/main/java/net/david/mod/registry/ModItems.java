package net.david.mod.registry;

import net.david.mod.ProtectorMod;
import net.david.mod.item.ProtectionUpgradeItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class ModItems {

    public static Item PROTECTION_CORE;
    public static Item ADMIN_PROTECTOR;
    public static Item PROTECTION_UPGRADE;

    public static void registerItems() {
        // 1. Registro del Item del Core normal (BlockItem)
        PROTECTION_CORE = registerItem("protection_core",
                new BlockItem(ModBlocks.PROTECTION_CORE, new Item.Properties()
                        .stacksTo(1)
                        .fireResistant()
                ));

        // 2. Registro del Item del Admin Protector (BlockItem)
        ADMIN_PROTECTOR = registerItem("admin_protector",
                new BlockItem(ModBlocks.ADMIN_PROTECTOR, new Item.Properties()
                        .stacksTo(1)
                        .rarity(Rarity.EPIC)
                ));

        // 3. Registro del Item de Mejora (Clase personalizada)
        PROTECTION_UPGRADE = registerItem("protection_upgrade",
                new ProtectionUpgradeItem(new Item.Properties()
                        .stacksTo(16)
                        .rarity(Rarity.RARE)
                ));
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ProtectorMod.MOD_ID, name), item);
    }
}
