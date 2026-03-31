package com.sumutiu.simpleprotect.util;

import com.sumutiu.simpleprotect.storage.Protection;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.*;
import java.util.stream.Collectors;

import static com.sumutiu.simpleprotect.util.MessagesHelper.*;

public class EventHandlers {

    private static final Map<UUID, Map<UUID, String>> playerInProtections = new HashMap<>();

    public static void register() {

        // --- block break prevention & protection removal ---
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, _) -> {
            if (world.isClientSide()) return true;

            String dim = world.dimension().identifier().toString();

            if (state.is(Blocks.EMERALD_BLOCK)) {

                Optional<Protection> protectionAt = ProtectionsManager.protectionsContaining(pos, dim).stream()
                        .filter(p -> p.x == pos.getX() && p.y == pos.getY() && p.z == pos.getZ())
                        .findFirst();

                if (protectionAt.isPresent()) {
                    Protection p = protectionAt.get();

                    if (p.owner.equals(player.getUUID())) {
                        ProtectionsManager.removeProtection(p);
                        PrivateMessage((ServerPlayer) player, PROTECTION_REMOVED);
                        return true;
                    } else {
                        PrivateMessage((ServerPlayer) player, BREAK_OTHERS_PROT);
                        return false;
                    }
                }
            }

            if (!ProtectionsManager.isPlayerAllowedAt(player.getUUID(), pos, dim)) {
                PrivateMessage((ServerPlayer) player, NO_BLOCK_DMG);
                return false;
            }

            return true;
        });

        // --- block use ---
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;

            BlockPos targetPos = hitResult.getBlockPos();
            String dim = world.dimension().identifier().toString();

            if (player.getItemInHand(hand).is(Items.EMERALD_BLOCK)) {

                BlockPos placePos = targetPos.relative(hitResult.getDirection());

                if (ProtectionsManager.isPlayerAllowedAt(player.getUUID(), placePos, dim)) {

                    for (Protection existing : ProtectionsManager.all()) {
                        if (!existing.owner.equals(player.getUUID())) {

                            int r = Protection.H_RADIUS * 2;

                            boolean overlapX = Math.abs(placePos.getX() - existing.x) <= r;
                            boolean overlapZ = Math.abs(placePos.getZ() - existing.z) <= r;

                            if (overlapX && overlapZ) {
                                PrivateMessage((ServerPlayer) player, PROT_OVERLAP);
                                return InteractionResult.FAIL;
                            }
                        }
                    }

                    Protection p = new Protection();
                    p.x = placePos.getX();
                    p.y = placePos.getY();
                    p.z = placePos.getZ();
                    p.owner = player.getUUID();
                    p.ownerName = player.getName().getString();
                    p.dimension = dim;

                    ProtectionsManager.addProtection(p);
                    PrivateMessage((ServerPlayer) player, NEW_PROT_CONFIRM);

                    return InteractionResult.PASS;
                } else {
                    PrivateMessage((ServerPlayer) player, NO_PROT_PLACEMENT);
                    return InteractionResult.FAIL;
                }
            }

            if (!ProtectionsManager.isPlayerAllowedAt(player.getUUID(), targetPos, dim)) {
                PrivateMessage((ServerPlayer) player, NO_INTERACTION);
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });

        // --- item use ---
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) return InteractionResult.PASS;

            var item = player.getItemInHand(hand).getItem();

            if (!(item instanceof BucketItem) &&
                    !(item instanceof FlintAndSteelItem)) {
                return InteractionResult.PASS;
            }

            HitResult hit = player.pick(5.0, 0.0f, false);

            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos targetPos = ((BlockHitResult) hit).getBlockPos();
                String dim = world.dimension().identifier().toString();

                if (!ProtectionsManager.isPlayerAllowedAt(player.getUUID(), targetPos, dim)) {
                    PrivateMessage((ServerPlayer) player, NO_ITEM_USE);
                    return InteractionResult.FAIL;
                }
            }

            return InteractionResult.PASS;
        });
    }

    // --- tick tracking ---
    public static void onServerTick(MinecraftServer server) {

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {

            UUID playerId = player.getUUID();
            String dim = player.level().dimension().identifier().toString();
            BlockPos pos = player.blockPosition();

            Map<UUID, String> currentOwnerInfo = ProtectionsManager.protectionsContaining(pos, dim)
                    .stream()
                    .collect(Collectors.toMap(
                            p -> p.owner,
                            p -> p.ownerName,
                            (a, _) -> a
                    ));

            Map<UUID, String> previousOwnerInfo =
                    playerInProtections.getOrDefault(playerId, Collections.emptyMap());

            Set<UUID> current = currentOwnerInfo.keySet();
            Set<UUID> previous = previousOwnerInfo.keySet();

            Set<UUID> entered = new HashSet<>(current);
            entered.removeAll(previous);

            Set<UUID> left = new HashSet<>(previous);
            left.removeAll(current);

            for (UUID id : entered) {
                PrivateMessage(player, String.format(PROT_ENTER, currentOwnerInfo.get(id)));
            }

            for (UUID id : left) {
                PrivateMessage(player, String.format(PROT_EXIT, previousOwnerInfo.get(id)));
            }

            if (currentOwnerInfo.isEmpty()) {
                playerInProtections.remove(playerId);
            } else {
                playerInProtections.put(playerId, currentOwnerInfo);
            }
        }
    }
}