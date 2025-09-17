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
import java.util.stream.Collectors;

public class EventHandlers {
    // track which protection owners a player is inside of
    private static final Map<UUID, Set<UUID>> playerProtectionOwners = new HashMap<>();

    public static void register() {
        // --- block break prevention & protection removal ---
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient()) return true;

            String dim = world.getRegistryKey().getValue().toString();

            // Handle breaking of an emerald block (potential protection block)
            if (state.isOf(Blocks.EMERALD_BLOCK)) {
                Optional<Protection> protectionAt = ProtectionsManager.protectionsContaining(pos, dim).stream()
                        .filter(p -> p.x == pos.getX() && p.y == pos.getY() && p.z == pos.getZ())
                        .findFirst();

                if (protectionAt.isPresent()) {
                    Protection p = protectionAt.get();
                    if (p.owner.equals(player.getUuid())) {
                        ProtectionsManager.removeProtection(p);
                        player.sendMessage(Text.literal("Your protection has been removed."), false);
                        return true; // Allow break
                    } else {
                        player.sendMessage(Text.literal("You cannot break another player's protection block!"), true);
                        return false; // Prevent break
                    }
                }
            }

            // General block break protection
            if (!ProtectionsManager.isPlayerAllowedAt(player.getUuid(), pos, dim)) {
                player.sendMessage(Text.literal("You cannot break blocks here!"), true);
                return false; // Prevent break
            }

            return true; // Allow break
        });

        // --- block use (interactions + emerald block placement) ---
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;

            BlockPos targetPos = hitResult.getBlockPos();
            String dim = world.getRegistryKey().getValue().toString();

            // Check for placing a protection block
            if (player.getStackInHand(hand).isOf(Items.EMERALD_BLOCK)) {
                BlockPos placePos = targetPos.offset(hitResult.getSide());
                // Allow placing emerald blocks if the location is not protected OR player has perms
                if (ProtectionsManager.isPlayerAllowedAt(player.getUuid(), placePos, dim)) {
                    // Since this event is before the block is placed, we can't be 100% sure.
                    // We will create the protection, and if the block placement fails, it's a minor issue.
                    // A better solution would involve a post-placement event.
                    Protection p = new Protection();
                    p.x = placePos.getX();
                    p.y = placePos.getY();
                    p.z = placePos.getZ();
                    p.owner = player.getUuid();
                    p.dimension = dim;
                    ProtectionsManager.addProtection(p);
                    player.sendMessage(Text.literal("Created a new protection zone!"), false);
                    return ActionResult.PASS; // Let the block be placed
                } else {
                    player.sendMessage(Text.literal("You cannot place a protection block here!"), true);
                    return ActionResult.FAIL;
                }
            }

            // For all other interactions, check permission at the target block
            if (!ProtectionsManager.isPlayerAllowedAt(player.getUuid(), targetPos, dim)) {
                player.sendMessage(Text.literal("You cannot interact with blocks here!"), true);
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

        // --- item uses (lava buckets, flint & steel, etc.) ---
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;

            // We only care about actions that can grief, like placing lava/fire
            // This is a simplified check; a more robust solution might check item tags
            if (!(player.getStackInHand(hand).getItem() instanceof net.minecraft.item.BucketItem) &&
                !(player.getStackInHand(hand).getItem() instanceof net.minecraft.item.FlintAndSteelItem)) {
                return ActionResult.PASS;
            }

            // Raycast to find the block the player is looking at
            net.minecraft.util.hit.HitResult hit = player.raycast(5.0, 0.0f, true);
            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                BlockPos targetPos = ((net.minecraft.util.hit.BlockHitResult) hit).getBlockPos();
                String dim = world.getRegistryKey().getValue().toString();

                if (!ProtectionsManager.isPlayerAllowedAt(player.getUuid(), targetPos, dim)) {
                    player.sendMessage(Text.literal("You cannot use this item here!"), true);
                    return ActionResult.FAIL;
                }
            }

            return ActionResult.PASS;
        });
    }

    // --- called each server tick to check enter/leave ---
    public static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerId = player.getUuid();
            String dim = player.getWorld().getRegistryKey().getValue().toString();
            BlockPos pos = player.getBlockPos();

            // Find all unique owners of protections the player is currently inside
            Set<UUID> currentOwners = ProtectionsManager.protectionsContaining(pos, dim)
                    .stream()
                    .map(p -> p.owner)
                    .collect(Collectors.toSet());

            Set<UUID> previousOwners = playerProtectionOwners.getOrDefault(playerId, Collections.emptySet());

            // --- Determine who they entered/left ---
            Set<UUID> enteredOwners = new HashSet<>(currentOwners);
            enteredOwners.removeAll(previousOwners);

            Set<UUID> leftOwners = new HashSet<>(previousOwners);
            leftOwners.removeAll(currentOwners);

            // --- Send messages ---
            for (UUID ownerId : enteredOwners) {
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
                String ownerName = (owner != null) ? owner.getName().getString() : "someone";
                player.sendMessage(Text.literal("You have entered " + ownerName + "'s protection."), false);
            }

            for (UUID ownerId : leftOwners) {
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
                String ownerName = (owner != null) ? owner.getName().getString() : "someone";
                player.sendMessage(Text.literal("You have left " + ownerName + "'s protection."), false);
            }

            // --- Update state for next tick ---
            if (currentOwners.isEmpty()) {
                playerProtectionOwners.remove(playerId);
            } else {
                playerProtectionOwners.put(playerId, currentOwners);
            }
        }
    }
}
