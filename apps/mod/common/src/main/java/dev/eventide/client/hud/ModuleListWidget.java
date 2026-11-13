package dev.eventide.client.hud;

import dev.eventide.client.Draw;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.gui.UiFont;
import dev.eventide.client.module.Module;
import dev.eventide.client.module.Modules;
import dev.eventide.client.ui.Anim;
import dev.eventide.client.ui.Ease;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;

/**
 * Active Modules: every mod you have on, longest name first, down the top right of the screen, each
 * as a glass chip with the mod's icon in the theme colour and its name, like the website's module
 * chips. A mod you turn on slides in from the edge, one you turn off slides back out, and the rest
 * move up or down to make room.
 */
final class ModuleListWidget extends HudWidget {
	private static final float CAP = Hud.CAP * 0.85f;
	private static final float CHIP_H = 2.55f * CAP;
	private static final float GAP = 0.6f;
	private static final float BAR = 1.4f;
	private static final float STEP = CHIP_H + GAP;
	private static final float PAD = 0.95f * CAP;
	private static final float ICON = 1.45f * CAP;
	private static final float ICON_GAP = 0.75f * CAP;
	private static final List<Module> SAMPLE = List.of(Modules.CROSSHAIR, Modules.KEYSTROKES, Modules.FREELOOK, Modules.ZOOM);
	private static final Set<Module> OUT_OF_GAME = Set.of(Modules.MENU_NAME, Modules.DISCORD, Modules.BORDERLESS,
			Modules.PROFILES, Modules.THEME);

	private final Map<String, RowState> states = new LinkedHashMap<>();
	private boolean primed = false;

	ModuleListWidget() {
		super("module_list", Modules.MODULE_LIST, Anchor.TOP_RIGHT);
	}

	private static float chipWidth(String name) {
		float icon = Modules.MODULE_LIST_ICONS.get() ? ICON + ICON_GAP : 0;
		return PAD + icon + Type.width(name, CAP, Weight.SEMIBOLD) + PAD * 1.15f + BAR;
	}

	/** Whether a mod that's on belongs in the list: not the menu and window ones, and HUD mods only if asked for. */
	private static boolean listed(Module module) {
		if (module == Modules.MODULE_LIST || !module.toggleable() || !module.enabled()) return false;
		if (OUT_OF_GAME.contains(module)) return false;
		return module.category != Module.Category.HUD || Modules.MODULE_LIST_HUD.get();
	}

	private static final Listing LISTING = new Listing(false);
	private static final Listing PREVIEW_LISTING = new Listing(true);

	private static List<Module> modules(boolean preview) {
		return (preview ? PREVIEW_LISTING : LISTING).get();
	}

	/**
	 * The listed mods, widest first. The HUD asks for them many times a frame (for the list's size and for laying out the widgets
	 * around it), so the sorted list is kept until a mod comes on or goes off, or something that changes the chips' widths does.
	 */
	private static final class Listing {
		private final boolean preview;
		private boolean[] listed = new boolean[0];
		private boolean icons;
		private boolean clean;
		private List<Module> list = null;

		Listing(boolean preview) {
			this.preview = preview;
		}

		List<Module> get() {
			List<Module> all = Modules.ALL;
			boolean icons = Modules.MODULE_LIST_ICONS.get();
			boolean clean = UiFont.clean();
			boolean same = list != null && listed.length == all.size() && this.icons == icons && this.clean == clean;
			if (!same) listed = new boolean[all.size()];
			for (int i = 0; i < all.size(); i++) {
				boolean now = listed(all.get(i));
				if (listed[i] != now) {
					listed[i] = now;
					same = false;
				}
			}
			if (same) return list;
			this.icons = icons;
			this.clean = clean;
			List<Module> made = new ArrayList<>();
			for (int i = 0; i < all.size(); i++) {
				if (listed[i]) made.add(all.get(i));
			}
			if (made.isEmpty() && preview) made.addAll(SAMPLE);
			made.sort(Comparator.comparingDouble((Module module) -> chipWidth(module.name)).reversed());
			list = made;
			return list;
		}
	}

	@Override
	public boolean visible(Minecraft mc) {
		return !modules(false).isEmpty();
	}

	@Override
	public int width(Minecraft mc) {
		float widest = 30;
		for (Module module : modules(true)) widest = Math.max(widest, chipWidth(module.name));
		return (int) Math.ceil(widest);
	}

	@Override
	public int height(Minecraft mc) {
		return (int) Math.ceil(Math.max(1, modules(true).size()) * STEP - GAP);
	}

	/** Brings each chip up to date: which mods came on (to slide in), went off (to slide out), or moved. */
	private void follow(List<Module> list, boolean quiet) {
		Set<String> present = new HashSet<>();
		for (int i = 0; i < list.size(); i++) {
			Module module = list.get(i);
			present.add(module.name);
			RowState state = states.get(module.name);
			if (state == null) {
				state = new RowState(i);
				states.put(module.name, state);
				if (quiet) state.in.snap(1);
			}
			state.in.set(1);
			state.slot.set(i);
			state.module = module;
		}
		Iterator<RowState> it = states.values().iterator();
		while (it.hasNext()) {
			RowState state = it.next();
			if (present.contains(state.module.name)) continue;
			state.in.set(0);
			if (state.in.get() < 0.01f) it.remove();
		}
	}

	@Override
	public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
		follow(modules(preview), !primed);
		primed = true;
		float right = x + width(mc);
		boolean shadow = textShadow();
		boolean glow = Modules.MODULE_LIST_GLOW.get();
		float strength = glassStrength();
		float hair = Hud.hairline(d);
		for (RowState state : states.values()) {
			float in = state.in.get();
			if (in <= 0.01f) continue;
			Module module = state.module;
			float slot = state.slot.get();
			float chipRight = right + (1 - in) * 12;
			float left = chipRight - chipWidth(module.name);
			float top = y + slot * STEP;
			float r = radius(CHIP_H / 2f);
			float old = Paint.opacity(Paint.opacity() * in);
			if (strength > 0.01f) {
				float glass = Paint.opacity(Paint.opacity() * Math.min(1, strength * 1.15f));
				Paint.rect(d, left, top, chipRight, top + CHIP_H, Math.min(r, 2), 0xC80B0817);
				Paint.opacity(glass);
			}
			int accent = Accent.get(slot * 0.035f);
			float tx = left + PAD;
			if (Modules.MODULE_LIST_ICONS.get()) {
				Paint.icon(d, module.icon() != null ? module.icon() : "list", tx, top + (CHIP_H - ICON) / 2f, ICON, accent);
				tx += ICON + ICON_GAP;
			}
			Hud.text(d, module.name, tx, top + (CHIP_H - CAP) / 2f, CAP, Weight.SEMIBOLD, Accent.lighten(accent, 0.55f), shadow);
			float bx = chipRight - BAR;
			if (glow) Paint.shadow(d, bx, top + 1, chipRight, top + CHIP_H - 1, BAR / 2f, 0, 0, 5, 0, Paint.fade(accent, 0.7f));
			Paint.rect(d, bx, top + 1, chipRight, top + CHIP_H - 1, BAR / 2f, accent);
			Paint.opacity(old);
		}
	}

	/** One chip's animation state. */
	private static final class RowState {
		final Anim in = new Anim(0, 380, Ease.OUT);
		final Anim slot;
		Module module;

		RowState(int index) {
			slot = new Anim(index, 340, Ease.OUT);
		}
	}
}
