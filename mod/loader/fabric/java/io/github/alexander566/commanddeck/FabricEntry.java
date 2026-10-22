package io.github.alexander566.commanddeck;

import net.fabricmc.api.ClientModInitializer;

public final class FabricEntry implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		CommandDeck.start();
	}
}
