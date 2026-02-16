package net.david.mod.client.screen;

import net.david.mod.ProtectorMod;
import net.david.mod.client.components.CheckButtonCustom;
import net.david.mod.client.components.CloseButtonCustom;
import net.david.mod.client.components.CustomTexturedButton;
import net.david.mod.menu.ProtectionCoreMenu;
import net.david.mod.network.ModNetworking;
import net.david.mod.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.UUID;

public class ProtectionCoreScreen extends AbstractContainerScreen<ProtectionCoreMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(ProtectorMod.MOD_ID, "textures/gui/protection_core.png");
    private static final ResourceLocation FONDO_INVITADOS = new ResourceLocation(ProtectorMod.MOD_ID, "textures/gui/fondo_invitados.png");
    private static final ResourceLocation SLOT_TEXTURE = new ResourceLocation(ProtectorMod.MOD_ID, "textures/gui/slot.png");
    private static final ResourceLocation TEXTURA_INPUT = new ResourceLocation(ProtectorMod.MOD_ID, "textures/gui/input_nombre.png");

    private EditBox nameInput;
    private Button upgradeButton, buildBtn, interactBtn, chestsBtn, clanBtn;
    private Button inviteConfirmBtn;
    private boolean buildToggle = false;
    private boolean interactToggle = false;
    private boolean chestsToggle = false;
    private String lastCheckedPlayer = "";
    private int suggestionIndex = 0;

    public ProtectionCoreScreen(ProtectionCoreMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        // 1. INPUT DE NOMBRE
        this.nameInput = new EditBox(this.font, x + 14, y + 38, 87, 9, Component.empty());
        this.nameInput.setBordered(false);
        this.nameInput.setMaxLength(16);
        this.nameInput.setHint(Component.literal("Nombre").withStyle(ChatFormatting.GRAY));
        this.addRenderableWidget(this.nameInput);
        this.inviteConfirmBtn = this.addRenderableWidget(new CheckButtonCustom(x + 105, y + 33, 32, 16, b -> handlePlayerAdd()));

        // 2. BOTONES DE PERMISOS
        this.buildBtn = this.addRenderableWidget(new CustomTexturedButton(x + 10, y + 55, 50, 20, Component.literal("B: OFF"), b -> {
            buildToggle = !buildToggle;
            sendPermission(nameInput.getValue(), "build", buildToggle);
        }));

        this.interactBtn = this.addRenderableWidget(new CustomTexturedButton(x + 63, y + 55, 50, 20, Component.literal("I: OFF"), b -> {
            interactToggle = !interactToggle;
            sendPermission(nameInput.getValue(), "interact", interactToggle);
        }));

        this.chestsBtn = this.addRenderableWidget(new CustomTexturedButton(x + 116, y + 55, 50, 20, Component.literal("C: OFF"), b -> {
            chestsToggle = !chestsToggle;
            sendPermission(nameInput.getValue(), "chests", chestsToggle);
        }));

        // 3. CLAN Y AJUSTES
        this.clanBtn = this.addRenderableWidget(new CustomTexturedButton(x + 10, y + 78, 50, 20, Component.literal("Clan"), b -> {
            if (this.menu.getBlockEntity().getTrustedNames().size() >= 3) {
                if (this.minecraft != null) this.minecraft.setScreen(new CreateClanScreen(this, this.menu.getBlockEntity()));
            }
        }));

        this.addRenderableWidget(new CustomTexturedButton(x + 63, y + 78, 50, 20, Component.literal("Ajustes"), b -> {
            if (this.minecraft != null) this.minecraft.setScreen(new FlagsScreen(this, this.menu.getBlockEntity()));
        }));

        // 4. UPGRADE Y ÁREA
        this.upgradeButton = this.addRenderableWidget(new CustomTexturedButton(x + 64, y + 105, 50, 20,
                Component.translatable("Mejorar"), b -> sendUpgradePacket()));

        this.addRenderableWidget(new CustomTexturedButton(x + 116, y + 105, 50, 20,
                Component.literal("Área"), b -> sendAreaPacket()));

        this.addRenderableWidget(new CloseButtonCustom(x + imageWidth - 18, y + 4, 14, 14, Component.empty(), b -> this.onClose()));
    }

    private void renderLateralPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        var core = this.menu.getBlockEntity();
        if (core == null) return;

        int listX = x - 105;
        int listY = y + 10;

        graphics.blit(FONDO_INVITADOS, listX, listY, 0, 0, 100, 205, 100, 205);
        graphics.drawString(this.font, "§6§lJugadores", listX + 11, listY + 12, 0xFFFFFF, false);

        String ownerName = core.getOwnerName();
        graphics.drawString(this.font, "§e★ §f" + ownerName, listX + 11, listY + 24, 0xFFAA00, false);

        List<String> guests = core.getTrustedNames();
        int offset = 1;
        for (String name : guests) {
            if (name.equalsIgnoreCase(ownerName)) continue;
            int entryY = listY + 24 + (offset * 13);
            int removeBtnX = listX + 76;

            boolean hoverRemove = mouseX >= removeBtnX && mouseX <= removeBtnX + 12 && mouseY >= entryY && mouseY <= entryY + 10;
            graphics.drawString(this.font, name, listX + 11, entryY, 0xAAAAAA, false);
            graphics.drawString(this.font, hoverRemove ? "§f[X]" : "§c[X]", removeBtnX, entryY, 0xFFFFFF, false);
            offset++;
        }

        int reqY = listY + 145;
        graphics.drawString(this.font, "§e§lRequisitos:", listX + 15, reqY, 0xFFFFFF, false);

        int level = core.getCoreLevel();
        if (level >= 5) {
            graphics.drawString(this.font, "§a✔ Máximo Nivel", listX + 15, reqY + 15, 0xFFFFFF, false);
        } else {
            String material = switch (level) {
                case 1 -> "64x Hierro";
                case 2 -> "32x Oro";
                case 3 -> "32x Diamante";
                case 4 -> "32x Netherite";
                default -> "???";
            };
            boolean hasUpgrade = core.getInventory().get(0).is(ModItems.PROTECTION_UPGRADE);
            boolean hasMats = core.canUpgrade();

            renderRequirement(graphics, listX + 15, reqY + 15, "1x Mejora", hasUpgrade);
            renderRequirement(graphics, listX + 15, reqY + 28, material, hasMats);
        }
    }

    private void renderRequirement(GuiGraphics graphics, int x, int y, String text, boolean met) {
        String prefix = met ? "§a[✔] " : "§c[X] ";
        graphics.drawString(this.font, prefix + "§7" + text, x, y, 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);

        var core = this.menu.getBlockEntity();
        if (core == null) return;

        int x = this.leftPos;
        int y = this.topPos;

        // --- TITULOS ---
        graphics.drawCenteredString(this.font, "§b§l§oProtection Core", x + this.imageWidth / 2, y + 10, 0xFFFFFF);
        graphics.drawString(this.font, "§6§lInvitar Jugadores", x + 10, y + 25, 0xFFFFFF, false);

        // --- LÓGICA DE ESTADO DE BOTONES ---
        String input = nameInput.getValue().trim();
        boolean hasName = !input.isEmpty();

        // Verificamos si el nombre escrito ya pertenece a la lista de invitados/miembros
        boolean isTrusted = core.getTrustedNames().stream()
                .anyMatch(n -> n.equalsIgnoreCase(input));

        // 1. Botón de INVITAR (CheckButton): Activo solo si hay texto y NO es miembro aún
        if (this.inviteConfirmBtn != null) {
            this.inviteConfirmBtn.active = hasName && !isTrusted;
        }

        // 2. Botones de PERMISOS (B, I, C): Activos solo si el jugador YA es miembro
        this.buildBtn.active = hasName && isTrusted;
        this.interactBtn.active = hasName && isTrusted;
        this.chestsBtn.active = hasName && isTrusted;

        // 3. Botón de CLAN: Activo si hay al menos 3 miembros (incluyendo dueño)
        if (this.clanBtn != null) {
            this.clanBtn.active = core.getTrustedNames().size() >= 3;
        }

        if (this.upgradeButton != null) {
            boolean canUpgrade = core.canUpgrade();
            boolean isMaxLevel = core.getCoreLevel() >= 5;

            this.upgradeButton.active = canUpgrade && !isMaxLevel;

            // Opcional: Cambiar el texto si ya está al máximo
            if (isMaxLevel) {
                this.upgradeButton.setMessage(Component.literal("MAX"));
            } else {
                this.upgradeButton.setMessage(Component.translatable("Mejorar"));
            }
        }

        // --- SINCRONIZACIÓN DE TOGGLES ---
        // Si el nombre en el input cambia, buscamos sus permisos para actualizar los botones visualmente
        if (hasName && !input.equalsIgnoreCase(lastCheckedPlayer)) {
            var perms = core.getPermissionsFor(input);
            if (perms != null) {
                this.buildToggle = perms.canBuild();
                this.interactToggle = perms.canInteract();
                this.chestsToggle = perms.canOpenChests();
            } else {
                // Si no hay permisos (jugador nuevo), reseteamos los toggles a OFF
                this.buildToggle = this.interactToggle = this.chestsToggle = false;
            }
            lastCheckedPlayer = input;
        }

        // --- ACTUALIZACIÓN DE TEXTOS EN BOTONES ---
        this.buildBtn.setMessage(Component.literal("B: " + (buildToggle ? "§aON" : "§cOFF")));
        this.interactBtn.setMessage(Component.literal("I: " + (interactToggle ? "§aON" : "§cOFF")));
        this.chestsBtn.setMessage(Component.literal("C: " + (chestsToggle ? "§aON" : "§cOFF")));

        // --- PANELES ADICIONALES ---
        renderLateralPanel(graphics, mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
        graphics.blit(SLOT_TEXTURE, x + 14, y + 104, 0, 0, 18, 18, 18, 18);
        graphics.blit(SLOT_TEXTURE, x + 34, y + 104, 0, 0, 18, 18, 18, 18);
        graphics.blit(TEXTURA_INPUT, x + 10, y + 35, 0, 0, 91, 13, 128, 32);
    }

    private void handlePlayerAdd() {
        String name = this.nameInput.getValue().trim();
        if (!name.isEmpty()) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(this.menu.getBlockEntity().getBlockPos());
            buf.writeUtf(name);
            ClientPlayNetworking.send(ModNetworking.INVITE_PLAYER_ID, buf);
            this.nameInput.setValue("");
        }
    }

    private void sendUpgradePacket() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getBlockEntity().getBlockPos());
        ClientPlayNetworking.send(ModNetworking.UPGRADE_ID, buf);
        playClickSound();
    }

    private void sendAreaPacket() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getBlockEntity().getBlockPos());
        ClientPlayNetworking.send(ModNetworking.GUI_SHOW_AREA_ID, buf);
    }

    private void sendPermission(String player, String type, boolean value) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getBlockEntity().getBlockPos());
        buf.writeUtf(player);
        buf.writeUtf(type);
        buf.writeBoolean(value);
        ClientPlayNetworking.send(ModNetworking.PERMISSION_ID, buf);
    }

    private void playClickSound() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.2F);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 1. Lógica de Autocompletado con TAB
        if (keyCode == 258 && this.nameInput.isFocused()) { // 258 = TAB
            String currentText = this.nameInput.getValue();
            if (!currentText.isEmpty() && this.minecraft != null) {
                List<String> suggestions = this.minecraft.getConnection().getOnlinePlayers().stream()
                        .map(p -> p.getProfile().getName())
                        .filter(n -> n.toLowerCase().startsWith(currentText.toLowerCase()))
                        .toList();

                if (!suggestions.isEmpty()) {
                    if (suggestionIndex >= suggestions.size()) suggestionIndex = 0;
                    this.nameInput.setValue(suggestions.get(suggestionIndex));
                    suggestionIndex++;
                    return true;
                }
            }
        }

        // Resetear sugerencias si se presiona cualquier otra tecla
        if (keyCode != 258) suggestionIndex = 0;

        // 2. Lógica para NO cerrar con la 'E'
        if (this.nameInput.isFocused()) {
            if (keyCode == 256) { // ESCAPE
                this.nameInput.setFocused(false);
                return true;
            }
            // Esto permite escribir letras (incluida la E) sin cerrar la GUI
            return this.nameInput.keyPressed(keyCode, scanCode, modifiers);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos;
        int y = this.topPos;
        int listX = x - 105;
        int listY = y + 10;
        var core = this.menu.getBlockEntity();

        if (core != null && this.minecraft != null && this.minecraft.player != null) {
            // Obtenemos la lista de UUIDs (Asegúrate que en el BE se llame getTrustedPlayers)
            List<java.util.UUID> guests = core.getTrustedPlayers();
            int offset = 1;

            for (java.util.UUID guestUuid : guests) {
                String name = core.getNameFromUUID(guestUuid);
                if (name.equalsIgnoreCase(core.getOwnerName())) continue;

                int entryY = listY + 24 + (offset * 13);

                // 1. CLICK EN EL NOMBRE
                if (mouseX >= listX + 11 && mouseX <= listX + 70 && mouseY >= entryY && mouseY <= entryY + 10) {
                    this.nameInput.setValue(name);
                    return true;
                }

                // 2. CLICK EN LA [X]
                int removeBtnX = listX + 76;
                if (mouseX >= removeBtnX && mouseX <= removeBtnX + 15 && mouseY >= entryY && mouseY <= entryY + 10) {
                    // LLAMADA CORREGIDA: Usamos el método local que definiste abajo
                    this.sendRemovePlayerPacket(guestUuid);

                    // Sonido corregido
                    this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                    return true;
                }
                offset++;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendRemovePlayerPacket(java.util.UUID uuid) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getBlockEntity().getBlockPos());
        buf.writeUUID(uuid);
        ClientPlayNetworking.send(ModNetworking.REMOVE_PLAYER_ID, buf);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, "§b§o" + this.playerInventoryTitle.getString(), this.inventoryLabelX, this.inventoryLabelY, 0xFFFFFF, true);
    }
}
