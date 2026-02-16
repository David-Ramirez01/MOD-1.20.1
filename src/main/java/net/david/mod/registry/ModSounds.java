package net.david.mod.registry;

import net.david.mod.ProtectorMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {

    // El SoundEvent se registra directamente como una constante estática
    public static final SoundEvent CORE_UPGRADE = registerSoundEvent("core_upgrade");

    /**
     * Registra el evento de sonido en el registro estándar de Minecraft.
     */
    private static SoundEvent registerSoundEvent(String name) {
        ResourceLocation id = new ResourceLocation(ProtectorMod.MOD_ID, name);
        // En Fabric 1.20.1, SoundEvent.createVariableRangeEvent es el estándar
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /**
     * Este método se llama desde el onInitialize de tu Mod principal.
     */
    public static void registerSounds() {
        // En Fabric, el simple hecho de acceder a la clase carga las constantes estáticas
        ProtectorMod.LOGGER.info("Registrando sonidos para " + ProtectorMod.MOD_ID);
    }
}

