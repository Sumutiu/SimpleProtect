package com.sumutiu.simpleprotect.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.sumutiu.simpleprotect.storage.Protection;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;

import static com.sumutiu.simpleprotect.util.MessagesHelper.*;

public class ProtectionCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, env) -> dispatcher.register(CommandManager.literal("sprotection")
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            MinecraftServer server = context.getSource().getServer();
                                            if (server != null) {
                                                return CommandSource.suggestMatching(
                                                        server.getPlayerNames(), builder
                                                );
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayerEntity executor = ctx.getSource().getPlayer();
                                            if (executor == null) return 0;

                                            Optional<Protection> protectionOpt = findOwnedProtectionAt(executor);
                                            if (protectionOpt.isEmpty()) {
                                                PrivateMessage(executor, NOT_IN_PROTECTION);
                                                return 1;
                                            }

                                            String targetName = StringArgumentType.getString(ctx, "player");
                                            ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                            if (target == null) {
                                                PrivateMessage(executor, String.format(PLAYER_NOT_FOUND, targetName));
                                                return 1;
                                            }

                                            Protection p = protectionOpt.get();
                                            if (p.allowed.contains(target.getUuid())) {
                                                PrivateMessage(executor, String.format(PLAYER_ALLOWED_ALREADY, targetName));
                                            } else {
                                                p.allowed.add(target.getUuid());
                                                ProtectionsManager.save();
                                                PrivateMessage(executor, String.format(PLAYER_ADDED, targetName));
                                                PrivateMessage(target, String.format(PLAYER_ADDED_CONFIRM, executor.getName().getString()));
                                            }
                                            return 1;
                                        })))
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            MinecraftServer server = context.getSource().getServer();
                                            if (server != null) {
                                                return CommandSource.suggestMatching(
                                                        server.getPlayerNames(), builder
                                                );
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayerEntity executor = ctx.getSource().getPlayer();
                                            if (executor == null) return 0;

                                            Optional<Protection> protectionOpt = findOwnedProtectionAt(executor);
                                            if (protectionOpt.isEmpty()) {
                                                PrivateMessage(executor, NOT_IN_PROTECTION);
                                                return 1;
                                            }

                                            String targetName = StringArgumentType.getString(ctx, "player");
                                            ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                            if (target == null) {
                                                PrivateMessage(executor, String.format(PLAYER_NOT_FOUND, targetName));
                                                return 1;
                                            }

                                            Protection p = protectionOpt.get();
                                            if (p.allowed.remove(target.getUuid())) {
                                                ProtectionsManager.save();
                                                PrivateMessage(executor, String.format(PLAYER_REMOVED, targetName));
                                                PrivateMessage(target, String.format(PLAYER_REMOVED_CONFIRM, executor.getName().getString()));
                                            } else {
                                                PrivateMessage(executor, String.format(PLAYER_REMOVED_FAIL, targetName));
                                            }
                                            return 1;
                                        })))
                )
        );
    }

    private static Optional<Protection> findOwnedProtectionAt(ServerPlayerEntity player) {
        String dim = player.getEntityWorld().getRegistryKey().getValue().toString();
        return ProtectionsManager.findByOwnerAt(player.getUuid(), player.getBlockPos(), dim);
    }
}
