package net.david.mod.client.components;

import net.david.mod.ProtectorMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class CloseButtonCustom extends Button {
    private static final ResourceLocation TEXTURE_X = new ResourceLocation(ProtectorMod.MOD_ID, "textures/gui/boton_x.png");

    // Definimos el tamaño de la celda en el archivo .png (usualmente 16x16 para iconos)
    private static final int TEXTURE_SIZE = 16;

    public CloseButtonCustom(int x, int y, int width, int height, Component title, OnPress onPress) {
        super(x, y, width, height, title, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        int vOffset = !this.active ? 0 : (this.isHoveredOrFocused() ? 32 : 16);

        graphics.blit(TEXTURE_X,
                this.getX(), this.getY(),
                this.width, this.height,
                0, vOffset,
                16, 16,
                16, 48
        );
    }
}
