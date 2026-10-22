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
		if (command.startsWith("/")) {
			player.commandSigned(command.substring(1), null);
		} else {
			player.chatSigned(command, null);
		}
		return true;
	}
}
