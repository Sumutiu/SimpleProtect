package com.sumutiu.simpleprotect.mixin;

import com.sumutiu.simpleprotect.util.EventHandlers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Every entity checks this before taking damage, also the ones the loaders' damage events don't cover
// (armor stands, item frames, paintings, boats, minecarts, end crystals). Inside a protection they can't be
// hurt by players who aren't allowed there (arrows, tridents, TNT they lit) or by explosions those players didn't cause.
@SuppressWarnings("unused") // Applied by Mixin (listed in simpleprotect.mixins.json), never called from code
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "isInvulnerableToBase", at = @At("RETURN"), cancellable = true)
    private void simpleprotect$protectEntities(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && EventHandlers.blocksDamage(simpleprotect$self(this), source)) {
            cir.setReturnValue(true);
        }
    }

    // This class is merged into Entity when the game loads, so "this" is the entity.
    // (IntelliJ only sees this class, and warns about casting "this" to Entity directly)
    @Unique
    private static Entity simpleprotect$self(Object mixin) {
        return (Entity) mixin;
    }
}
