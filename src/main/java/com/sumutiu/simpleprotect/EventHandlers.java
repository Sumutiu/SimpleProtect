package com.sumutiu.simpleprotect;


import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class EventHandlers {
    // track which protections a player is inside
    private static final Map<UUID, Set<String>> playerProtections = new HashMap<>();

    public static void register() {
        // --- block break prevention & protection removal ---
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient) return true;

            String dim = world.getRegistryKey().getValue().getPath();

            // Check if the broken block is the protection emerald block
            if (state.isOf(Blocks.EMERALD_BLOCK)) {
                ProtectionsManager.all().stream()
                        .filter(p -> p.x == pos.getX() && p.y == pos.getY() && p.z == pos.getZ() && p.dimension.equals(dim))
                        .findFirst()
                        .ifPresent(p -> {
                            if (p.owner.equals(player.getUuid())) {
                                ProtectionsManager.removeProtection(p);
                                player.sendMessage(Text.literal("Your protection has been removed."), false);
                            } else {
                                player.sendMessage(Text.literal("You cannot break another player's protection block!"), true);
                                throw new RuntimeException("Cancel block break"); // cancel the event
                            }
                        });
            }

            // Normal protection rule for any other block
            if (!ProtectionsManager.isPlayerAllowedAt(player.getUuid(), pos, dim)) {
                player.sendMessage(Text.literal("You cannot break blocks here!"), true);
                return false;
            }
            return true;
        });

        // --- block use (interactions + emerald block placement) ---
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            BlockPos targetPos = hitResult.getBlockPos();
            BlockPos placePos = targetPos.offset(hitResult.getSide());

            // detect if placing an emerald block
            if (player.getStackInHand(hand).isOf(Items.EMERALD_BLOCK)) {
                world.getServer().execute(() -> {
                    if (world.getBlockState(placePos).isOf(Blocks.EMERALD_BLOCK)) {
                        Protection p = new Protection();
                        p.x = placePos.getX();
                        p.y = placePos.getY();
                        p.z = placePos.getZ();
                        p.owner = player.getUuid();
                        p.allowed = new ArrayList<>();
                        p.dimension = world.getRegistryKey().getValue().getPath();
                        ProtectionsManager.addProtection(p);
                        player.sendMessage(Text.literal("Created a new protection zone!"), false);
                    }
                });
            }

            // normal protection interaction checks
            if (!ProtectionsManager.isPlayerAllowedAt(player.getUuid(), targetPos, world.getRegistryKey().getValue().getPath())) {
                player.sendMessage(Text.literal("You cannot interact here!"), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // --- item uses (lava buckets, flint & steel, etc.) ---
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            BlockPos pos = player.getBlockPos();
            if (!ProtectionsManager.isPlayerAllowedAt(player.getUuid(), pos, world.getRegistryKey().getValue().getPath())) {
                player.sendMessage(Text.literal("You cannot use items here!"), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    // --- called each server tick to check enter/leave ---
    public static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID pid = player.getUuid();
            String dim = player.getWorld().getRegistryKey().getValue().getPath();
            BlockPos pos = player.getBlockPos();

            List<Protection> inside = ProtectionsManager.protectionsContaining(pos, dim);
            Set<String> newSet = new HashSet<>();
            for (Protection p : inside) newSet.add(p.idString());

            Set<String> oldSet = playerProtections.getOrDefault(pid, Collections.emptySet());

            Set<String> entered = new HashSet<>(newSet);
            entered.removeAll(oldSet);
            Set<String> left = new HashSet<>(oldSet);
            left.removeAll(newSet);

            for (String eid : entered) {
                Protection p = ProtectionsManager.all().stream().filter(pr -> pr.idString().equals(eid)).findFirst().orElse(null);
                if (p != null) {
                    var owner = server.getPlayerManager().getPlayer(p.owner);
                    if (owner != null) {
                        player.sendMessage(Text.literal("You entered " + owner.getName().getString() + "'s protection."), false);
                    }
                }
            }
            for (String lid : left) {
                Protection p = ProtectionsManager.all().stream().filter(pr -> pr.idString().equals(lid)).findFirst().orElse(null);
                if (p != null) {
                    var owner = server.getPlayerManager().getPlayer(p.owner);
                    if (owner != null) {
                        player.sendMessage(Text.literal("You left " + owner.getName().getString() + "'s protection."), false);
                    }
                }
            }

            playerProtections.put(pid, newSet);
        }
    }
}
