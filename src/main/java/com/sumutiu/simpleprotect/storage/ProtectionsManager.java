package com.sumutiu.simpleprotect.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.sumutiu.simpleprotect.util.MessagesHelper.*;
import static com.sumutiu.simpleprotect.SimpleProtect.FILE;

public class ProtectionsManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Protection>>() {}.getType();

    // Map<idString, Protection>
    private static final Map<String, Protection> protections = new ConcurrentHashMap<>();

    // Dimension ids as text ("minecraft:overworld"), so the hot paths (liquids, fire) don't build the text every time
    private static final Map<ResourceKey<Level>, String> dimensionIds = new ConcurrentHashMap<>();

    public static String dimensionId(Level level) {
        return dimensionIds.computeIfAbsent(level.dimension(), key -> key.identifier().toString());
    }

    // Returns false if the file can't be read. The mod then stays off (players can't join),
    // so the file is never overwritten and no protection is lost.
    public static synchronized boolean load() {
        protections.clear();

        List<Protection> list;
        try {
            list = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), LIST_TYPE);
        } catch (IOException | JsonParseException e) {
            Logger(2, String.format(PROT_FILE_READ_FAILED, FILE, e.getMessage()));
            return false;
        }

        if (list == null) {
            return true; // empty file
        }

        int skipped = 0;
        for (Protection p : list) {
            if (!repair(p)) {
                skipped++;
                continue;
            }

            // The same protection twice (edited by hand): keep one, with the allowed players of both
            Protection existing = protections.putIfAbsent(p.idString(), p);
            if (existing != null) {
                for (UUID player : p.allowed) {
                    existing.addAllowed(player, p.allowedNames.getOrDefault(player, existing.allowedNames.get(player)));
                }
            }
        }

        if (skipped > 0) {
            // The next save leaves the broken entries out, so keep a copy of the file as it is now
            Path backup = FILE.resolveSibling(FILE.getFileName() + ".backup-" + System.currentTimeMillis());
            try {
                Files.copy(FILE, backup);
                Logger(1, String.format(PROT_ENTRIES_SKIPPED, skipped, backup.getFileName()));
                save(); // write the cleaned file now, so the next start doesn't make another copy
            } catch (IOException e) {
                // Without a copy, don't risk losing them: the mod stays off
                Logger(2, String.format(PROT_BACKUP_FAILED, skipped, e.getMessage()));
                protections.clear();
                return false;
            }
        }
        return true;
    }

    // Fills in what can be filled in; returns false if the entry is unusable (no owner or dimension)
    private static boolean repair(Protection p) {
        if (p == null || p.owner == null || p.dimension == null) {
            return false;
        }
        if (p.ownerName == null) {
            p.ownerName = p.owner.toString();
        }
        if (p.allowed == null) {
            p.allowed = new ArrayList<>();
        }
        p.allowed.removeIf(Objects::isNull);
        if (p.allowedNames == null) {
            p.allowedNames = new HashMap<>();
        }
        p.allowedNames.values().removeIf(Objects::isNull);
        return true;
    }

    public static synchronized boolean save() {
        Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            // Serialize to a String first: Gson wraps write errors in an unchecked JsonIOException.
            // Write a temporary file and then replace the real one, so a crash can't leave it half written.
            Files.writeString(tmp, GSON.toJson(new ArrayList<>(protections.values())), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            Logger(2, String.format(PROT_FILE_SAVE_FAILED, ex.getMessage()));
            return false;
        }
    }

    // Server stopped: forget everything
    public static synchronized void clear() {
        protections.clear();
    }

    public static synchronized void addProtection(Protection p) {
        protections.put(p.idString(), p);
        save();
    }

    public static synchronized void removeProtection(Protection p) {
        protections.remove(p.idString());
        save();
    }

    public static synchronized void removeProtections(List<Protection> list) {
        for (Protection p : list) {
            protections.remove(p.idString());
        }
        save();
    }

    public static boolean hasAny() {
        return !protections.isEmpty();
    }

    // The protection whose protection block is at this position, if any
    public static Optional<Protection> findCenterAt(BlockPos pos, String dimension) {
        for (Protection p : protections.values()) {
            if (p.isCenter(pos, dimension)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public static Optional<Protection> findByOwnerAt(UUID owner, BlockPos pos, String dimension) {
        return protections.values().stream()
                .filter(p -> p.owner.equals(owner))
                .filter(p -> p.contains(pos, dimension))
                .min(Comparator.comparingInt(p ->
                        Math.abs(p.x - pos.getX()) + Math.abs(p.z - pos.getZ())
                ));
    }

    // The protection here whose block is the closest, if any
    public static Optional<Protection> findAt(BlockPos pos, String dimension) {
        return protections.values().stream()
                .filter(p -> p.contains(pos, dimension))
                .min(Comparator.comparingInt(p ->
                        Math.abs(p.x - pos.getX()) + Math.abs(p.z - pos.getZ())
                ));
    }

    // All protection blocks of a player, sorted by dimension and position
    public static List<Protection> ownedBy(UUID owner) {
        List<Protection> out = new ArrayList<>();
        for (Protection p : protections.values()) {
            if (p.owner.equals(owner)) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparing((Protection p) -> p.dimension).thenComparingInt(p -> p.x).thenComparingInt(p -> p.z));
        return out;
    }

    // The distance (on X or Z, whichever is bigger) to the player's closest protection block in this dimension
    public static int distanceToOwnBlock(UUID owner, BlockPos pos, String dimension) {
        int closest = Integer.MAX_VALUE;
        for (Protection p : protections.values()) {
            if (p.owner.equals(owner) && p.dimension.equals(dimension)) {
                int distance = Math.max(Math.abs(p.x - pos.getX()), Math.abs(p.z - pos.getZ()));
                closest = Math.min(closest, distance);
            }
        }
        return closest;
    }

    // The protections of the same owner that touch or overlap this one, directly or through others:
    // together they are one protected area, with the same allowed players
    public static List<Protection> groupOf(Protection start) {
        int reach = 2 * Protection.radius() + 1;

        // The owner's other protections in this dimension
        List<Protection> candidates = new ArrayList<>();
        for (Protection p : protections.values()) {
            if (p != start && p.owner.equals(start.owner) && p.dimension.equals(start.dimension)) {
                candidates.add(p);
            }
        }

        List<Protection> group = new ArrayList<>();
        group.add(start);
        for (int i = 0; i < group.size() && !candidates.isEmpty(); i++) {
            Protection current = group.get(i);
            for (Iterator<Protection> it = candidates.iterator(); it.hasNext(); ) {
                Protection p = it.next();
                if (Math.abs(p.x - current.x) <= reach && Math.abs(p.z - current.z) <= reach) {
                    group.add(p);
                    it.remove();
                }
            }
        }
        return group;
    }

    // The protections in this dimension that cover any part of this area (for explosions)
    public static List<Protection> protectionsOverlapping(String dimension, int minX, int maxX, int minZ, int maxZ) {
        int radius = Protection.radius();
        List<Protection> out = new ArrayList<>();
        for (Protection p : protections.values()) {
            if (p.dimension.equals(dimension)
                    && p.x + radius >= minX && p.x - radius <= maxX
                    && p.z + radius >= minZ && p.z - radius <= maxZ) {
                out.add(p);
            }
        }
        return out;
    }

    public static boolean isInAny(List<Protection> list, BlockPos pos, String dimension) {
        for (Protection p : list) {
            if (p.contains(pos, dimension)) return true;
        }
        return false;
    }

    // Gives every protection of the group the allowed players of all of them (after a new block joined it)
    public static synchronized void syncAllowed(List<Protection> group) {
        Map<UUID, String> all = new LinkedHashMap<>();
        for (Protection p : group) {
            for (UUID player : p.allowed) {
                String name = p.allowedNames.get(player);
                if (name != null || !all.containsKey(player)) {
                    all.put(player, name);
                }
            }
        }

        for (Protection p : group) {
            all.forEach(p::addAllowed);
        }
        save();
    }

    public static List<Protection> protectionsContaining(BlockPos pos, String dimension) {
        List<Protection> out = new ArrayList<>();

        for (Protection p : protections.values()) {
            if (p.contains(pos, dimension)) {
                out.add(p);
            }
        }

        return out;
    }

    // True if any protection covers this position
    public static boolean isProtected(BlockPos pos, String dimension) {
        for (Protection p : protections.values()) {
            if (p.contains(pos, dimension)) return true;
        }
        return false;
    }

    // The owners of the protections covering this position (empty if none)
    public static Set<UUID> ownersAt(BlockPos pos, String dimension) {
        Set<UUID> owners = new HashSet<>();
        for (Protection p : protections.values()) {
            if (p.contains(pos, dimension)) {
                owners.add(p.owner);
            }
        }
        return owners;
    }

    // True if something at 'from' (a piston, a dispenser, flowing liquid) may NOT change the block at 'to':
    // 'to' is protected by someone who doesn't also protect 'from'.
    public static boolean cannotAffect(BlockPos from, BlockPos to, String dimension) {
        if (protections.isEmpty()) return false;
        return !ownersAt(from, dimension).containsAll(ownersAt(to, dimension));
    }

    // True if a liquid may NOT flow into 'to' from the block next to it (flowing in this direction)
    public static boolean cannotFlowInto(LevelAccessor level, BlockPos to, Direction direction) {
        if (protections.isEmpty() || !(level instanceof Level world)) return false;

        // Most liquid flows nowhere near a protection: decide that without building any sets
        String dim = dimensionId(world);
        if (!isProtected(to, dim)) return false;

        return cannotAffect(to.relative(direction.getOpposite()), to, dim);
    }

    public static boolean isPlayerAllowedAt(UUID player, BlockPos pos, String dimension) {
        boolean protectedHere = false;

        for (Protection p : protections.values()) {
            if (!p.contains(pos, dimension)) continue;
            if (p.isOwnerOrAllowed(player)) return true;
            protectedHere = true;
        }

        return !protectedHere;
    }

    // True if a new protection centered here would overlap a protection of another player (in the same dimension)
    public static boolean overlapsOtherOwner(UUID owner, BlockPos pos, String dimension) {
        int r = Protection.radius() * 2;

        for (Protection p : protections.values()) {
            if (p.owner.equals(owner) || !p.dimension.equals(dimension)) continue;

            boolean overlapX = Math.abs(pos.getX() - p.x) <= r;
            boolean overlapZ = Math.abs(pos.getZ() - p.z) <= r;
            if (overlapX && overlapZ) return true;
        }

        return false;
    }

    // A player joined: keep the stored names up to date (players can change their name)
    public static synchronized void updateNames(UUID player, String name) {
        boolean changed = false;

        for (Protection p : protections.values()) {
            if (p.owner.equals(player) && !name.equals(p.ownerName)) {
                p.ownerName = name;
                changed = true;
            }
            if (p.allowed.contains(player) && !name.equals(p.allowedNames.get(player))) {
                p.allowedNames.put(player, name);
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }
}
