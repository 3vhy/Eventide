package dev.eventide.client.gui;

import dev.eventide.client.Config;
import dev.eventide.client.EventideClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Where you last played, for the main menu's Continue card: the world or server, its name and icon and when.
 * It's noted each time you join one and kept in the config.
 */
public final class LastPlayed {
	private static byte[] iconBytes = null;
	private static String iconFor = null;

	private LastPlayed() {
	}

	/** Notes the world or server just joined. */
	public static void record(Minecraft mc) {
		try {
			Config.LastPlayed last = new Config.LastPlayed();
			MinecraftServer local = mc.getSingleplayerServer();
			if (local != null) {
				Path folder = local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
				last.kind = "world";
				last.name = local.getWorldData().getLevelName();
				last.target = folder.getFileName().toString();
				Path icon = folder.resolve("icon.png");
				if (Files.isRegularFile(icon) && Files.size(icon) < 256 * 1024) last.icon = Base64.getEncoder().encodeToString(Files.readAllBytes(icon));
			} else {
				ServerData server = mc.getCurrentServer();
				if (server == null || server.isLan()) return;
				last.kind = "server";
				last.name = server.name == null || server.name.isBlank() ? server.ip : server.name;
				last.target = server.ip;
				byte[] icon = server.getIconBytes();
				if (icon != null) last.icon = Base64.getEncoder().encodeToString(icon);
			}
			last.time = System.currentTimeMillis();
			Config.get().lastPlayed = last;
			Config.saveSoon();
		} catch (Exception e) {
			EventideClient.LOG.warn("Couldn't note where you're playing for the Continue card", e);
		}
	}

	/** Where you last played, or null. */
	public static Config.LastPlayed get() {
		Config.LastPlayed last = Config.get().lastPlayed;
		return last == null || last.target == null || last.target.isBlank() ? null : last;
	}

	/** Its icon's PNG bytes, decoded once, or null. */
	public static byte[] icon(Config.LastPlayed last) {
		String key = last.kind + ":" + last.target + ":" + last.time;
		if (!key.equals(iconFor)) {
			iconFor = key;
			try {
				iconBytes = last.icon == null || last.icon.isEmpty() ? null : Base64.getDecoder().decode(last.icon);
			} catch (IllegalArgumentException e) {
				iconBytes = null;
			}
		}
		return iconBytes;
	}

	/** "just now", "2m ago", "3h ago", "yesterday" or "5d ago". */
	public static String ago(long time) {
		long minutes = Math.max(0, (System.currentTimeMillis() - time) / 60000);
		if (minutes < 1) return "just now";
		if (minutes < 60) return minutes + "m ago";
		long hours = minutes / 60;
		if (hours < 24) return hours + "h ago";
		long days = hours / 24;
		return days == 1 ? "yesterday" : days + "d ago";
	}

	/** Jumps back in: loads the world or connects to the server, coming back to {@code from} if that fails. */
	public static void resume(Screen from) {
		Config.LastPlayed last = get();
		if (last == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (last.kind.equals("server")) {
			ServerData server = new ServerData(last.name, last.target, ServerData.Type.OTHER);
			ConnectScreen.startConnecting(from, mc, ServerAddress.parseString(last.target), server, false, null);
		} else {
			mc.createWorldOpenFlows().openWorld(last.target, () -> mc.setScreen(from));
		}
	}
}
