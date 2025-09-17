package com.sumutiu.simpleprotect;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Optional;

public class ProtectionCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, env) -> {
                    dispatcher.register(CommandManager.literal("sprotection")
                            .then(CommandManager.literal("add")
                                    .then(CommandManager.argument("player", StringArgumentType.word())
                                            .executes(ctx -> {
                                                ServerPlayerEntity executor = ctx.getSource().getPlayer();
                                                if (executor == null) return 0;

                                                Optional<Protection> protectionOpt = findOwnedProtectionAt(executor);
                                                if (protectionOpt.isEmpty()) {
                                                    executor.sendMessage(Text.literal("You are not standing inside one of your protections."), false);
                                                    return 1;
                                                }

                                                String targetName = StringArgumentType.getString(ctx, "player");
                                                ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                                if (target == null) {
                                                    executor.sendMessage(Text.literal("Player not found: " + targetName), false);
                                                    return 1;
                                                }

                                                Protection p = protectionOpt.get();
                                                if (p.allowed.contains(target.getUuid())) {
                                                    executor.sendMessage(Text.literal(targetName + " is already allowed in this protection."), false);
                                                } else {
                                                    p.allowed.add(target.getUuid());
                                                    ProtectionsManager.updateProtection(p);
                                                    executor.sendMessage(Text.literal("Added " + targetName + " to this protection."), false);
                                                    target.sendMessage(Text.literal("You have been added to " + executor.getName().getString() + "'s protection."), false);
                                                }
                                                return 1;
                                            })))
                            .then(CommandManager.literal("remove")
                                    .then(CommandManager.argument("player", StringArgumentType.word())
                                            .executes(ctx -> {
                                                ServerPlayerEntity executor = ctx.getSource().getPlayer();
                                                if (executor == null) return 0;

                                                Optional<Protection> protectionOpt = findOwnedProtectionAt(executor);
                                                if (protectionOpt.isEmpty()) {
                                                    executor.sendMessage(Text.literal("You are not standing inside one of your protections."), false);
                                                    return 1;
                                                }

                                                String targetName = StringArgumentType.getString(ctx, "player");
                                                ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                                if (target == null) {
                                                    executor.sendMessage(Text.literal("Player not found: " + targetName), false);
                                                    return 1;
                                                }

                                                Protection p = protectionOpt.get();
                                                if (p.allowed.remove(target.getUuid())) {
                                                    ProtectionsManager.updateProtection(p);
                                                    executor.sendMessage(Text.literal("Removed " + targetName + " from this protection."), false);
                                                    target.sendMessage(Text.literal("You have been removed from " + executor.getName().getString() + "'s protection."), false);
                                                } else {
                                                    executor.sendMessage(Text.literal(targetName + " was not in this protection's allowed list."), false);
                                                }
                                                return 1;
                                            })))
                    );
                }
        );
    }

    private static Optional<Protection> findOwnedProtectionAt(ServerPlayerEntity player) {
        String dim = player.getWorld().getRegistryKey().getValue().toString();
        return ProtectionsManager.findByOwnerAt(player.getUuid(), player.getBlockPos(), dim);
    }
}
