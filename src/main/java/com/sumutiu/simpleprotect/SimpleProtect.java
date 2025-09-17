package com.sumutiu.simpleprotect;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class SimpleProtect implements ModInitializer {
	public static final String MODID = "simpleprotect";

	@Override
	public void onInitialize() {
		// Init JSON store
		ProtectionsManager.init();

		// Register events + commands
		EventHandlers.register();
		ProtectionCommands.register();

		// Tick loop for enter/leave detection
		ServerTickEvents.START_SERVER_TICK.register(server -> EventHandlers.onServerTick(server));
	}
}
