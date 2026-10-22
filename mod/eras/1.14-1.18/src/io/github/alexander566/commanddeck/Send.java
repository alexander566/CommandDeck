package io.github.alexander566.commanddeck;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

final class Send {

	/** Sends like typing in chat; false when not in a world. */
	static boolean send(String command) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		player.chat(command);
		return true;
	}
}
