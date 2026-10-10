package com.sumutiu.simpleprotect.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sumutiu.simpleprotect.storage.NameCache;
import com.sumutiu.simpleprotect.storage.Protection;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import com.sumutiu.simpleprotect.util.EventHandlers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.sumutiu.simpleprotect.SimpleProtect.SimpleProtectInitialized;
import static com.sumutiu.simpleprotect.util.MessagesHelper.*;

public class ProtectionCommands {

    // Called by the loader when the server registers its commands
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("sprotection")

                        // ---------------- ADD ----------------
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(ProtectionCommands::suggestKnownPlayers)
                                        .executes(ProtectionCommands::add)
                                )
                        )

                        // ---------------- REMOVE ----------------
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(ProtectionCommands::suggestAllowedPlayers)
                                        .executes(ProtectionCommands::remove)
                                )
                        )

                        // ---------------- INFO ----------------
                        .then(Commands.literal("info")
                                .executes(ProtectionCommands::info)
                        )

                        // ---------------- LIST ----------------
                        .then(Commands.literal("list")
                                .executes(ProtectionCommands::listOwn)
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .requires(ProtectionCommands::isAdmin)
                                        .suggests(ProtectionCommands::suggestKnownPlayers)
                                        .executes(ProtectionCommands::listOther)
                                )
                        )

                        // ---------------- ADMIN ----------------
                        .then(Commands.literal("bypass")
                                .requires(ProtectionCommands::isAdmin)
                                .executes(ProtectionCommands::bypass)
                        )
                        .then(Commands.literal("delete")
                                .requires(ProtectionCommands::isAdmin)
                                .executes(ProtectionCommands::delete)
                        )
        );
    }

    // Works for offline players who joined before: their name is remembered
    private static int add(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer executor = readyPlayer(ctx);
        if (executor == null) return 0;

        Optional<Protection> protectionOpt = findOwnedProtectionAt(executor);
        if (protectionOpt.isEmpty()) {
            PrivateMessage(executor, NOT_IN_PROTECTION);
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        String targetName = StringArgumentType.getString(ctx, "player");

        UUID targetId;
        String name;
        ServerPlayer online = server.getPlayerList().getPlayerByName(targetName);
        if (online != null) {
            targetId = online.getUUID();
            name = online.getName().getString();
        } else {
            targetId = NameCache.findByName(targetName);
            name = targetId != null ? NameCache.getName(targetId) : null;
        }

        if (targetId == null) {
            PrivateMessage(executor, String.format(PLAYER_NOT_FOUND, targetName));
            return 0;
        }

        Protection p = protectionOpt.get();

        if (p.owner.equals(targetId)) {
            PrivateMessage(executor, PLAYER_IS_OWNER);
            return 0;
        }

        // The whole protected area (all the connected protection blocks) gets the same allowed players
        List<Protection> group = ProtectionsManager.groupOf(p);
        if (group.stream().allMatch(part -> part.allowed.contains(targetId))) {
            PrivateMessage(executor, String.format(PLAYER_ALLOWED_ALREADY, name));
            return 0;
        }

        for (Protection part : group) {
            part.addAllowed(targetId, name);
        }
        ProtectionsManager.save();

        PrivateMessage(executor, String.format(PLAYER_ADDED, name));
        if (online != null) {
            PrivateMessage(online, String.format(PLAYER_ADDED_CONFIRM, executor.getName().getString()));
        }
        return 1;
    }

    // Works for offline players too: their name is stored with the protection
    private static int remove(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer executor = readyPlayer(ctx);
        if (executor == null) return 0;

        Optional<Protection> protectionOpt = findOwnedProtectionAt(executor);
        if (protectionOpt.isEmpty()) {
            PrivateMessage(executor, NOT_IN_PROTECTION);
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        List<Protection> group = ProtectionsManager.groupOf(protectionOpt.get());
        String targetName = StringArgumentType.getString(ctx, "player");

        UUID targetId = null;
        for (Protection part : group) {
            targetId = findAllowed(server, part, targetName);
            if (targetId != null) break;
        }

        if (targetId == null) {
            PrivateMessage(executor, String.format(PLAYER_REMOVED_FAIL, targetName));
            return 0;
        }

        String name = targetName;
        for (Protection part : group) {
            name = part.allowedNames.getOrDefault(targetId, name);
            part.removeAllowed(targetId);
        }
        ProtectionsManager.save();
        PrivateMessage(executor, String.format(PLAYER_REMOVED, name));

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target != null) {
            PrivateMessage(target, String.format(PLAYER_REMOVED_CONFIRM, executor.getName().getString()));
        }
        return 1;
    }

    // Who owns the protection you are standing in, and who may build there
    private static int info(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = readyPlayer(ctx);
        if (player == null) return 0;

        Optional<Protection> protectionOpt = ProtectionsManager.findAt(player.blockPosition(), dimensionOf(player));
        if (protectionOpt.isEmpty()) {
            PrivateMessage(player, NOT_INSIDE_ANY_PROTECTION);
            return 0;
        }

        List<Protection> group = ProtectionsManager.groupOf(protectionOpt.get());
        Protection p = protectionOpt.get();

        Set<String> allowed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Protection part : group) {
            for (UUID id : part.allowed) {
                allowed.add(part.allowedNames.getOrDefault(id, id.toString()));
            }
        }

        PrivateMessage(player, String.format(INFO_OWNER, p.ownerName, group.size()));
        PrivateMessage(player, String.format(INFO_ALLOWED, allowed.isEmpty() ? INFO_NOBODY : String.join(", ", allowed)));
        return 1;
    }

    private static int listOwn(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = readyPlayer(ctx);
        if (player == null) return 0;

        sendList(player, player.getUUID(), player.getName().getString());
        return 1;
    }

    // Admins: the protection blocks of any player (also offline)
    private static int listOther(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = readyPlayer(ctx);
        if (player == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "player");
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        UUID targetId = online != null ? online.getUUID() : NameCache.findByName(targetName);

        if (targetId == null) {
            PrivateMessage(player, String.format(PLAYER_NOT_FOUND, targetName));
            return 0;
        }

        String name = online != null ? online.getName().getString() : NameCache.getName(targetId);
        sendList(player, targetId, name);
        return 1;
    }

    private static void sendList(ServerPlayer player, UUID owner, String ownerName) {
        List<Protection> owned = ProtectionsManager.ownedBy(owner);
        if (owned.isEmpty()) {
            PrivateMessage(player, String.format(LIST_EMPTY, ownerName));
            return;
        }

        PrivateMessage(player, String.format(LIST_HEADER, ownerName, owned.size()));
        for (Protection p : owned) {
            PrivateMessage(player, String.format(LIST_ENTRY, p.x, p.y, p.z, p.dimension));
        }
    }

    // Admins: build in every protection (until turned off, or you leave)
    private static int bypass(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = readyPlayer(ctx);
        if (player == null) return 0;

        boolean on = EventHandlers.toggleBypass(player.getUUID());
        PrivateMessage(player, on ? BYPASS_ON : BYPASS_OFF);
        return 1;
    }

    // Admins: delete the protection you are standing in (with all its connected protection blocks)
    private static int delete(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = readyPlayer(ctx);
        if (player == null) return 0;

        Optional<Protection> protectionOpt = ProtectionsManager.findAt(player.blockPosition(), dimensionOf(player));
        if (protectionOpt.isEmpty()) {
            PrivateMessage(player, NOT_INSIDE_ANY_PROTECTION);
            return 0;
        }

        List<Protection> group = ProtectionsManager.groupOf(protectionOpt.get());
        ProtectionsManager.removeProtections(group);

        PrivateMessage(player, String.format(PROTECTION_DELETED, protectionOpt.get().ownerName, group.size()));
        Logger(0, String.format(LOG_PROTECTION_DELETED, player.getName().getString(), protectionOpt.get().ownerName, group.size()));
        return 1;
    }

    // The player running the command, or null (after telling why) if it can't be used now
    private static ServerPlayer readyPlayer(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            Logger(1, PLAYER_ONLY_COMMAND);
            return null;
        }

        if (!SimpleProtectInitialized) {
            PrivateMessage(player, MOD_INIT_NOT_READY);
            return null;
        }
        return player;
    }

    // Operators only (and the server console, for which the player-only commands then say so)
    private static boolean isAdmin(CommandSourceStack source) {
        try {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                return source.getEntity() == null;
            }

            return source.getServer().getPlayerList().isOp(
                    new NameAndId(player.getUUID(), player.getName().getString())
            );
        } catch (Exception ignored) {
            return false;
        }
    }

    // An online player with that name first (names can change hands), then the stored names,
    // then a UUID (for players added before names were stored)
    private static UUID findAllowed(MinecraftServer server, Protection p, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null && p.allowed.contains(online.getUUID())) {
            return online.getUUID();
        }

        UUID stored = p.findAllowedByName(name);
        if (stored != null) {
            return stored;
        }

        try {
            UUID id = UUID.fromString(name);
            return p.allowed.contains(id) ? id : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Online players, and players who joined before
    private static CompletableFuture<Suggestions> suggestKnownPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(Arrays.asList(ctx.getSource().getServer().getPlayerList().getPlayerNamesArray()));
        names.addAll(NameCache.names());

        for (String name : names) {
            suggestIfMatching(builder, name);
        }
        return builder.buildFuture();
    }

    // The players allowed in the protection the player is standing in
    private static CompletableFuture<Suggestions> suggestAllowedPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = ctx.getSource().getServer();

            findOwnedProtectionAt(player).ifPresent(p -> {
                for (UUID id : p.allowed) {
                    String name = p.allowedNames.get(id);
                    if (name == null) {
                        ServerPlayer online = server.getPlayerList().getPlayer(id);
                        // Name not known yet (added before names were stored, and offline): the UUID works too
                        name = online != null ? online.getName().getString() : id.toString();
                    }
                    suggestIfMatching(builder, name);
                }
            });
        }
        return builder.buildFuture();
    }

    private static void suggestIfMatching(SuggestionsBuilder builder, String name) {
        if (name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
            builder.suggest(name);
        }
    }

    // ---------------- helper ----------------
    private static Optional<Protection> findOwnedProtectionAt(ServerPlayer player) {
        return ProtectionsManager.findByOwnerAt(
                player.getUUID(),
                player.blockPosition(),
                dimensionOf(player)
        );
    }

    private static String dimensionOf(ServerPlayer player) {
        return ProtectionsManager.dimensionId(player.level());
    }
}
