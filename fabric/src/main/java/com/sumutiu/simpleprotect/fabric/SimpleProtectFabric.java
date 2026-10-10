package com.sumutiu.simpleprotect.fabric;

import com.sumutiu.simpleprotect.SimpleProtect;
import com.sumutiu.simpleprotect.commands.ProtectionCommands;
import com.sumutiu.simpleprotect.util.EventHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric entrypoint (see fabric.mod.json): passes the Fabric API events to the common code.
 * Only Fabric creates it, from the name in fabric.mod.json, so IntelliJ thinks it is never used.
 */
@SuppressWarnings("unused")
public class SimpleProtectFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        SimpleProtect.init(FabricLoader.getInstance()
                .getModContainer(SimpleProtect.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown"));

        ServerLifecycleEvents.SERVER_STARTED.register(SimpleProtect::onServerStarted);

        ServerPlayConnectionEvents.JOIN.register((handler, _, _) -> SimpleProtect.onPlayerJoin(handler.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, _) -> SimpleProtect.onPlayerLeave(handler.getPlayer()));

        ServerTickEvents.START_SERVER_TICK.register(SimpleProtect::onServerTick);

        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> SimpleProtect.onServerStopped());

        // --- block break prevention ---
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, _, _) -> EventHandlers.beforeBlockBreak(world, player, pos));

        // --- protection removal: the protection block was broken ---
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, _, _) -> EventHandlers.afterBlockBreak(world, player, pos));

        // --- block use ---
        UseBlockCallback.EVENT.register(EventHandlers::onUseBlock);

        // --- item use (buckets, lily pads) ---
        UseItemCallback.EVENT.register(EventHandlers::onUseItem);

        // --- entities: hitting and using them (item frames, armor stands, animals, villagers, vehicles...) ---
        AttackEntityCallback.EVENT.register((player, world, _, entity, _) -> EventHandlers.onEntityInteraction(player, world, entity));
        UseEntityCallback.EVENT.register((player, world, _, entity, _) -> EventHandlers.onEntityInteraction(player, world, entity));

        // --- entities: damage a player causes from a distance (arrows, tridents, TNT they lit...) and explosions.
        // EntityMixin covers the entities this event doesn't (armor stands, item frames, boats...) ---
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, _) -> !EventHandlers.blocksDamage(entity, source));

        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ProtectionCommands.register(dispatcher));
    }
}
