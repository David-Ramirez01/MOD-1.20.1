package net.david.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.event.ProtectionEvents;
import net.david.mod.util.ClanSavedData;
import net.david.mod.util.InviteManager;
import net.david.mod.util.ProtectionDataManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClanCommands {
    public static final Set<UUID> VISUALIZER_ENABLED = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> PRESENTATION_DISABLED = ConcurrentHashMap.newKeySet();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("protector")
                .then(Commands.literal("info")
                        .executes(ClanCommands::showProtectionInfo))

                .then(Commands.literal("help").executes(c -> showHelp(c.getSource(), "protector")))

                // Comandos de Usuario
                .then(Commands.literal("presentation")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .executes(ClanCommands::togglePresentation)))
                .then(Commands.literal("accept").executes(ClanCommands::acceptInvite))
                .then(Commands.literal("deny").executes(ClanCommands::denyInvite))

                // Comandos de Administración (Nivel 2)
                .then(Commands.literal("visualizer")
                        .requires(s -> s.hasPermission(2))
                        .executes(ClanCommands::toggleVisualizer))

                .then(Commands.literal("admin")
                        .requires(s -> s.hasPermission(2))
                        // /protector admin radius <cantidad>
                        .then(Commands.literal("radius")
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 1000))
                                        .executes(ClanCommands::setAdminRadius)))
                        // /protector admin trust <jugador>
                        .then(Commands.literal("trust")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> executeAdminTrust(c, true))))
                        .then(Commands.literal("untrust")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> executeAdminTrust(c, false)))))

                .then(Commands.literal("limit")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 100))
                                .executes(ClanCommands::setGlobalLimit)))

                .then(Commands.literal("list")
                        .requires(s -> s.hasPermission(2))
                        .executes(ClanCommands::listCores))
        );

        dispatcher.register(Commands.literal("clan")
                .then(Commands.literal("help").executes(c -> showHelp(c.getSource(), "clan")))
                .then(Commands.literal("info")
                        .executes(c -> showClanInfo(c, null))
                        .then(Commands.argument("nombreClan", StringArgumentType.string())
                                .requires(s -> s.hasPermission(2))
                                .executes(c -> showClanInfo(c, StringArgumentType.getString(c, "nombreClan")))))
                .then(Commands.literal("delete").executes(ClanCommands::deleteClan))
        );
    }

    private static int showHelp(CommandSourceStack source, String type) {
        if (type.equals("protector")) {
            source.sendSuccess(() -> Component.literal("§b§l╔════════════════════════════════╗"), false);
            source.sendSuccess(() -> Component.literal("§b§l║       AYUDA DE PROTECCIÓN      ║"), false);
            source.sendSuccess(() -> Component.literal("§b§l╠════════════════════════════════╝"), false);
            source.sendSuccess(() -> Component.literal("§e§l> COMANDOS DE USUARIO:"), false);
            source.sendSuccess(() -> Component.literal("§f /protector presentation <on/off> §7- Activa/Desactiva avisos."), false);
            source.sendSuccess(() -> Component.literal("§f /protector accept/deny §7- Gestiona invitaciones."), false);

            if (source.hasPermission(2)) {
                source.sendSuccess(() -> Component.literal("§d§l> COMANDOS DE STAFF:"), false);
                source.sendSuccess(() -> Component.literal("§f /protector visualizer §7- Ver bordes de protección."), false);
                source.sendSuccess(() -> Component.literal("§f /protector admin radius <n> §7- Cambia radio del Admin Core."), false);
                source.sendSuccess(() -> Component.literal("§f /protector admin trust <jugador> §7- Acceso total a zona."), false);
                source.sendSuccess(() -> Component.literal("§f /protector limit <n> §7- Máximo de cores por jugador."), false);
                source.sendSuccess(() -> Component.literal("§f /protector list §7- Lista todos los cores activos."), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("§6§l╔════════════════════════════════╗"), false);
            source.sendSuccess(() -> Component.literal("§6§l║          SISTEMA DE CLAN       ║"), false);
            source.sendSuccess(() -> Component.literal("§6§l╠════════════════════════════════╝"), false);
            source.sendSuccess(() -> Component.literal("§f/clan info §7- Ver miembros y líder de tu clan."), false);
            source.sendSuccess(() -> Component.literal("§f/clan delete §7- Disuelve tu clan actual."), false);
            source.sendSuccess(() -> Component.literal("§7(Para crear, usa el botón del panel del Núcleo)"), false);
        }
        source.sendSuccess(() -> Component.literal("§b§l╚════════════════════════════════╝"), false);
        return 1;
    }

    private static int setAdminRadius(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer admin = context.getSource().getPlayerOrException();
        int newRadius = IntegerArgumentType.getInteger(context, "cantidad");

        // Buscar el core que el admin está mirando o donde está parado
        HitResult hit = admin.pick(8.0D, 0.0F, false);
        ProtectionCoreBlockEntity core = null;

        if (hit.getType() == HitResult.Type.BLOCK) {
            core = ProtectionEvents.findCoreAt(admin.serverLevel(), ((BlockHitResult) hit).getBlockPos());
        }
        if (core == null) {
            core = ProtectionEvents.findCoreAt(admin.serverLevel(), admin.blockPosition());
        }

        if (core != null && core.isAdmin()) {
            core.setAdminRadius(newRadius);
            context.getSource().sendSuccess(() -> Component.literal("§a✔ Radio del Admin Core actualizado a: §f" + newRadius), true);
            return 1;
        }

        context.getSource().sendFailure(Component.literal("§c[!] Debes estar dentro o mirando un Admin Protector."));
        return 0;
    }

    // --- MÉTODOS ANTERIORES MANTENIDOS (Breve resumen) ---

    private static int togglePresentation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String state = StringArgumentType.getString(context, "state");
        boolean isOn = state.equalsIgnoreCase("on");
        if (isOn) PRESENTATION_DISABLED.remove(player.getUUID());
        else PRESENTATION_DISABLED.add(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal(isOn ? "§aAvisos activados." : "§cAvisos desactivados."), false);
        return 1;
    }

    private static int toggleVisualizer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID uuid = context.getSource().getPlayerOrException().getUUID();
        if (VISUALIZER_ENABLED.contains(uuid)) VISUALIZER_ENABLED.remove(uuid);
        else VISUALIZER_ENABLED.add(uuid);
        boolean active = VISUALIZER_ENABLED.contains(uuid);
        context.getSource().sendSuccess(() -> Component.literal(active ? "§aVisualizador activado." : "§cVisualizador desactivado."), false);
        return 1;
    }

    private static int executeAdminTrust(CommandContext<CommandSourceStack> context, boolean trust) throws CommandSyntaxException {
        ServerPlayer admin = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");

        HitResult hit = admin.pick(6.0D, 0.0F, false);
        ProtectionCoreBlockEntity core = (hit.getType() == HitResult.Type.BLOCK) ?
                ProtectionEvents.findCoreAt(admin.serverLevel(), ((BlockHitResult) hit).getBlockPos()) :
                ProtectionEvents.findCoreAt(admin.serverLevel(), admin.blockPosition());

        if (core != null && core.isAdmin()) {
            if (trust) core.updatePermission(target.getUUID(), target.getName().getString(), "build", true);
            else core.removePlayerPermissions(target.getName().getString());
            context.getSource().sendSuccess(() -> Component.literal("§ePermisos actualizados para: §f" + target.getName().getString()), true);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("§c[!] Acción solo válida en Admin Protectors."));
        return 0;
    }

    private static int setGlobalLimit(CommandContext<CommandSourceStack> context) {
        int limit = IntegerArgumentType.getInteger(context, "cantidad");
        ProtectionDataManager.get(context.getSource().getLevel()).setGlobalLimit(limit);
        context.getSource().sendSuccess(() -> Component.literal("§aLímite global: §f" + limit), true);
        return 1;
    }

    private static int listCores(CommandContext<CommandSourceStack> context) {
        var cores = ProtectionDataManager.get(context.getSource().getLevel()).getAllCores();

        if (cores.isEmpty()) {
            context.getSource().sendFailure(Component.literal("§cNo hay núcleos activos en este mundo."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("§6§l--- LISTA DE NÚCLEOS (Click para TP) ---"), false);

        cores.forEach((pos, entry) -> {
            String type = entry.isAdmin() ? "§d[ADMIN]" : "§7[CORE]";
            String posString = pos.getX() + " " + pos.getY() + " " + pos.getZ();

            // Creamos el componente de texto para cada línea
            Component message = Component.literal(type + " §f" + pos.toShortString() + " §e(R: " + entry.radius() + ")")
                    .withStyle(style -> style
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                    net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                    "/tp @s " + posString)) // Comando que se ejecuta al hacer click
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                    net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("§aClick para teletransportarte a este núcleo")))
                            .withColor(net.minecraft.ChatFormatting.WHITE)
                    );

            context.getSource().sendSuccess(() -> message, false);
        });

        return cores.size();
    }

    private static int acceptInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        InviteManager.PendingInvite invite = InviteManager.getInvite(player.getUUID());

        if (invite == null) {
            context.getSource().sendFailure(Component.literal("§c[!] No tienes invitaciones pendientes."));
            return 0;
        }

        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = invite.corePos();

        // Buscamos el bloque de protección
        if (level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
            // Añadimos al jugador con permisos básicos (puedes ajustar los booleanos por defecto)
            core.updatePermission(player.getUUID(), player.getName().getString(), "build", false);
            core.updatePermission(player.getUUID(), player.getName().getString(), "interact", true);
            core.updatePermission(player.getUUID(), player.getName().getString(), "chests", false);

            core.markDirtyAndUpdate();

            // Notificamos al dueño si está online
            ServerPlayer owner = context.getSource().getServer().getPlayerList().getPlayer(invite.requesterUUID());
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("§a[!] " + player.getName().getString() + " ha aceptado tu invitación."));
            }

            player.sendSystemMessage(Component.literal("§a✔ Ahora eres miembro de esta protección."));
            InviteManager.removeInvite(player.getUUID());
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§c[!] El núcleo de protección ya no existe."));
            InviteManager.removeInvite(player.getUUID());
            return 0;
        }
    }

    private static int denyInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        InviteManager.PendingInvite invite = InviteManager.getInvite(player.getUUID());

        if (invite != null) {
            InviteManager.removeInvite(player.getUUID());

            // Notificar al que envió la invitación
            ServerPlayer requester = context.getSource().getServer().getPlayerList().getPlayer(invite.requesterUUID());
            if (requester != null) {
                requester.sendSystemMessage(Component.literal("§c[!] " + player.getName().getString() + " rechazó tu invitación."));
            }

            context.getSource().sendSuccess(() -> Component.literal("§eInvitación rechazada."), false);
            return 1;
        }

        context.getSource().sendFailure(Component.literal("§c[!] No tienes invitaciones para rechazar."));
        return 0;
    }

    private static int showClanInfo(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ClanSavedData data = ClanSavedData.get(context.getSource().getServer());
        ClanSavedData.ClanInstance clan = (name == null) ?
                data.getClanByMember(context.getSource().getPlayerOrException().getUUID()) : data.getClanByName(name);
        if (clan != null) {
            context.getSource().sendSuccess(() -> Component.literal("§bClan: §f" + clan.name + " §7| §eLíder: §f" + clan.leaderName), false);
            return 1;
        }
        return 0;
    }

    private static int deleteClan(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ClanSavedData data = ClanSavedData.get(context.getSource().getServer());
        data.deleteClan(context.getSource().getPlayerOrException().getUUID());
        return 1;
    }

    private static int showProtectionInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();

        // 1. Raycast para ver qué bloque mira el jugador
        HitResult hit = player.pick(10.0D, 0.0F, false);
        BlockPos targetPos = (hit.getType() == HitResult.Type.BLOCK) ? ((BlockHitResult) hit).getBlockPos() : player.blockPosition();

        ProtectionCoreBlockEntity core = ProtectionEvents.findCoreAt(level, targetPos);

        if (core == null) {
            context.getSource().sendFailure(Component.literal("§c[!] No hay ninguna protección en esta ubicación."));
            return 0;
        }

        // 2. Mostrar la información
        context.getSource().sendSuccess(() -> Component.literal("§b§l--- INFORMACIÓN DE PROTECCIÓN ---"), false);
        context.getSource().sendSuccess(() -> Component.literal("§eDueño: §f" + (core.isAdmin() ? "§dAdministración" : core.getOwnerName())), false);
        context.getSource().sendSuccess(() -> Component.literal("§eNivel: §a" + core.getCoreLevel() + " §7| §eRadio: §a" + core.getRadius()), false);
        context.getSource().sendSuccess(() -> Component.literal("§ePosición: §7" + core.getBlockPos().toShortString()), false);

        // Mostrar Flags activas
        StringBuilder flagsInfo = new StringBuilder("§eFlags (ON): §f");
        boolean first = true;
        for (String flag : ProtectionCoreBlockEntity.BASIC_FLAGS) {
            if (core.getFlag(flag)) {
                if (!first) flagsInfo.append(", ");
                flagsInfo.append(flag);
                first = false;
            }
        }
        context.getSource().sendSuccess(() -> Component.literal(flagsInfo.toString()), false);

        return 1;
    }
}