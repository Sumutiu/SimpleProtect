package com.sumutiu.simpleprotect.util;


import com.sumutiu.simpleprotect.storage.Protection;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.stream.Collectors;

import static com.sumutiu.simpleprotect.util.MessagesHelper.*;

public class EventHandlers {
    // track which protection owners a player is inside of, mapping owner UUID to owner name
    private static final Map<UUID, Map<UUID, String>> playerInProtections = new HashMap<>();

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
                        PrivateMessage((ServerPlayerEntity) player, PROTECTION_REMOVED);
                        return true; // Allow break
                    } else {
                        PrivateMessage((ServerPlayerEntity) player, BREAK_OTHERS_PROT);
                        return false; // Prevent break
                    }
                }
            }

            // General block break protection
            if (!ProtectionsManager.isPlayerAllowedAt(player.getUuid(), pos, dim)) {
                PrivateMessage((ServerPlayerEntity) player, NO_BLOCK_DMG);
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
                    // Check for overlaps with other players' protections
                    for (Protection existingProtection : ProtectionsManager.all()) {
                        if (!existingProtection.owner.equals(player.getUuid())) {
                            int totalRadius = Protection.H_RADIUS * 2;
                            boolean overlapX = Math.abs(placePos.getX() - existingProtection.x) <= totalRadius;
                            boolean overlapZ = Math.abs(placePos.getZ() - existingProtection.z) <= totalRadius;
                            if (overlapX && overlapZ) {
                                PrivateMessage((ServerPlayerEntity) player, PROT_OVERLAP);
                                return ActionResult.FAIL;
                            }
                        }
                    }

                    // Since this event is before the block is placed, we can't be 100% sure.
                    // We will create the protection, and if the block placement fails, it's a minor issue.
                    // A better solution would involve a post-placement event.
                    Protection p = new Protection();
                    p.x = placePos.getX();
                    p.y = placePos.getY();
                    p.z = placePos.getZ();
                    p.owner = player.getUuid();
                    p.ownerName = player.getName().getString();
                    p.dimension = dim;
                    ProtectionsManager.addProtection(p);
                    PrivateMessage((ServerPlayerEntity) player, NEW_PROT_CONFIRM);
                    return ActionResult.PASS; // Let the block be placed
                } else {
                    PrivateMessage((ServerPlayerEntity) player, NO_PROT_PLACEMENT);
                    return ActionResult.FAIL;
                }
            }

            // For all other interactions, check permission at the target block
            if (!ProtectionsManager.isPlayerAllowedAt(player.getUuid(), targetPos, dim)) {
                PrivateMessage((ServerPlayerEntity) player, NO_INTERACTION);
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
                    PrivateMessage((ServerPlayerEntity) player, NO_ITEM_USE);
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
            String dim = player.getEntityWorld().getRegistryKey().getValue().toString();
            BlockPos pos = player.getBlockPos();

            // Find all unique owners of protections the player is currently inside
            Map<UUID, String> currentOwnerInfo = ProtectionsManager.protectionsContaining(pos, dim)
                    .stream()
                    .collect(Collectors.toMap(p -> p.owner, p -> p.ownerName, (name1, name2) -> name1));

            Map<UUID, String> previousOwnerInfo = playerInProtections.getOrDefault(playerId, Collections.emptyMap());

            // --- Determine who they entered/left ---
            Set<UUID> currentOwnerIds = currentOwnerInfo.keySet();
            Set<UUID> previousOwnerIds = previousOwnerInfo.keySet();

            Set<UUID> enteredOwnerIds = new HashSet<>(currentOwnerIds);
            enteredOwnerIds.removeAll(previousOwnerIds);

            Set<UUID> leftOwnerIds = new HashSet<>(previousOwnerIds);
            leftOwnerIds.removeAll(currentOwnerIds);

            // --- Send messages ---
            for (UUID ownerId : enteredOwnerIds) {
                String ownerName = currentOwnerInfo.get(ownerId);
                PrivateMessage(player, String.format(PROT_ENTER, ownerName));
            }

            for (UUID ownerId : leftOwnerIds) {
                String ownerName = previousOwnerInfo.get(ownerId); // Get name from previous state
                PrivateMessage(player, String.format(PROT_EXIT, ownerName));
            }

            // --- Update state for next tick ---
            if (currentOwnerInfo.isEmpty()) {
                playerInProtections.remove(playerId);
            } else {
                playerInProtections.put(playerId, currentOwnerInfo);
            }
        }
    }
}
