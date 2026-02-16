package net.david.mod.registry;

import net.david.mod.ProtectorMod;
import net.david.mod.menu.ProtectionCoreMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

public class ModMenus {
    // En Fabric usamos ScreenHandlerType (MenuType en Mojmap)
    public static MenuType<ProtectionCoreMenu> PROTECTION_CORE_MENU;
    public static MenuType<ProtectionCoreMenu> ADMIN_CORE_MENU;

    public static void registerMenus() {
        // Registro del Menú Normal
        PROTECTION_CORE_MENU = Registry.register(
                BuiltInRegistries.MENU,
                new ResourceLocation(ProtectorMod.MOD_ID, "protection_core_menu"),
                new ExtendedScreenHandlerType<>(ProtectionCoreMenu::new)
        );

        // Registro del Menú Admin
        ADMIN_CORE_MENU = Registry.register(
                BuiltInRegistries.MENU,
                new ResourceLocation(ProtectorMod.MOD_ID, "admin_core_menu"),
                new ExtendedScreenHandlerType<>(ProtectionCoreMenu::new)
        );
    }
}