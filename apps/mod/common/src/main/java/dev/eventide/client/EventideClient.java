package dev.eventide.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.discord.DiscordPresence;
import dev.eventide.client.gui.MenuNameTag;
import dev.eventide.client.gui.PauseMenuButton;
import dev.eventide.client.gui.menu.MenuHost;
import dev.eventide.client.gui.menu.MenuScreen;
import dev.eventide.client.hud.Hotbar;
import dev.eventide.client.hud.HudRenderer;
import dev.eventide.client.hud.HudWidgets;
import dev.eventide.client.hud.ScoreboardWidget;
import dev.eventide.client.module.Knives;
import dev.eventide.client.module.Borderless;
import dev.eventide.client.module.Combo;
import dev.eventide.client.module.ConfirmDisconnect;
import dev.eventide.client.module.Crosshair;
import dev.eventide.client.module.CrystalOptimizer;
import dev.eventide.client.module.Freelook;
import dev.eventide.client.module.Modules;
import dev.eventide.client.module.PopTracker;
import dev.eventide.client.module.Target;
import dev.eventide.client.module.TpsTracker;
import dev.eventide.client.module.Zoom;
import dev.eventide.client.music.MusicBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Eventide, inside Minecraft. */
public final class EventideClient implements ClientModInitializer {
	public static final String MOD_ID = "eventide";
	public static final Logger LOG = LoggerFactory.getLogger("Eventide");

	public static KeyMapping openSettingsKey;
	public static KeyMapping zoomKey;
	public static KeyMapping freelookKey;
	public static KeyMapping inspectKey;

	private static String version = "?";
	private static String minecraftVersion = "";

	@Override
	public void onInitializeClient() {
		version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		int plus = version.indexOf('+');
		if (plus > 0) version = version.substring(0, plus);
		minecraftVersion = FabricLoader.getInstance().getModContainer("minecraft")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");

		Migration.run();
		Config.load();
		DiscordPresence.start();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openSettingsKey = Compat.registerKey(new KeyMapping("key.eventide.open_settings", InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		zoomKey = Compat.registerKey(new KeyMapping("key.eventide.zoom", InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_C, category));
		freelookKey = Compat.registerKey(new KeyMapping("key.eventide.freelook", InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_LEFT_ALT, category));
		inspectKey = Compat.registerKey(new KeyMapping("key.eventide.inspect", InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_Y, category));

		Compat.registerHud(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), HudRenderer::render);
		Compat.replaceHud(VanillaHudElements.CROSSHAIR, Crosshair::draw);
		Compat.replaceHud(VanillaHudElements.HOTBAR, Hotbar::draw);
		Compat.replaceHud(VanillaHudElements.SCOREBOARD, (d, original) -> ((ScoreboardWidget) HudWidgets.SCOREBOARD).drawInPlace(d));
		ConfirmDisconnect.install();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			WindowBranding.apply();
			FreezeWatch.start();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			Zoom.restore(client);
			Freelook.stop(client);
			DiscordPresence.stop();
			MusicBridge.stop();
			Config.save();
		});
		ClientTickEvents.END_CLIENT_TICK.register(EventideClient::tick);
		dev.eventide.client.module.GroundFx.init();
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			PopTracker.reset();
			TpsTracker.reset();
			Zoom.restore(client);
			Freelook.stop(client);
		});
		AttackEntityCallback.EVENT.register(CrystalOptimizer::onAttack);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) {
				Target.onAttack(entity);
				Combo.onAttack(entity);
				dev.eventide.client.module.KillEffects.onAttack(entity);
				dev.eventide.client.module.HitGlow.onAttack(entity);
			}
			return InteractionResult.PASS;
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof PauseScreen) PauseMenuButton.add(client, screen);
			if (screen instanceof net.minecraft.client.gui.screens.ChatScreen) {
				net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((shown, event) ->
						!(event.button() == 0 && HudWidgets.musicClick(event.x(), event.y())));
			}
			if (screen instanceof net.minecraft.client.gui.screens.TitleScreen || screen instanceof PauseScreen) {
				net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.afterKeyPress(screen).register((shown, event) -> {
					if (openSettingsKey != null && openSettingsKey.matches(event) && client.screen == shown) {
						client.setScreen(dev.eventide.client.gui.menu.MenuHost.fromKey(shown));
					}
				});
			}
			if (MenuNameTag.shows(screen)) Compat.afterScreen(screen, d -> MenuNameTag.draw(d, screen));
		});

		LOG.info("Eventide {} loaded on Minecraft {}", version, minecraftVersion);
	}

	private static void tick(Minecraft mc) {
		FreezeWatch.tick();
		dev.eventide.client.module.EventideBadge.tick(mc);
		while (openSettingsKey.consumeClick()) {
			if (mc.screen == null) mc.setScreen(MenuHost.fromKey(null));
		}
		while (inspectKey.consumeClick()) Knives.inspect(mc);
		Knives.tick(mc);
		if (Borderless.needsReapply() && mc.getWindow().isFullscreen()) {
			((dev.eventide.client.mixin.WindowAccessor) (Object) mc.getWindow()).eventide$setMode();
		}
		Modules.tick(mc);
		WindowBranding.tick();
		DiscordPresence.tick(mc);
		Config.tick();
	}

	public static String version() {
		return version;
	}

	public static String minecraftVersion() {
		return minecraftVersion;
	}
}
