package dev.eventide.client.gui.menu;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.Config;
import dev.eventide.client.Draw;
import dev.eventide.client.EventideClient;
import dev.eventide.client.compat.CompatScreen;
import dev.eventide.client.gui.HudEditorScreen;
import dev.eventide.client.gui.MenuNameTag;
import dev.eventide.client.module.Module;
import dev.eventide.client.module.Module.Category;
import dev.eventide.client.module.Modules;
import dev.eventide.client.ui.Paint;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * What the screen menu ({@link MenuScreen}) and the floating one ({@link FloatingMenuScreen}) share: the
 * game side of {@link MenuView.Host}, the keyboard, and moving between the two without losing your place.
 */
public abstract class MenuHost extends CompatScreen implements MenuView.Host {
	protected final Screen parent;
	protected final SheetExtras extras;
	protected final MenuView view;

	protected MenuHost(Screen parent, Category start) {
		super(Component.literal("Eventide"));
		this.parent = parent;
		this.extras = new SheetExtras(this);
		this.view = new MenuView(this, start);
	}

	public MenuView view() {
		return view;
	}

	/** Whether the menu should float in the world (Theme > Menu display, and only with a world to float in). */
	public static boolean floatingNow() {
		return Modules.THEME_MENU_MODE.is("Floating") && Minecraft.getInstance().level != null && !FloatingMenu.failed();
	}

	/** Opens the menu at {@code start} (all mods for null): on screen, or floating in the world as Theme > Menu display says. */
	public static void show(Screen parent, Category start) {
		if (floatingNow()) FloatingMenu.open(parent, start);
		else Compat.setScreen(new MenuScreen(parent, start));
	}

	@Override
	public String keyFor(Module module) {
		KeyMapping key = keyMapping(module);
		return key == null ? null : shortKey(key.getTranslatedKeyMessage().getString());
	}

	/** The key that works a module (Zoom's, Freelook's, the knife inspect), or null. */
	public static KeyMapping keyMapping(Module module) {
		if (module == Modules.ZOOM) return EventideClient.zoomKey;
		if (module == Modules.FREELOOK) return EventideClient.freelookKey;
		if (module == Modules.KNIVES) return EventideClient.inspectKey;
		if (module == Modules.THEME) return EventideClient.openSettingsKey;
		return null;
	}

	/** A key's name made short enough for a card's key badge: Left Alt is L-ALT. */
	static String shortKey(String name) {
		String upper = name.toUpperCase(Locale.ROOT);
		if (upper.startsWith("LEFT ")) return "L-" + upper.substring(5);
		if (upper.startsWith("RIGHT ")) return "R-" + upper.substring(6);
		return upper.length() > 6 ? upper.substring(0, 6) : upper;
	}

	@Override
	public String playerName() {
		return Minecraft.getInstance().getUser().getName();
	}

	@Override
	public void drawFace(Draw d, float x, float y, float size, float radius) {
		face(d, MenuNameTag.ownSkin(Minecraft.getInstance()), x, y, size, radius);
	}

	/** A player's head (with its hat layer) as a sharp, rounded picture. */
	public static void face(Draw d, PlayerSkin skin, float x, float y, float size, float radius) {
		Identifier texture = skin.body().texturePath();
		Paint.image(d, texture, x, y, x + size, y + size, radius, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, 0xFFFFFFFF, false);
		Paint.image(d, texture, x, y, x + size, y + size, radius, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, 0xFFFFFFFF, false);
	}

	@Override
	public boolean inWorld() {
		return Minecraft.getInstance().level != null;
	}

	@Override
	public void openHudEditor() {
		Compat.setScreen(new HudEditorScreen(this));
	}

	@Override
	public void back() {
		onClose();
	}

	@Override
	public void clickSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	@Override
	public SettingsSheet.Extras extras() {
		return extras;
	}

	@Override
	public void setFloating(boolean floating) {
		if (floating && Minecraft.getInstance().level == null) {
			view.notice("Floating mode needs a world to float in");
			return;
		}
		Modules.THEME_MENU_MODE.set(floating ? "Floating" : "Screen");
		MenuHost next;
		if (floating) {
			FloatingMenu.open(parent, view.category());
			next = FloatingMenu.host();
		} else {
			FloatingMenu.close();
			next = new MenuScreen(parent, view.category());
			Compat.setScreen(next);
		}
		next.view.follow(view);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return view.charTyped(event.codepointAsString());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (extras.pickingBind()) {
			extras.bindKey(event.key());
			return true;
		}
		boolean ctrl = (event.modifiers() & 2) != 0;
		boolean shift = (event.modifiers() & 1) != 0;
		if (view.keyPressed(event.key(), ctrl, shift)) return true;
		if (!view.typing() && EventideClient.openSettingsKey != null && EventideClient.openSettingsKey.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void onClose() {
		Config.save();
		Compat.setScreen(parent);
	}
}
