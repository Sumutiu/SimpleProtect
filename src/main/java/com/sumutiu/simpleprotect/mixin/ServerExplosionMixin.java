package com.sumutiu.simpleprotect.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sumutiu.simpleprotect.config.SimpleProtectConfig;
import com.sumutiu.simpleprotect.storage.Protection;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

// Explosions (TNT, creepers, beds, end crystals...) don't destroy blocks inside protections.
// The list is changed where explode() uses it, so it also works with mods that build the list themselves (Lithium).
@SuppressWarnings("unused") // Applied by Mixin (listed in simpleprotect.mixins.json), never called from code
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @ModifyExpressionValue(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;calculateExplodedPositions()Ljava/util/List;"))
    private List<BlockPos> simpleprotect$keepProtectedBlocks(List<BlockPos> blocks) {
        if (blocks.isEmpty() || !SimpleProtectConfig.getProtectFromExplosions() || !ProtectionsManager.hasAny()) return blocks;

        // Only the protections near the explosion (it can destroy a lot of blocks)
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        String dim = ProtectionsManager.dimensionId(this.level);
        List<Protection> nearby = ProtectionsManager.protectionsOverlapping(dim, minX, maxX, minZ, maxZ);
        if (nearby.isEmpty()) return blocks;

        List<BlockPos> kept = new ObjectArrayList<>(blocks.size());
        for (BlockPos pos : blocks) {
            if (!ProtectionsManager.isInAny(nearby, pos, dim)) {
                kept.add(pos);
            }
        }
        return kept;
    }
}
