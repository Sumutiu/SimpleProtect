package com.sumutiu.simpleprotect.neoforge;

import com.sumutiu.simpleprotect.SimpleProtect;
import com.sumutiu.simpleprotect.commands.ProtectionCommands;
import com.sumutiu.simpleprotect.util.EventHandlers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entrypoint (see neoforge.mods.toml): passes the NeoForge events to the common code.
 * Only NeoForge creates it, by finding @Mod, so IntelliJ thinks it is never used.
 * The protection checks listen with the highest priority, so the other mods never see what a protection stops.
 */
@SuppressWarnings("unused")
@Mod(SimpleProtect.MOD_ID)
public class SimpleProtectNeoForge {

    public SimpleProtectNeoForge(ModContainer container) {
        SimpleProtect.init(container.getModInfo().getVersion().toString());

        IEventBus events = NeoForge.EVENT_BUS;

        events.addListener(ServerStartedEvent.class, event -> SimpleProtect.onServerStarted(event.getServer()));

        events.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                SimpleProtect.onPlayerJoin(player);
            }
        });

        events.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                SimpleProtect.onPlayerLeave(player);
            }
        });

        events.addListener(ServerTickEvent.Pre.class, event -> SimpleProtect.onServerTick(event.getServer()));

        events.addListener(ServerStoppedEvent.class, _ -> SimpleProtect.onServerStopped());

        // --- block break prevention ---
        events.addListener(EventPriority.HIGHEST, BreakBlockEvent.class, event -> {
            if (event.getLevel() instanceof Level level && !EventHandlers.beforeBlockBreak(level, event.getPlayer(), event.getPos())) {
                event.setCanceled(true);
                // The check only runs on the server, so tell the client the block is still there
                event.setNotifyClient(true);
            }
        });

        // --- protection removal: the protection block was broken ---
        // Lowest priority: runs after the other mods, and not at all when one of them cancelled the break
        events.addListener(EventPriority.LOWEST, BreakBlockEvent.class, event -> {
            if (event.getLevel() instanceof Level level) {
                EventHandlers.queueAfterBlockBreak(level, event.getPlayer(), event.getPos());
            }
        });

        // --- block use ---
        events.addListener(EventPriority.HIGHEST, PlayerInteractEvent.RightClickBlock.class, event -> {
            InteractionResult result = EventHandlers.onUseBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });

        // --- item use (buckets, lily pads) ---
        events.addListener(EventPriority.HIGHEST, PlayerInteractEvent.RightClickItem.class, event -> {
            InteractionResult result = EventHandlers.onUseItem(event.getEntity(), event.getLevel(), event.getHand());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });

        // --- entities: hitting and using them (item frames, armor stands, animals, villagers, vehicles...) ---
        events.addListener(EventPriority.HIGHEST, AttackEntityEvent.class, event -> {
            if (EventHandlers.onEntityInteraction(event.getEntity(), event.getEntity().level(), event.getTarget()) != InteractionResult.PASS) {
                event.setCanceled(true);
            }
        });

        events.addListener(EventPriority.HIGHEST, PlayerInteractEvent.EntityInteract.class, event -> {
            InteractionResult result = EventHandlers.onEntityInteraction(event.getEntity(), event.getLevel(), event.getTarget());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });

        // --- entities: damage a player causes from a distance (arrows, tridents, TNT they lit...) and explosions.
        // EntityMixin covers the entities this event doesn't (armor stands, item frames, boats...) ---
        events.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, event -> {
            if (EventHandlers.blocksDamage(event.getEntity(), event.getSource())) {
                event.setCanceled(true);
            }
        });

        events.addListener(RegisterCommandsEvent.class, event -> ProtectionCommands.register(event.getDispatcher()));
    }
}
