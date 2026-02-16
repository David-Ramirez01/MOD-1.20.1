package net.david.mod.client.screen;

import net.david.mod.ProtectorMod;
import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.menu.ProtectionCoreMenu;
import net.david.mod.network.ModNetworking; // Referencia centralizada de paquetes
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AdminCoreScreen extends AbstractContainerScreen<ProtectionCoreMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ProtectorMod.MOD_ID, "textures/gui/admin_core.png");

    private EditBox radiusInput;
    private final ProtectionCoreBlockEntity core;

    public AdminCoreScreen(ProtectionCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.core = menu.getBlockEntity();
        this.imageWidth = 176;
        this.imageHeight = 222;

        this.titleLabelY = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        // 1. INPUT DE RADIO
        this.radiusInput = new EditBox(this.font, x + 80, y + 25, 50, 18, Component.literal("Radio"));
        this.radiusInput.setValue(String.valueOf(core.getRadius()));
        this.radiusInput.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(this.radiusInput);

        // 2. BOTÓN CONFIGURAR FLAGS
        this.addRenderableWidget(Button.builder(Component.literal("🚩 CONFIGURAR FLAGS"), b -> {
            saveLocalRadius();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new FlagsScreen(this, core));
            }
        }).bounds(x + 20, y + 55, 136, 20).build());

        // 3. BOTÓN APLICAR CAMBIOS
        this.addRenderableWidget(Button.builder(Component.literal("✅ Aplicar Cambios"), b -> {
            applyChanges();
        }).bounds(x + 20, y + 80, 136, 20).build());
    }

    private void saveLocalRadius() {
        try {
            int r = Integer.parseInt(radiusInput.getValue());
            core.setAdminRadius(r);
        } catch (NumberFormatException ignored) {}
    }

    private void applyChanges() {
        try {
            int newRadius = Integer.parseInt(radiusInput.getValue());

            // --- NETWORKING FABRIC 1.20.1 ---
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(core.getBlockPos());
            buf.writeInt(newRadius);
            buf.writeBoolean(core.getFlag("pvp"));
            buf.writeBoolean(core.getFlag("explosions"));
            buf.writeBoolean(core.getFlag("build"));

            // Se usa la constante centralizada del ModNetworking
            ClientPlayNetworking.send(ModNetworking.UPDATE_ADMIN_ID, buf);
            // -------------------------

            this.onClose();
        } catch (NumberFormatException e) {
            radiusInput.setValue(String.valueOf(core.getRadius()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, "§4§lADMIN PROTECTOR", 35, 8, 0xFFFFFF, false);
        graphics.drawString(this.font, "Radio:", 35, 30, 0x404040, false);
        graphics.drawString(this.font, "§8Actual: " + core.getRadius(), 80, 45, 0xFFFFFF, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        return this.radiusInput.keyPressed(keyCode, scanCode, modifiers) ||
                this.radiusInput.canConsumeInput() ||
                super.keyPressed(keyCode, scanCode, modifiers);
    }
}