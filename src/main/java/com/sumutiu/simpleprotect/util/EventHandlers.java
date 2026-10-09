package com.sumutiu.simpleprotect.util;

import com.sumutiu.simpleprotect.config.SimpleProtectConfig;
import com.sumutiu.simpleprotect.storage.Protection;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.players.NameAndId;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static com.sumutiu.simpleprotect.util.MessagesHelper.*;

public class EventHandlers {

    // The same "you can't do that" message is not repeated within this time (one click can fire for both hands)
    private static final long DENY_MESSAGE_COOLDOWN_MS = 1000;

    // Protection blocks a player is placing: the protection is created on the next tick, once the block is really there
    private record PendingProtection(UUID owner, String ownerName, Level world, BlockPos pos, String dimension) {}

    private record DenyMessage(String message, long timeMs) {}

    // The protections each player was in at the last check: owner -> owner name
    private static final Map<UUID, Map<UUID, String>> playerInProtections = new HashMap<>();
    private static final Map<UUID, DenyMessage> lastDenyMessage = new HashMap<>();
    private static final List<PendingProtection> pendingProtections = new ArrayList<>();
    // Admins who turned on /sprotection bypass (until they turn it off, leave, or the server stops)
    private static final Set<UUID> bypassing = new HashSet<>();

    public static void register() {

        // --- block break prevention ---
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, _, _) -> {
            if (world.isClientSide()) return true;

            String dim = ProtectionsManager.dimensionId(world);

            Optional<Protection> protectionAt = ProtectionsManager.findCenterAt(pos, dim);
            if (protectionAt.isPresent()) {
                // The owner (or an admin in bypass) may break it: the protection is removed once the block is really broken, see AFTER
                if (protectionAt.get().owner.equals(player.getUUID()) || isBypassing(player)) {
                    return true;
                }
                deny(player, BREAK_OTHERS_PROT);
                return false;
            }

            if (!canBuild(player, pos, dim)) {
                deny(player, NO_BLOCK_DMG);
                return false;
            }

            return true;
        });

        // --- protection removal: the protection block was broken ---
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, _, _) -> afterBlockBreak(world, player, pos));

        // --- block use ---
        UseBlockCallback.EVENT.register(EventHandlers::onUseBlock);

        // --- item use (buckets, lily pads) ---
        UseItemCallback.EVENT.register(EventHandlers::onUseItem);

        // --- entities: hitting and using them (item frames, armor stands, animals, villagers, vehicles...) ---
        AttackEntityCallback.EVENT.register((player, world, _, entity, _) -> onEntityInteraction(player, world, entity));
        UseEntityCallback.EVENT.register((player, world, _, entity, _) -> onEntityInteraction(player, world, entity));

        // --- entities: damage a player causes from a distance (arrows, tridents, TNT they lit...) and explosions.
        // EntityMixin covers the entities this event doesn't (armor stands, item frames, boats...) ---
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, _) -> !blocksDamage(entity, source));
    }

    private static void afterBlockBreak(Level world, Player player, BlockPos pos) {
        if (world.isClientSide()) return;

        String dim = ProtectionsManager.dimensionId(world);
        Optional<Protection> protectionAt = ProtectionsManager.findCenterAt(pos, dim);
        if (protectionAt.isEmpty()) return;

        Protection p = protectionAt.get();
        if (p.owner.equals(player.getUUID())) {
            ProtectionsManager.removeProtection(p);
            message(player, PROTECTION_REMOVED);
        } else if (isBypassing(player)) {
            ProtectionsManager.removeProtection(p);
            message(player, String.format(ADMIN_REMOVED_PROTECTION_BLOCK, p.ownerName));
        }
    }

    private static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (world.isClientSide()) return InteractionResult.PASS;

        UUID playerId = player.getUUID();
        BlockPos targetPos = hitResult.getBlockPos();
        String dim = ProtectionsManager.dimensionId(world);
        ItemStack stack = player.getItemInHand(hand);
        boolean protectionBlock = isProtectionBlock(stack);

        // Where a block from this hand would go: the clicked block itself if it can be replaced (grass, snow),
        // otherwise the block next to the clicked side. Fire also goes next to the clicked side.
        BlockPos placePos = null;
        if (stack.getItem() instanceof FlintAndSteelItem || stack.getItem() instanceof FireChargeItem) {
            placePos = targetPos.relative(hitResult.getDirection());
        } else if (stack.getItem() instanceof BlockItem) {
            placePos = new BlockPlaceContext(player, hand, stack, hitResult).getClickedPos();

            // Overlap, distance and limit are checked once the block is placed: if one of them doesn't allow
            // a protection there, the block is still placed, as a normal block (e.g. a beacon in a friend's base)
            if (protectionBlock && !canBuild(player, placePos, dim)) {
                deny(player, NO_PROT_PLACEMENT);
                return InteractionResult.FAIL;
            }
        }

        if (!canBuild(player, targetPos, dim)) {
            deny(player, NO_INTERACTION);
            return InteractionResult.FAIL;
        }

        // Also stops placing a block into a protection from outside it (clicking the side of a block at the edge)
        if (placePos != null && !canBuild(player, placePos, dim)) {
            deny(player, NO_BLOCK_PLACE);
            return InteractionResult.FAIL;
        }

        // The click may still not place the block (e.g. it opens a chest, or something is in the way),
        // so the protection is only created on the next tick if the block is really there
        if (placePos != null && protectionBlock && !world.getBlockState(placePos).is(SimpleProtectConfig.getProtectionBlock())) {
            addPending(new PendingProtection(playerId, player.getName().getString(), world, placePos, dim));
        }

        return InteractionResult.PASS;
    }

    private static InteractionResult onUseItem(Player player, Level world, InteractionHand hand) {
        if (world.isClientSide()) return InteractionResult.PASS;

        Item item = player.getItemInHand(hand).getItem();

        // Aim the same way the item does: an empty bucket or a lily pad targets the liquid itself,
        // a full bucket goes through liquids to the block behind them
        ClipContext.Fluid fluid;
        if (item == Items.BUCKET || item instanceof PlaceOnWaterBlockItem) {
            fluid = ClipContext.Fluid.SOURCE_ONLY;
        } else if (item instanceof BucketItem) {
            fluid = ClipContext.Fluid.NONE;
        } else {
            return InteractionResult.PASS;
        }

        BlockHitResult hit = rayTrace(player, world, fluid);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;

        // A full bucket places its liquid next to the block it hits, so check that block too
        String dim = ProtectionsManager.dimensionId(world);
        BlockPos targetPos = hit.getBlockPos();
        if (!canBuild(player, targetPos, dim) || !canBuild(player, targetPos.relative(hit.getDirection()), dim)) {
            deny(player, NO_ITEM_USE);
            return InteractionResult.FAIL;
        }

        return InteractionResult.PASS;
    }

    // What the player is looking at, within reach (the same way buckets find their target)
    private static BlockHitResult rayTrace(Player player, Level world, ClipContext.Fluid fluid) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
        return world.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, fluid, player));
    }

    private static InteractionResult onEntityInteraction(Player player, Level world, Entity entity) {
        if (world.isClientSide() || SimpleProtectConfig.getProtectEntities() || isNotProtectedEntity(entity)) {
            return InteractionResult.PASS;
        }

        if (isAttacking(entity, player) || canBuild(player, entity.blockPosition(), ProtectionsManager.dimensionId(world))) {
            return InteractionResult.PASS;
        }

        deny(player, NO_ENTITY_INTERACTION);
        return InteractionResult.FAIL;
    }

    // True if an entity inside a protection must not take this damage: from a player who isn't allowed there
    // (arrows, tridents, TNT they lit), from their tamed pets, or from an explosion no allowed player caused (creepers, ghasts)
    public static boolean blocksDamage(Entity entity, DamageSource source) {
        if (SimpleProtectConfig.getProtectEntities() || !ProtectionsManager.hasAny() || isNotProtectedEntity(entity)) return false;

        // Who is behind it: a player, or the owner of a tamed pet (a wolf attacking for them).
        // A pet whose owner is offline counts as someone who isn't allowed. Wild animals hunting are not affected.
        Player attacker = null;
        boolean fromPet = false;
        if (source.getEntity() instanceof Player player) {
            attacker = player;
        } else if (source.getEntity() instanceof TamableAnimal pet && pet.isTame()) {
            fromPet = true;
            if (pet.getOwner() instanceof Player owner) {
                attacker = owner;
            }
        }

        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        if (attacker == null && !fromPet && !(explosion && SimpleProtectConfig.getProtectFromExplosions())) return false;
        if (attacker != null && isAttacking(entity, attacker)) return false;

        String dim = ProtectionsManager.dimensionId(entity.level());
        if (!ProtectionsManager.isProtected(entity.blockPosition(), dim)) return false;

        return attacker == null || !canBuild(attacker, entity.blockPosition(), dim);
    }

    // Players, monsters and projectiles (fireballs can be hit back) are never protected; everything else in a protection is
    private static boolean isNotProtectedEntity(Entity entity) {
        return entity instanceof Player
                || entity instanceof Projectile
                || entity.getType().getCategory() == MobCategory.MONSTER;
    }

    // Anyone may fight back against a mob that is attacking them (an iron golem, a wolf)
    private static boolean isAttacking(Entity entity, Player player) {
        return entity instanceof Mob mob && mob.getTarget() == player;
    }

    private static boolean isProtectionBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == SimpleProtectConfig.getProtectionBlock();
    }

    // The owner, the players they allowed, and admins in bypass can build here
    private static boolean canBuild(Player player, BlockPos pos, String dimension) {
        return isBypassing(player) || ProtectionsManager.isPlayerAllowedAt(player.getUUID(), pos, dimension);
    }

    // --- tick tracking ---
    public static void onServerTick(MinecraftServer server) {
        createPendingProtections(server);

        // Bypass ends when the admin is no longer an operator
        if (!bypassing.isEmpty()) {
            bypassing.removeIf(id -> {
                ServerPlayer admin = server.getPlayerList().getPlayer(id);
                return admin == null || !server.getPlayerList().isOp(new NameAndId(id, admin.getName().getString()));
            });
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {

            UUID playerId = player.getUUID();
            String dim = ProtectionsManager.dimensionId(player.level());
            BlockPos pos = player.blockPosition();

            Map<UUID, String> currentOwnerInfo = new HashMap<>();
            for (Protection p : ProtectionsManager.protectionsContaining(pos, dim)) {
                currentOwnerInfo.putIfAbsent(p.owner, p.ownerName);
            }

            Map<UUID, String> previousOwnerInfo =
                    playerInProtections.getOrDefault(playerId, Collections.emptyMap());

            for (Map.Entry<UUID, String> entry : currentOwnerInfo.entrySet()) {
                if (!previousOwnerInfo.containsKey(entry.getKey())) {
                    PrivateMessage(player, String.format(PROT_ENTER, entry.getValue()));
                }
            }

            for (Map.Entry<UUID, String> entry : previousOwnerInfo.entrySet()) {
                if (!currentOwnerInfo.containsKey(entry.getKey())) {
                    PrivateMessage(player, String.format(PROT_EXIT, entry.getValue()));
                }
            }

            if (currentOwnerInfo.isEmpty()) {
                playerInProtections.remove(playerId);
            } else {
                playerInProtections.put(playerId, currentOwnerInfo);
            }
        }
    }

    private static void createPendingProtections(MinecraftServer server) {
        if (pendingProtections.isEmpty()) return;

        List<PendingProtection> pending = new ArrayList<>(pendingProtections);
        pendingProtections.clear();

        for (PendingProtection placed : pending) {
            // Not placed after all
            if (!placed.world().getBlockState(placed.pos()).is(SimpleProtectConfig.getProtectionBlock())) continue;

            ServerPlayer player = server.getPlayerList().getPlayer(placed.owner());

            // The owner put back a lost protection block: keep the protection as it is, with its allowed players
            if (ProtectionsManager.findCenterAt(placed.pos(), placed.dimension()).isPresent()) {
                if (player != null) PrivateMessage(player, PROTECTION_RESTORED);
                continue;
            }

            // Protections of different players can't overlap
            if (ProtectionsManager.overlapsOtherOwner(placed.owner(), placed.pos(), placed.dimension())) {
                if (player != null) deny(player, PROT_OVERLAP);
                continue;
            }

            // Inside your own protection, a protection block only extends it when it is far enough from your
            // other protection blocks. Closer ones (like a beacon base) stay normal blocks.
            boolean insideOwn = ProtectionsManager.findByOwnerAt(placed.owner(), placed.pos(), placed.dimension()).isPresent();
            int minDistance = SimpleProtectConfig.getMinExtensionDistance();
            if (insideOwn && ProtectionsManager.distanceToOwnBlock(placed.owner(), placed.pos(), placed.dimension()) < minDistance) {
                if (player != null) deny(player, String.format(EXTENSION_TOO_CLOSE, minDistance));
                continue;
            }

            int limit = SimpleProtectConfig.getMaxProtectionsPerPlayer();
            if (limit > 0 && ProtectionsManager.ownedBy(placed.owner()).size() >= limit) {
                if (player != null) deny(player, String.format(PROT_LIMIT_REACHED, limit));
                continue;
            }

            Protection p = new Protection();
            p.x = placed.pos().getX();
            p.y = placed.pos().getY();
            p.z = placed.pos().getZ();
            p.owner = placed.owner();
            p.ownerName = placed.ownerName();
            p.dimension = placed.dimension();

            ProtectionsManager.addProtection(p);

            // Touching or overlapping your other protections: it becomes part of that area, with the same allowed players
            List<Protection> group = ProtectionsManager.groupOf(p);
            if (group.size() > 1) {
                ProtectionsManager.syncAllowed(group);
                if (player != null) PrivateMessage(player, PROT_EXTENDED);
            } else if (player != null) {
                PrivateMessage(player, NEW_PROT_CONFIRM);
            }
        }
    }

    // One click can place with the main hand or the off hand: keep one entry per position
    private static void addPending(PendingProtection pending) {
        for (PendingProtection other : pendingProtections) {
            if (other.pos().equals(pending.pos()) && other.dimension().equals(pending.dimension())) return;
        }
        pendingProtections.add(pending);
    }

    // Returns true if bypass is now on
    public static boolean toggleBypass(UUID playerId) {
        if (bypassing.remove(playerId)) {
            return false;
        }
        bypassing.add(playerId);
        return true;
    }

    private static boolean isBypassing(Player player) {
        return bypassing.contains(player.getUUID());
    }

    // Player left: forget what was tracked for them
    public static void onDisconnect(UUID playerId) {
        playerInProtections.remove(playerId);
        lastDenyMessage.remove(playerId);
        bypassing.remove(playerId);
    }

    // Server stopped: forget everything
    public static void clear() {
        playerInProtections.clear();
        lastDenyMessage.clear();
        pendingProtections.clear();
        bypassing.clear();
    }

    private static void message(Player player, String message) {
        if (player instanceof ServerPlayer serverPlayer) {
            PrivateMessage(serverPlayer, message);
        }
    }

    // Tells the player why something was blocked, without repeating the same message right away
    private static void deny(Player player, String message) {
        long now = System.nanoTime() / 1_000_000L;
        DenyMessage last = lastDenyMessage.get(player.getUUID());
        if (last != null && last.message().equals(message) && now - last.timeMs() < DENY_MESSAGE_COOLDOWN_MS) {
            return;
        }

        lastDenyMessage.put(player.getUUID(), new DenyMessage(message, now));
        message(player, message);
    }
}
