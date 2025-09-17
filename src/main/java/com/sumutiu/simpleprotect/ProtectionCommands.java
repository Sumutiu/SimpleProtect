package com.sumutiu.simpleprotect;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public class ProtectionCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment env) -> {
                    dispatcher.register(CommandManager.literal("sprotection")
                            .then(CommandManager.literal("add")
                                    .then(CommandManager.argument("player", StringArgumentType.word())
                                            .executes(ctx -> {
                                                ServerPlayerEntity executor = ctx.getSource().getPlayer();
                                                if (executor == null) return 0;

                                                String targetName = StringArgumentType.getString(ctx, "player");
                                                ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);

                                                if (target == null) {
                                                    executor.sendMessage(Text.literal("Player not found: " + targetName), false);
                                                    return 1;
                                                }

                                                Protection p = findOwnedProtectionAt(executor);
                                                if (p == null) {
                                                    executor.sendMessage(Text.literal("You are not standing inside your own protection."), false);
                                                    return 1;
                                                }

                                                if (!p.allowed.contains(target.getUuid())) {
                                                    p.allowed.add(target.getUuid());
                                                    ProtectionsManager.updateProtection(p);
                                                    executor.sendMessage(Text.literal("Added " + target.getName().getString() + " to your protection."), false);

                                                    // Notify the added player
                                                    target.sendMessage(Text.literal("You have been added to " + executor.getName().getString() + "'s protection."), false);
                                                } else {
                                                    executor.sendMessage(Text.literal("That player is already allowed."), false);
                                                }
                                                return 1;
                                            })))
                            .then(CommandManager.literal("remove")
                                    .then(CommandManager.argument("player", StringArgumentType.word())
                                            .executes(ctx -> {
                                                ServerPlayerEntity executor = ctx.getSource().getPlayer();
                                                if (executor == null) return 0;

                                                String targetName = StringArgumentType.getString(ctx, "player");
                                                ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);

                                                if (target == null) {
                                                    executor.sendMessage(Text.literal("Player not found: " + targetName), false);
                                                    return 1;
                                                }

                                                Protection p = findOwnedProtectionAt(executor);
                                                if (p == null) {
                                                    executor.sendMessage(Text.literal("You are not standing inside your own protection."), false);
                                                    return 1;
                                                }

                                                if (p.allowed.remove(target.getUuid())) {
                                                    ProtectionsManager.updateProtection(p);
                                                    executor.sendMessage(Text.literal("Removed " + target.getName().getString() + " from your protection."), false);
                                                } else {
                                                    executor.sendMessage(Text.literal("That player is not allowed."), false);
                                                }
                                                return 1;
                                            })))
                    );
                }
        );
    }

    private static Protection findOwnedProtectionAt(ServerPlayerEntity player) {
        String dim = player.getWorld().getRegistryKey().getValue().getPath();
        List<Protection> inside = ProtectionsManager.protectionsContaining(player.getBlockPos(), dim);
        for (Protection p : inside) {
            if (p.owner.equals(player.getUuid())) return p;
        }
        return null;
    }
}
