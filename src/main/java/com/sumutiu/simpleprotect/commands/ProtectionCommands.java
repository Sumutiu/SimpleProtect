package com.sumutiu.simpleprotect.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.sumutiu.simpleprotect.storage.Protection;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

import static com.sumutiu.simpleprotect.SimpleProtect.SimpleProtectInitialized;
import static com.sumutiu.simpleprotect.util.MessagesHelper.*;

public class ProtectionCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, _, _) -> dispatcher.register(
                        Commands.literal("sprotection")

                                // ---------------- ADD ----------------
                                .then(Commands.literal("add")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    MinecraftServer server = context.getSource().getServer();

                                                    for (String name : server.getPlayerList().getPlayerNamesArray()) {
                                                        builder.suggest(name);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {

                                                    CommandSourceStack source = ctx.getSource();
                                                    if (!(source.getEntity() instanceof ServerPlayer executor)) {
                                                        Logger(1, PLAYER_ONLY_COMMAND);
                                                        return 0;
                                                    }

                                                    if (SimpleProtectInitialized) {
                                                        Optional<Protection> protectionOpt = findOwnedProtectionAt(executor);

                                                        if (protectionOpt.isEmpty()) {
                                                            PrivateMessage(executor, NOT_IN_PROTECTION);
                                                            return 1;
                                                        }

                                                        String targetName = StringArgumentType.getString(ctx, "player");

                                                        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);

                                                        if (target == null) {
                                                            PrivateMessage(executor, String.format(PLAYER_NOT_FOUND, targetName));
                                                            return 1;
                                                        }

                                                        Protection p = protectionOpt.get();

                                                        if (p.allowed.contains(target.getUUID())) {
                                                            PrivateMessage(executor, String.format(PLAYER_ALLOWED_ALREADY, targetName));
                                                        } else {
                                                            p.allowed.add(target.getUUID());
                                                            ProtectionsManager.save();

                                                            PrivateMessage(executor, String.format(PLAYER_ADDED, targetName));

                                                            PrivateMessage(target, String.format(PLAYER_ADDED_CONFIRM, executor.getName().getString()));
                                                        }
                                                        return 1;
                                                    } else {
                                                        PrivateMessage(executor, MOD_INIT_NOT_READY);
                                                        return 0;
                                                    }
                                                })
                                        )
                                )

                                // ---------------- REMOVE ----------------
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    MinecraftServer server = context.getSource().getServer();

                                                    for (String name : server.getPlayerList().getPlayerNamesArray()) {
                                                        builder.suggest(name);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    CommandSourceStack source = ctx.getSource();
                                                    if (!(source.getEntity() instanceof ServerPlayer executor)) {
                                                        Logger(1, PLAYER_ONLY_COMMAND);
                                                        return 0;
                                                    }

                                                    if (SimpleProtectInitialized) {

                                                        Optional<Protection> protectionOpt = findOwnedProtectionAt(executor);

                                                        if (protectionOpt.isEmpty()) {
                                                            PrivateMessage(executor, NOT_IN_PROTECTION);
                                                            return 1;
                                                        }

                                                        String targetName = StringArgumentType.getString(ctx, "player");

                                                        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);

                                                        if (target == null) {
                                                            PrivateMessage(executor, String.format(PLAYER_NOT_FOUND, targetName));
                                                            return 1;
                                                        }

                                                        Protection p = protectionOpt.get();

                                                        if (p.allowed.remove(target.getUUID())) {
                                                            ProtectionsManager.save();

                                                            PrivateMessage(executor, String.format(PLAYER_REMOVED, targetName));
                                                            PrivateMessage(target, String.format(PLAYER_REMOVED_CONFIRM, executor.getName().getString()));
                                                        } else {
                                                            PrivateMessage(executor, String.format(PLAYER_REMOVED_FAIL, targetName));
                                                        }
                                                    }

                                                    return 1;
                                                })
                                        )
                                )
                )
        );
    }

    // ---------------- helper ----------------
    private static Optional<Protection> findOwnedProtectionAt(ServerPlayer player) {
        String dim = player.level().dimension().identifier().toString();
        return ProtectionsManager.findByOwnerAt(
                player.getUUID(),
                player.blockPosition(),
                dim
        );
    }
}