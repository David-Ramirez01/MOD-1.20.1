package net.david.mod.client.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class CheckButtonCustom extends Button {
    private static final ResourceLocation TEXTURE_CHECK = new ResourceLocation(net.david.mod.ProtectorMod.MOD_ID, "textures/gui/boton_check.png");

    // Dimensiones de una celda individual en tu .png (ajustado a 32x16)
    private static final int TEXTURE_WIDTH = 32;
    private static final int TEXTURE_HEIGHT = 16;

    public CheckButtonCustom(int x, int y, int width, int height, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        // vOffset: 0 (Inactivo), 16 (Activo), 32 (Hover)
        int vOffset = !this.active ? 0 : (this.isHoveredOrFocused() ? 32 : 16);

        graphics.blit(TEXTURE_CHECK,
                this.getX(), this.getY(),
                this.width, this.height,
                0, vOffset,
                32, 16,
                32, 48
        );
    }
}