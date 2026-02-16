package net.david.mod.client.screen;

import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public class FlagsScreen extends Screen {
    private final ProtectionCoreBlockEntity core;
    private final Screen lastScreen;

    public FlagsScreen(Screen lastScreen, ProtectionCoreBlockEntity core) {
        super(Component.literal("Configuración de Zona"));
        this.lastScreen = lastScreen;
        this.core = core;
    }

    @Override
    protected void init() {
        int startX = this.width / 2 - 145;
        int startY = 45;
        int columnWidth = 150;

        List<String> allFlags = new ArrayList<>();
        allFlags.addAll(ProtectionCoreBlockEntity.BASIC_FLAGS);
        allFlags.addAll(ProtectionCoreBlockEntity.ADMIN_FLAGS);

        for (int i = 0; i < allFlags.size(); i++) {
            String flagId = allFlags.get(i);

            int column = i % 2;
            int row = i / 2;

            int posX = startX + (column * columnWidth);
            int posY = startY + (row * 22);

            createFlagButton(flagId, posX, posY);
        }

        // Botón Volver
        this.addRenderableWidget(Button.builder(Component.literal("§lVolver"),
                        b -> {
                            if (this.minecraft != null) this.minecraft.setScreen(lastScreen);
                        })
                .bounds(this.width / 2 - 50, this.height - 35, 100, 20).build());
    }

    private void createFlagButton(String flagId, int x, int y) {
        boolean active = core.getFlag(flagId);

        boolean isAdminFlag = ProtectionCoreBlockEntity.ADMIN_FLAGS.contains(flagId);
        String prefix = isAdminFlag ? "§6⚙ " : "§e• ";

        this.addRenderableWidget(Button.builder(
                Component.literal(prefix + capitalize(flagId) + ": ")
                        .append(active ? Component.literal("§aON") : Component.literal("§cOFF")),
                b -> {
                    // --- NETWORKING FABRIC 1.20.1 ---
                    FriendlyByteBuf buf = PacketByteBufs.create();
                    buf.writeBlockPos(core.getBlockPos());
                    buf.writeUtf(flagId);
                    ClientPlayNetworking.send(ModNetworking.UPDATE_FLAG_ID, buf);
                    // -------------------------------

                    core.setFlag(flagId, !active);
                    this.rebuildWidgets();
                }).bounds(x, y, 140, 20).build());
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("-", " ");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics); // Solo requiere graphics en 1.20.1

        graphics.drawCenteredString(this.font, "§b§lCONFIGURACIÓN GLOBAL DE FLAGS", this.width / 2, 25, 0xFFFFFF);

        // Definición local de warnY para evitar errores de compilación
        int warnY = this.height - 75;
        graphics.drawCenteredString(this.font, "§4§l⚠ ¡CUIDADO!", this.width / 2, warnY, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "§cTen cuidado con qué flags cambias, ya que esto", this.width / 2, warnY + 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "§cpuede afectar negativamente a tus granjas.", this.width / 2, warnY + 22, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}