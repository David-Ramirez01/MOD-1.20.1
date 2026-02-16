package net.david.mod.client.screen;

import net.david.mod.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CreateClanScreen extends Screen {
    private final Screen lastScreen;
    private final BlockEntity core;
    private EditBox clanNameInput;

    public CreateClanScreen(Screen lastScreen, BlockEntity core) {
        super(Component.literal("Crear Nuevo Clan"));
        this.lastScreen = lastScreen;
        this.core = core;
    }

    @Override
    protected void init() {
        int x = this.width / 2;
        int y = this.height / 2;

        this.clanNameInput = new EditBox(this.font, x - 100, y - 20, 200, 20, Component.literal("Nombre del Clan"));
        this.clanNameInput.setMaxLength(16);
        this.addRenderableWidget(this.clanNameInput);

        // Botón Confirmar
        this.addRenderableWidget(Button.builder(Component.literal("✅ Confirmar"), b -> {
            String name = this.clanNameInput.getValue().trim();
            if (!name.isEmpty()) {
                // --- NETWORKING FABRIC 1.20.1 ---
                FriendlyByteBuf buf = PacketByteBufs.create();
                buf.writeBlockPos(core.getBlockPos());
                buf.writeUtf(name);

                // Asegúrate de definir CREATE_CLAN_ID en tu ModNetworking
                ClientPlayNetworking.send(ModNetworking.CREATE_CLAN_ID, buf);
                // -------------------------------

                if (this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
            }
        }).bounds(x - 105, y + 25, 100, 20).build());

        // Botón Volver
        this.addRenderableWidget(Button.builder(Component.literal("❌ Cancelar"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(lastScreen);
            }
        }).bounds(x + 5, y + 25, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // En Fabric 1.20.1 solo requiere el objeto GuiGraphics
        this.renderBackground(graphics);

        int centerX = this.width / 2;
        int titleY = this.height / 2 - 50;
        graphics.drawCenteredString(this.font, Component.literal("FUNDAR CLAN").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), centerX, titleY, 0xFFFFFF);

        Component warning = Component.literal("⚠ ¡Este nombre no podrá cambiarse después!");
        int warningX = centerX - (this.font.width(warning) / 2);
        int warningY = this.height / 2 + 5;

        graphics.drawString(this.font, warning, warningX, warningY, 0xFF5555, false);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
