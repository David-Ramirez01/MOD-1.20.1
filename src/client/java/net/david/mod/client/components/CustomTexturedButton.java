package net.david.mod.client.components;

import net.david.mod.ProtectorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class CustomTexturedButton extends Button {
    private final ResourceLocation texture;

    public CustomTexturedButton(int x, int y, int width, int height, Component message, OnPress onPress, ResourceLocation texture) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.texture = texture;
    }

    public CustomTexturedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, new ResourceLocation(ProtectorMod.MOD_ID, "textures/gui/boton_custom.png"));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // NUEVO ORDEN:
        // vOffset 0: Inactivo (Gris)
        // vOffset 20: Activo (Normal)
        // vOffset 40: Hover (Resaltado)

        int vOffset;
        if (!this.active) {
            vOffset = 0;
        } else if (this.isHoveredOrFocused()) {
            vOffset = 40; // Hover al final
        } else {
            vOffset = 20; // Activo en el medio
        }

        graphics.blit(this.texture, this.getX(), this.getY(), 0, vOffset, this.width, this.height, this.width, 60);

        // Renderizado de texto (se mantiene igual)
        if (!this.getMessage().getString().isEmpty()) {
            int color = this.active ? (this.isHoveredOrFocused() ? 0xFFFF00 : 0xFFFFFF) : 0xA0A0A0;
            graphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                    this.getX() + this.width / 2,
                    this.getY() + (this.height - 8) / 2,
                    color);
        }
    }
}