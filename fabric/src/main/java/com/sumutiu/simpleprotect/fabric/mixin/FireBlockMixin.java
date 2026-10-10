package com.sumutiu.simpleprotect.fabric.mixin;

import com.sumutiu.simpleprotect.config.SimpleProtectConfig;
import com.sumutiu.simpleprotect.storage.ProtectionsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fire doesn't burn away blocks inside protections (fire spreading from outside, lava nearby, lightning)
// Fabric only: NeoForge adds a Direction to checkBurnOut, so it has its own copy of this mixin.
@SuppressWarnings("unused") // Applied by Mixin (listed in simpleprotect.fabric.mixins.json), never called from code
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void simpleprotect$noBurningInProtections(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
        if (SimpleProtectConfig.getProtectFromFireSpread()
                && ProtectionsManager.hasAny()
                && ProtectionsManager.isProtected(pos, ProtectionsManager.dimensionId(level))) {
            ci.cancel();
        }
    }
}
