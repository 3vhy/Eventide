package dev.eventide.client.hud;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.Draw;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.gui.MenuNameTag;
import dev.eventide.client.gui.SafeItems;
import dev.eventide.client.gui.menu.MenuScreen;
import dev.eventide.client.hud.HudWidget.Anchor;
import dev.eventide.client.module.Combo;
import dev.eventide.client.module.JumpReset;
import dev.eventide.client.module.Module;
import dev.eventide.client.module.Modules;
import dev.eventide.client.module.PopTracker;
import dev.eventide.client.module.Target;
import dev.eventide.client.module.TpsTracker;
import dev.eventide.client.ui.Anim;
import dev.eventide.client.ui.Ease;
import dev.eventide.client.ui.Grad;
import dev.eventide.client.ui.Logo;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** All HUD widgets, in drawing order, in the website's violet glass. */
public final class HudWidgets {
	private HudWidgets() {
	}

	/** A pill's accent icon, with a soft shadow under it when there's little or no glass behind it. */
	private static void pillIcon(Draw d, String name, float x, float y, float size, boolean shadow) {
		if (shadow) Paint.icon(d, name, x + 0.6f, y + 0.6f, size, 0x99000000);
		Paint.icon(d, name, x, y, size, Pills.lavender());
	}

	/**
	 * A glass pill: an icon in the accent colour, the value in bold and a dim unit after it (and for some a tiny
	 * graph or bar). It stands alone or sticks to other pills into one bar (see {@link Pills}).
	 */
	private abstract static class StatWidget extends HudWidget {
		private static final float ICON = 8;
		private static final float VALUE = 5.6f;
		private static final float UNIT = 4.6f;
		private static final float PAD = 7.5f;

		StatWidget(String id, Module module, Anchor anchor) {
			super(id, module, anchor);
		}

		abstract String icon();

		abstract String label();

		abstract String value(Minecraft mc, boolean preview);

		int color(Minecraft mc) {
			return valueColor();
		}

		/** The widest the value gets, so the pill doesn't twitch as it changes. */
		String template() {
			return "00";
		}

		float extraWidth() {
			return 0;
		}

		void drawExtra(Draw d, float x, float cy) {
		}

		@Override
		public boolean pill() {
			return true;
		}

		private String unit() {
			return showLabel() ? label() : "";
		}

		private static float valueWidth(String value, String template) {
			return Math.max(Hud.width(value, VALUE, Weight.BOLD), Hud.width(template, VALUE, Weight.BOLD));
		}

		private float layout(String value) {
			String unit = unit();
			float w = PAD + ICON + 4 + valueWidth(value, template());
			if (!unit.isEmpty()) w += 2.4f + Hud.width(unit, VALUE, Weight.BOLD);
			if (extraWidth() > 0) w += 5 + extraWidth();
			return w + PAD;
		}

		@Override
		public int width(Minecraft mc) {
			return (int) Math.ceil(layout(value(mc, true)));
		}

		@Override
		public int height(Minecraft mc) {
			return Math.round(Hud.PILL_H);
		}

		@Override
		public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
			Pills.glass(d, x, y, x + width(mc), y + Hud.PILL_H, glassStrength());
			drawContent(d, x, y, mc, preview);
		}

		@Override
		public void drawContent(Draw d, int x, int y, Minecraft mc, boolean preview) {
			String value = value(mc, preview);
			boolean shadow = textShadow();
			float cx = x + PAD;
			float cy = y + Hud.PILL_H / 2f;
			pillIcon(d, icon(), cx, cy - ICON / 2f, ICON, shadow);
			cx += ICON + 4;
			float vw = valueWidth(value, template());
			Hud.text(d, value, cx, cy - VALUE / 2f, VALUE, Weight.BOLD, color(mc), shadow);
			cx += Hud.width(value, VALUE, Weight.BOLD);
			String unit = unit();
			if (!unit.isEmpty()) {
				cx += 2.4f;
				Hud.text(d, unit, cx, cy - VALUE / 2f, VALUE, Weight.BOLD, Paint.fade(color(mc), 0.92f), shadow);
				cx += Hud.width(unit, VALUE, Weight.BOLD);
			}
			cx += vw - Hud.width(value, VALUE, Weight.BOLD);
			if (extraWidth() > 0) drawExtra(d, cx + 5, cy);
		}
	}

	/** Your head and name (or Name Protect's name for you) in a pill, at the left end of the top bar. */
	public static final HudWidget PLAYER_NAME = new HudWidget("player_name", Modules.PLAYER_NAME, Anchor.TOP_BAR) {
		private static final float VALUE = 5.6f;
		private static final float PAD = 7.5f;
		private static final float FACE = 10.5f;

		private String name(Minecraft mc) {
			return dev.eventide.client.module.NameProtect.apply(mc.getUser().getName());
		}

		@Override
		public boolean pill() {
			return true;
		}

		@Override
		public int width(Minecraft mc) {
			return (int) Math.ceil(PAD + FACE + 4.5f + Hud.width(name(mc), VALUE, Weight.BOLD) + PAD);
		}

		@Override
		public int height(Minecraft mc) {
			return Math.round(Hud.PILL_H);
		}

		@Override
		public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
			Pills.glass(d, x, y, x + width(mc), y + Hud.PILL_H, glassStrength());
			drawContent(d, x, y, mc, preview);
		}

		@Override
		public void drawContent(Draw d, int x, int y, Minecraft mc, boolean preview) {
			float cy = y + Hud.PILL_H / 2f;
			float cx = x + PAD;
			dev.eventide.client.gui.menu.MenuHost.face(d, dev.eventide.client.gui.MenuNameTag.ownSkin(mc), cx, cy - FACE / 2f, FACE, FACE * 0.2f);
			cx += FACE + 4.5f;
			Hud.text(d, name(mc), cx, cy - VALUE / 2f, VALUE, Weight.BOLD, valueColor(), textShadow());
		}
	};

	public static final HudWidget FPS = new StatWidget("fps", Modules.FPS, Anchor.TOP_BAR) {
		private static final int SAMPLES = 24;
		private static final long EVERY_MS = 250;
		private final float[] history = new float[SAMPLES];
		private final float[] points = new float[SAMPLES * 2];
		private int filled = 0;
		private long lastSample = 0;

		@Override
		String icon() {
			return "monitor";
		}

		@Override
		String label() {
			return "FPS";
		}

		@Override
		String template() {
			return "000";
		}

		@Override
		String value(Minecraft mc, boolean preview) {
			return Integer.toString(mc.getFps());
		}

		@Override
		float extraWidth() {
			return Modules.FPS_GRAPH.get() ? 21 : 0;
		}

		/** The last few seconds as a row of thin bars, newest on the right, in the accent colour. */
		@Override
		void drawExtra(Draw d, float x, float cy) {
			long now = System.currentTimeMillis();
			if (now - lastSample >= EVERY_MS) {
				lastSample = now;
				System.arraycopy(history, 1, history, 0, SAMPLES - 1);
				history[SAMPLES - 1] = Minecraft.getInstance().getFps();
				filled = Math.min(SAMPLES, filled + 1);
			}
			int bars = 10;
			float max = 1;
			for (int i = SAMPLES - bars; i < SAMPLES; i++) max = Math.max(max, history[i]);
			float h = 6;
			int accent = Accent.get();
			for (int i = 0; i < bars; i++) {
				float v = history[SAMPLES - bars + i] / max;
				float bh = Math.max(0.8f, h * v);
				float bx = x + i * 2.1f;
				Paint.rect(d, bx, cy + h / 2f - bh, bx + 1.3f, cy + h / 2f, 0.4f, Paint.fade(accent, 0.45f + 0.55f * (i + 1) / bars));
			}
		}
	};

	public static final HudWidget CPS = new StatWidget("cps", Modules.CPS, Anchor.TOP_LEFT) {
		@Override
		String icon() {
			return "mouse-pointer-click";
		}

		@Override
		String label() {
			return "CPS";
		}

		@Override
		String template() {
			return "0 · 0";
		}

		@Override
		String value(Minecraft mc, boolean preview) {
			return CpsTracker.left() + " · " + CpsTracker.right();
		}
	};

	public static final HudWidget PING = new StatWidget("ping", Modules.PING, Anchor.TOP_LEFT) {
		@Override
		String icon() {
			return "wifi";
		}

		@Override
		String label() {
			return "ms";
		}

		private int latency(Minecraft mc) {
			ClientPacketListener connection = mc.getConnection();
			LocalPlayer player = mc.player;
			if (connection == null || player == null) return -1;
			PlayerInfo info = connection.getPlayerInfo(player.getUUID());
			return info == null ? -1 : info.getLatency();
		}

		@Override
		String value(Minecraft mc, boolean preview) {
			int latency = latency(mc);
			if (latency < 0) return preview ? "42" : "-";
			return Integer.toString(latency);
		}

		@Override
		int color(Minecraft mc) {
			int latency = latency(mc);
			if (!Modules.PING_COLOURS.get() || latency < 0) return valueColor();
			return Hud.quality(latency / 200.0);
		}

		@Override
		public boolean visible(Minecraft mc) {
			return mc.getSingleplayerServer() == null;
		}
	};

	public static final HudWidget TPS = new StatWidget("tps", Modules.TPS, Anchor.TOP_LEFT) {
		@Override
		String icon() {
			return "timer";
		}

		@Override
		String label() {
			return "TPS";
		}

		@Override
		String template() {
			return "00.0";
		}

		@Override
		String value(Minecraft mc, boolean preview) {
			double tps = TpsTracker.tps();
			if (tps < 0) return preview ? "20.0" : "-";
			return String.format(Locale.ROOT, "%.1f", tps);
		}

		@Override
		int color(Minecraft mc) {
			double tps = TpsTracker.tps();
			if (!Modules.TPS_COLOURS.get() || tps < 0) return valueColor();
			return Hud.quality((20 - tps) / 10.0);
		}
	};

	public static final HudWidget CLOCK = new StatWidget("clock", Modules.CLOCK, Anchor.TOP_BAR) {
		@Override
		String icon() {
			return "clock";
		}

		@Override
		String label() {
			return "";
		}

		@Override
		String value(Minecraft mc, boolean preview) {
			boolean seconds = Modules.CLOCK_SECONDS.get();
			String pattern = Modules.CLOCK_24H.get() ? (seconds ? "HH:mm:ss" : "HH:mm") : (seconds ? "h:mm:ss a" : "h:mm a");
			return LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
		}
	};

	public static final HudWidget COMBO = new StatWidget("combo", Modules.COMBO, Anchor.TOP_LEFT) {
		@Override
		String icon() {
			return "flame";
		}

		@Override
		String label() {
			return "Combo";
		}

		@Override
		String value(Minecraft mc, boolean preview) {
			int combo = Combo.count();
			return combo == 0 && preview ? "3" : Integer.toString(combo);
		}

		@Override
		public boolean visible(Minecraft mc) {
			return Combo.count() > 0;
		}
	};

	/** Coordinates as a pill: X, Y and Z with their letters in the accent colour, and which way you face. */
	public static final HudWidget COORDS = new HudWidget("coords", Modules.COORDS, Anchor.TOP_CENTER) {
		private static final float VALUE = 5.6f;
		private static final float LETTER = 4.6f;
		private static final float PAD = 7.5f;
		private static final String[] AXES = {"x", "y", "z"};

		private String[] values(Minecraft mc) {
			LocalPlayer player = mc.player;
			if (player == null) return new String[] {"-420", "120", "446", "N"};
			BlockPos pos = player.blockPosition();
			String facing = player.getDirection().getName();
			return new String[] {Integer.toString(pos.getX()), Integer.toString(pos.getY()), Integer.toString(pos.getZ()),
					facing.substring(0, 1).toUpperCase(Locale.ROOT)};
		}

		@Override
		public boolean pill() {
			return true;
		}

		@Override
		public int width(Minecraft mc) {
			String[] v = values(mc);
			float w = PAD + 8 + 4;
			for (int i = 0; i < 3; i++) w += Hud.width(AXES[i], LETTER, Weight.BOLD) + 2.4f + Hud.width(v[i], VALUE, Weight.BOLD) + (i < 2 ? 6 : 0);
			if (Modules.COORDS_FACING.get()) w += 9 + Hud.width("W", VALUE, Weight.SEMIBOLD);
			return (int) Math.ceil(w + PAD);
		}

		@Override
		public int height(Minecraft mc) {
			return Math.round(Hud.PILL_H);
		}

		@Override
		public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
			Pills.glass(d, x, y, x + width(mc), y + Hud.PILL_H, glassStrength());
			drawContent(d, x, y, mc, preview);
		}

		@Override
		public void drawContent(Draw d, int x, int y, Minecraft mc, boolean preview) {
			String[] v = values(mc);
			boolean shadow = textShadow();
			float cy = y + Hud.PILL_H / 2f;
			float cx = x + PAD;
			pillIcon(d, "navigation", cx, cy - 4, 8, shadow);
			cx += 8 + 4;
			for (int i = 0; i < 3; i++) {
				cx += Hud.text(d, AXES[i], cx, cy - VALUE / 2f + (VALUE - LETTER), LETTER, Weight.BOLD, Pills.lavender(), shadow) + 2.4f;
				cx += Hud.text(d, v[i], cx, cy - VALUE / 2f, VALUE, Weight.BOLD, valueColor(), shadow) + (i < 2 ? 6 : 0);
			}
			if (Modules.COORDS_FACING.get()) {
				float fx = x + width(mc) - PAD - Hud.width("W", VALUE, Weight.SEMIBOLD);
				Pills.divider(d, fx - 4.5f, y, y + Hud.PILL_H);
				Hud.text(d, v[3], fx, cy - VALUE / 2f, VALUE, Weight.SEMIBOLD, Tokens.TEXT_2, shadow);
			}
		}
	};

	/** How much of the game's memory is in use, with a small bar. */
	public static final HudWidget MEMORY = new StatWidget("memory", Modules.MEMORY, Anchor.TOP_LEFT) {
		@Override
		String icon() {
			return "memory-stick";
		}

		@Override
		String label() {
			return "";
		}

		@Override
		String template() {
			return "00%";
		}

		private static float used() {
			Runtime runtime = Runtime.getRuntime();
			return (runtime.totalMemory() - runtime.freeMemory()) / (float) runtime.maxMemory();
		}

		@Override
		String value(Minecraft mc, boolean preview) {
			return Math.round(used() * 100) + "%";
		}

		@Override
		float extraWidth() {
			return 16;
		}

		@Override
		void drawExtra(Draw d, float x, float cy) {
			Hud.bar(d, x, cy - 1.1f, x + 16, 2.2f, used(), null);
		}
	};

	/** The server you're on (its address), or Singleplayer, or Local for a LAN world. */
	public static final HudWidget SERVER = new StatWidget("server", Modules.SERVER, Anchor.TOP_BAR) {
		@Override
		String icon() {
			return "cloud";
		}

		@Override
		String label() {
			return "";
		}

		@Override
		String template() {
			return "";
		}

		@Override
		String value(Minecraft mc, boolean preview) {
			if (mc.getSingleplayerServer() != null) return "Singleplayer";
			var server = mc.getCurrentServer();
			if (server == null) return preview ? "play.server.net" : "Offline";
			return server.isLan() ? "Local" : Type.fit(server.ip, 110, 5.6f, Weight.BOLD);
		}
	};

	/**
	 * The website's keystrokes: W, A, S and D on a glass block with the mouse buttons and their CPS
	 * under them. A key lights up in the accent gradient with a glow and dips a little while held.
	 */
	public static final HudWidget KEYSTROKES = new HudWidget("keystrokes", Modules.KEYSTROKES, Anchor.TOP_LEFT) {
		private static final float S = 0.44f;
		private static final float KW = 45 * S;
		private static final float KH = 40 * S;
		private static final float MH = 36 * S;
		private static final float SH = 22 * S;
		private static final float GAP = 5 * S;
		private static final float PAD = 10 * S;
		private static final float R = 9 * S;
		private static final float FADE_SECONDS = 0.08f;
		private final Map<KeyMapping, float[]> pressed = new IdentityHashMap<>();

		private float inner() {
			return 3 * KW + 2 * GAP;
		}

		@Override
		public int width(Minecraft mc) {
			return (int) Math.ceil(inner() + 2 * PAD);
		}

		@Override
		public int height(Minecraft mc) {
			float h = 2 * PAD + 2 * KH + GAP;
			if (Modules.KEYSTROKES_MOUSE.get()) h += GAP + MH;
			if (Modules.KEYSTROKES_SPACE.get()) h += GAP + SH;
			return (int) Math.ceil(h);
		}

		@Override
		public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
			var o = mc.options;
			glass(d, x, y, width(mc), height(mc), 14 * S);
			float left = x + PAD;
			float top = y + PAD;
			key(d, left + KW + GAP, top, KW, KH, "W", null, o.keyUp);
			float row = top + KH + GAP;
			key(d, left, row, KW, KH, "A", null, o.keyLeft);
			key(d, left + KW + GAP, row, KW, KH, "S", null, o.keyDown);
			key(d, left + 2 * (KW + GAP), row, KW, KH, "D", null, o.keyRight);
			row += KH + GAP;
			if (Modules.KEYSTROKES_MOUSE.get()) {
				float half = (inner() - GAP) / 2f;
				key(d, left, row, half, MH, "LMB", CpsTracker.left() + " CPS", o.keyAttack);
				key(d, left + half + GAP, row, half, MH, "RMB", CpsTracker.right() + " CPS", o.keyUse);
				row += MH + GAP;
			}
			if (Modules.KEYSTROKES_SPACE.get()) {
				float t = held(o.keyJump);
				keyBack(d, left, row, inner(), SH, t);
				float dash = inner() * 0.4f;
				float mid = row + SH / 2f;
				Paint.rect(d, left + (inner() - dash) / 2f, mid - 0.6f, left + (inner() + dash) / 2f, mid + 0.6f, 0.6f,
						Draw.mix(Tokens.TEXT_3, 0xFFFFFFFF, t));
			}
		}

		private Grad pressedFill() {
			int c = Modules.KEYSTROKES_PRESSED.get() | 0xFF000000;
			int fill = Paint.fade(c == (Tokens.PRIMARY_MID | 0xFF000000) ? Accent.get() : c, 0.85f);
			return new Grad(fill, fill, 0);
		}

		/** How far a key has faded to pressed: 0 when up, 1 once it's been held for a moment. */
		private float held(KeyMapping key) {
			float[] state = pressed.computeIfAbsent(key, k -> new float[] {0f, 0f});
			float now = System.nanoTime() / 1.0e9f;
			float step = state[1] == 0f ? 1f : Math.min(1f, (now - state[1]) / FADE_SECONDS);
			state[1] = now;
			state[0] = key.isDown() ? Math.min(1f, state[0] + step) : Math.max(0f, state[0] - step);
			return state[0];
		}

		private void keyBack(Draw d, float x, float y, float w, float h, float t) {
			float inset = t * Math.min(w, h) * 0.02f;
			float x1 = x + inset;
			float y1 = y + inset;
			float x2 = x + w - inset;
			float y2 = y + h - inset;
			float hair = Hud.hairline(d);
			float strength = Math.max(0.35f, glassStrength());
			float old = Paint.opacity(Paint.opacity() * strength);
			Paint.rect(d, x1, y1, x2, y2, radius(R), 0x0DFFFFFF);
			Paint.stroke(d, x1, y1, x2, y2, radius(R), hair, 0x12FFFFFF);
			Paint.opacity(old);
			if (t > 0.01f) {
				float lit = Paint.opacity(Paint.opacity() * t);
				Paint.shadow(d, x1, y1, x2, y2, radius(R), 0, 0, 8, -2, 0xE6B51FF4);
				Paint.rect(d, x1, y1, x2, y2, radius(R), pressedFill());
				Paint.stroke(d, x1, y1, x2, y2, radius(R), hair, 0x33FFFFFF);
				Paint.opacity(lit);
			}
		}

		private void key(Draw d, float x, float y, float w, float h, String label, String small, KeyMapping key) {
			float t = held(key);
			keyBack(d, x, y, w, h, t);
			boolean shadow = textShadow() && t < 0.5f;
			int color = Draw.mix(Tokens.TEXT_2, 0xFFFFFFFF, t);
			if (small == null) {
				float cap = 12.5f * S * 0.727f * 1.25f;
				float tw = Hud.width(label, cap, Weight.BOLD);
				Hud.text(d, label, x + (w - tw) / 2f, y + (h - cap) / 2f, cap, Weight.BOLD, color, shadow);
				return;
			}
			float cap = 11.5f * S * 0.727f * 1.25f;
			float smallCap = 9.5f * S * 0.727f * 1.25f;
			float gap = 1.6f;
			float top = y + (h - cap - gap - smallCap) / 2f;
			Hud.text(d, label, x + (w - Hud.width(label, cap, Weight.BOLD)) / 2f, top, cap, Weight.BOLD, color, shadow);
			int smallColor = Draw.mix(Tokens.TEXT_3, 0xCCFFFFFF, t);
			Hud.text(d, small, x + (w - Hud.width(small, smallCap, Weight.SEMIBOLD)) / 2f, top + cap + gap, smallCap, Weight.SEMIBOLD,
					smallColor, shadow);
		}
	};

	public static final HudWidget ARMOR = new HudWidget("armor", Modules.ARMOR, Anchor.BOTTOM_RIGHT) {
		private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		private static final EquipmentSlot[] WITH_HAND = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
				EquipmentSlot.MAINHAND};

		private EquipmentSlot[] slots() {
			return Modules.ARMOR_HAND.get() ? WITH_HAND : ARMOR_SLOTS;
		}

		private boolean row() {
			return Modules.ARMOR_LAYOUT.is("Row");
		}

		private boolean hidden() {
			return Modules.ARMOR_DURABILITY.is("Hidden");
		}

		private float labelWidth() {
			return hidden() ? 0 : Hud.width(Modules.ARMOR_DURABILITY.is("Points") ? "2031" : "100%", Hud.CAP * 0.9f, Weight.SEMIBOLD);
		}

		@Override
		public int width(Minecraft mc) {
			if (row()) return slots().length * 20 + 6;
			return (int) Math.ceil(6 + 16 + (hidden() ? 0 : 4 + labelWidth()) + 6);
		}

		@Override
		public int height(Minecraft mc) {
			if (row()) return hidden() ? 22 : 30;
			return slots().length * 19 + 5;
		}

		@Override
		protected int[] defaultPosition(Minecraft mc, int screenWidth, int screenHeight) {
			if (row()) return new int[] {(screenWidth - width(mc)) / 2 + 100, screenHeight - 38 - height(mc)};
			return super.defaultPosition(mc, screenWidth, screenHeight);
		}

		@Override
		protected boolean usesAnchor() {
			return !row();
		}

		@Override
		public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
			glass(d, x, y, width(mc), height(mc), Hud.RADIUS);
			LocalPlayer player = mc.player;
			EquipmentSlot[] slots = slots();
			boolean shadow = textShadow();
			float cap = Hud.CAP * 0.9f;
			for (int i = 0; i < slots.length; i++) {
				int ix = row() ? x + 5 + i * 20 : x + 6;
				int iy = row() ? y + 3 : y + 3 + i * 19;
				ItemStack stack = player == null ? ItemStack.EMPTY : player.getItemBySlot(slots[i]);
				if (stack.isEmpty()) {
					Paint.rect(d, ix + 2, iy + 2, ix + 14, iy + 14, 3, 0x0DFFFFFF);
					continue;
				}
				d.item(stack, ix, iy);
				if (hidden() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) continue;
				float left = (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage();
				int color = Hud.quality(1 - left);
				String label = Modules.ARMOR_DURABILITY.is("Points") ? Integer.toString(stack.getMaxDamage() - stack.getDamageValue())
						: Math.round(left * 100) + "%";
				if (row()) {
					float tw = Hud.width(label, cap * 0.8f, Weight.SEMIBOLD);
					Hud.text(d, label, ix + 8 - tw / 2f, iy + 19, cap * 0.8f, Weight.SEMIBOLD, color, shadow);
				} else {
					Hud.text(d, label, ix + 20, iy + 4, cap, Weight.SEMIBOLD, color, shadow);
					Hud.bar(d, ix + 20, iy + 11.5f, ix + 20 + labelWidth(), 1.4f, left, new Grad(color, color, 90));
				}
			}
		}
	};

	public static final HudWidget ITEMS = new HudWidget("items", Modules.ITEMS, Anchor.BOTTOM_RIGHT) {
		private List<Item[]> items() {
			List<Item[]> list = new ArrayList<>();
			if (Modules.ITEMS_TOTEMS.get()) list.add(new Item[] {Items.TOTEM_OF_UNDYING});
			if (Modules.ITEMS_CRYSTALS.get()) list.add(new Item[] {Items.END_CRYSTAL});
			if (Modules.ITEMS_OBSIDIAN.get()) list.add(new Item[] {Items.OBSIDIAN});
			if (Modules.ITEMS_XP.get()) list.add(new Item[] {Items.EXPERIENCE_BOTTLE});
			if (Modules.ITEMS_GAPPLES.get()) list.add(new Item[] {Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE});
			if (Modules.ITEMS_PEARLS.get()) list.add(new Item[] {Items.ENDER_PEARL});
			if (Modules.ITEMS_ANCHORS.get()) {
				list.add(new Item[] {Items.RESPAWN_ANCHOR});
				list.add(new Item[] {Items.GLOWSTONE});
			}
			return list;
		}

		@Override
		public int width(Minecraft mc) {
			return 6 + Math.max(1, items().size()) * 20 - 2 + 6;
		}

		@Override
		public int height(Minecraft mc) {
			return 24;
		}

		@Override
		public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
			List<Item[]> items = items();
			glass(d, x, y, width(mc), 24, Hud.RADIUS);
			LocalPlayer player = mc.player;
			for (int i = 0; i < items.size(); i++) {
				Item[] group = items.get(i);
				int count = 0;
				if (player != null) {
					for (Item item : group) count += count(player.getInventory(), item);
				} else if (preview) {
					count = 8;
				}
				ItemStack icon = SafeItems.stack(group[0]);
				int ix = x + 6 + i * 20;
				int iy = y + 4;
				if (icon != null) d.item(icon, ix, iy);
				if (count == 0) Paint.rect(d, ix - 1, iy - 1, ix + 17, iy + 17, 4, 0xB00A0814);
				String label = Integer.toString(count);
				float cap = 3.9f;
				float tw = Hud.width(label, cap, Weight.BOLD);
				Hud.text(d, label, ix + 17 - tw, iy + 16 - cap, cap, Weight.BOLD, count == 0 ? Tokens.TEXT_3 : 0xFFFFFFFF, true);
			}
		}

		private int count(Inventory inventory, Item item) {
			int total = 0;
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.is(item)) total += stack.getCount();
			}
			return total;
		}
	};

	public static final HudWidget POPS = new PopsWidget();

	/**
	 * The website's Target HUD: the face in violet corner brackets, the name, a badge for what they
	 * hold, a heart with their health, the distance, a gradient health bar (pink when they're low, with
	 * absorption in gold and a ghost of the last hit), their armour, and chips for combo, pops and armour.
	 */
	public static final HudWidget TARGET = new HudWidget("target", Modules.TARGET_HUD, Anchor.BELOW_CROSSHAIR) {
		private static final float U = 0.8f;
		private static final float W = 180 * U;
		private static final float PAD = 7 * U;
		private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		private static final float[] SAMPLE_ARMOR = {0.92f, 0.64f, 0.81f, 0.27f};
		private final Anim shownHp = new Anim(1, 350, Ease.OUT);
		private final Anim ghostHp = new Anim(1, 500, Ease.IN_OUT);
		private LivingEntity last = null;
		private long since = 0;
		private long ghostAt = 0;
		private float lastHp = -1;

		@Override
		public int width(Minecraft mc) {
			return (int) Math.ceil(W);
		}

		@Override
		public int height(Minecraft mc) {
			return Math.round((Modules.TARGET_ARMOR.get() ? 66 : 44) * U);
		}

		@Override
		public boolean visible(Minecraft mc) {
			return Target.get() != null;
		}

		private static String badge(ItemStack held) {
			if (held.isEmpty()) return "FISTS";
			String id = held.getItem().toString().toLowerCase(Locale.ROOT);
			if (id.contains("sword")) return "SWORD";
			if (id.contains("_axe")) return "AXE";
			if (id.contains("mace")) return "MACE";
			if (id.contains("crossbow")) return "CROSSBOW";
			if (id.contains("bow")) return "BOW";
			if (id.contains("trident")) return "TRIDENT";
			if (id.contains("crystal")) return "CRYSTAL";
			if (id.contains("totem")) return "TOTEM";
			return "ITEM";
		}

		@Override
		public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
			LivingEntity target = Target.get();
			long now = System.currentTimeMillis();
			if (target != last) {
				last = target;
				since = now;
				lastHp = -1;
			}
			String name = target == null ? "Nightfall" : target.getName().getString();
			float max = target == null ? 20f : Math.max(1f, target.getMaxHealth());
			float health = target == null ? 13f : Math.max(0, target.getHealth());
			float absorption = target == null ? 4f : target.getAbsorptionAmount();
			double distance = target == null || mc.player == null ? 3.2 : mc.player.distanceTo(target);
			boolean dead = target != null && target.isDeadOrDying();
			float fraction = Math.min(1, health / max);
			if (lastHp < 0) {
				shownHp.snap(fraction);
				ghostHp.snap(fraction);
			} else if (fraction != lastHp) {
				shownHp.set(fraction);
				if (fraction < lastHp) ghostAt = now + 450;
				else ghostHp.snap(fraction);
			}
			lastHp = fraction;
			if (now >= ghostAt) ghostHp.set(shownHp.target());
			boolean low = fraction <= 0.3f && !dead;
			float intro = Ease.OUT.apply((now - since) / 450.0);
			float h = height(mc);
			float old = Paint.opacity(Paint.opacity() * intro);
			float x0 = x + (1 - intro) * 8 * U;
			boolean shadow = textShadow();

			float r = radius(9 * U);
			float strength = Math.max(0.25f, glassStrength());
			float glassOld = Paint.opacity(Paint.opacity() * strength);
			Paint.rect(d, x0, y, x0 + W, y + h, r, 0x940A0814);
			int edge = dead || low ? 0x80FF5AD9 : 0x1FFFFFFF;
			Paint.stroke(d, x0, y, x0 + W, y + h, r, Hud.hairline(d), edge);
			Paint.opacity(glassOld);

			float face = 26 * U;
			float fx = x0 + PAD;
			float fy = y + PAD;
			float pop = Ease.SPRING.apply(Math.min(1, (now - since) / 400.0));
			float fs = face * (0.6f + 0.4f * pop);
			float fcx = fx + face / 2f;
			float fcy = fy + face / 2f;
			Paint.shadow(d, fcx - fs / 2f, fcy - fs / 2f, fcx + fs / 2f, fcy + fs / 2f, 6 * U, 0, 0, 8 * U, 0, 0x54000000);
			if (target == null || target instanceof Player) {
				MenuScreen.face(d, target instanceof Player player ? skinOf(mc, player) : MenuNameTag.ownSkin(mc), fcx - fs / 2f, fcy - fs / 2f,
						fs, 6 * U);
			} else {
				Paint.rect(d, fcx - fs / 2f, fcy - fs / 2f, fcx + fs / 2f, fcy + fs / 2f, 6 * U, Accent.tile());
				Paint.icon(d, "ghost", fcx - fs * 0.3f, fcy - fs * 0.3f, fs * 0.6f, 0xFFFFFFFF);
			}
			Paint.stroke(d, fcx - fs / 2f, fcy - fs / 2f, fcx + fs / 2f, fcy + fs / 2f, 6 * U, Hud.hairline(d), 0x2EFFFFFF);
			if (dead) {
				Paint.rect(d, fx, fy, fx + face, fy + face, 6 * U, 0xA80B0915);
				Paint.icon(d, "skull", fx + face * 0.21f, fy + face * 0.21f, face * 0.58f, Tokens.PINK);
			} else {
				brackets(d, fx, fy, face, now);
			}

			float nx = x0 + 41 * U;
			float nameCap = 10 * U * 0.727f;
			String badge = target == null ? "SWORD" : badge(target.getMainHandItem());
			float badgeCap = 6 * U * 0.727f;
			float bh = 11 * U;
			float bw = 5 * U + 6.5f * U + 3 * U + Type.spacedWidth(badge, badgeCap, 0.08f, Weight.BOLD) + 6 * U;
			float bx = x0 + W - PAD - bw;
			float by = y + 8 * U;
			Paint.rect(d, bx, by, bx + bw, by + bh, bh / 2f, 0x50880FF9);
			Paint.stroke(d, bx, by, bx + bw, by + bh, bh / 2f, Hud.hairline(d), 0x80B51FF4);
			Paint.icon(d, badge.equals("AXE") ? "hammer" : badge.equals("BOW") || badge.equals("CROSSBOW") ? "target" : "sword", bx + 5 * U,
					by + (bh - 6.5f * U) / 2f, 6.5f * U, Tokens.LILAC);
			Type.spaced(d, badge, bx + 5 * U + 6.5f * U + 3 * U, by + (bh - badgeCap) / 2f, badgeCap, 0.08f, Weight.BOLD, Tokens.TEXT);
			String shownName = Type.fit(name, bx - nx - 4, nameCap, Weight.SEMIBOLD);
			Hud.text(d, shownName, nx, y + 13.5f * U - nameCap / 2f, nameCap, Weight.SEMIBOLD, dead ? Tokens.TEXT_2 : Tokens.TEXT, shadow);
			if (dead) {
				float sw = Hud.width(shownName, nameCap, Weight.SEMIBOLD) * Ease.OUT.apply(Math.min(1, (now - since) / 350.0));
				Paint.rect(d, nx - U, y + 13.5f * U - 0.55f * U, nx + sw + U, y + 13.5f * U + 0.55f * U, 0.5f, Tokens.PINK);
			}

			float hy = y + 27 * U;
			float heart = 9 * U;
			float beat = low ? heartbeat(now) : 1;
			float hs = heart * beat;
			Paint.icon(d, "heart", nx + heart / 2f - hs / 2f, hy - hs / 2f, hs, 1, true, dead ? Tokens.TEXT_3 : Tokens.PINK, null);
			String hp = trim(health / 2f);
			float hx = nx + heart + 3 * U;
			hx += Hud.text(d, hp, hx, hy - nameCap / 2f, nameCap, Weight.SEMIBOLD, low ? Tokens.PINK : Tokens.TEXT, shadow);
			if (absorption > 0) {
				float absCap = 8 * U * 0.727f;
				Hud.text(d, "+" + trim(absorption / 2f), hx + 3 * U, hy - absCap / 2f, absCap, Weight.SEMIBOLD, Tokens.GOLD, shadow);
			}
			float distCap = 8 * U * 0.727f;
			String dist = String.format(Locale.ROOT, "%.1fm", distance);
			Hud.text(d, dist, x0 + W - PAD - Hud.width(dist, distCap, Weight.MEDIUM), hy - distCap / 2f, distCap, Weight.MEDIUM, Tokens.TEXT_2,
					shadow);

			float barY = y + 38.5f * U;
			float barH = 3.5f * U;
			float bx1 = x0 + PAD;
			float bx2 = x0 + W - PAD;
			Paint.rect(d, bx1, barY, bx2, barY + barH, barH / 2f, 0x1FFFFFFF);
			float ghost = ghostHp.get();
			if (ghost > shownHp.get()) Paint.rect(d, bx1, barY, bx1 + (bx2 - bx1) * ghost, barY + barH, barH / 2f, 0x99FFC6E8);
			float fill = dead ? 0 : Math.max(barH / (bx2 - bx1), shownHp.get());
			if (fill > 0) {
				float fx2 = bx1 + (bx2 - bx1) * fill;
				Paint.rect(d, bx1, barY, fx2, barY + barH, barH / 2f, low ? Tokens.PINK : Accent.get());
			}
			if (absorption > 0) {
				float aw = (bx2 - bx1) * Math.min(1, absorption / max);
				Paint.shadow(d, bx1, barY - 0.5f * U, bx1 + aw, barY + 0.9f * U, 0.7f * U, 0, 0, 5 * U, 0, 0x99FFD27A);
				Paint.rect(d, bx1, barY - 0.5f * U, bx1 + aw, barY + 0.9f * U, 0.7f * U, Tokens.GOLD);
			}

			if (!Modules.TARGET_ARMOR.get()) {
				Paint.opacity(old);
				return;
			}
			float ay = y + 44 * U;
			float segment = (bx2 - bx1 - 3 * 2 * U) / 4f;
			for (int i = 0; i < 4; i++) {
				float sx = bx1 + i * (segment + 2 * U);
				float left;
				if (target == null) {
					left = SAMPLE_ARMOR[i];
				} else {
					ItemStack piece = target.getItemBySlot(ARMOR_SLOTS[i]);
					left = piece.isEmpty() ? 0 : !piece.isDamageableItem() || piece.getMaxDamage() <= 0 ? 1
							: (piece.getMaxDamage() - piece.getDamageValue()) / (float) piece.getMaxDamage();
				}
				Paint.rect(d, sx, ay, sx + segment, ay + 1.5f * U, 0.75f * U, 0x1FFFFFFF);
				if (left > 0) Paint.rect(d, sx, ay, sx + segment * left, ay + 1.5f * U, 0.75f * U, Hud.quality(1 - left));
			}

			float cy = y + 49 * U;
			float ch = 12 * U;
			if (dead) {
				Paint.rect(d, bx1, cy, bx2, cy + ch, 4 * U, 0x33FF5AD9);
				Paint.stroke(d, bx1, cy, bx2, cy + ch, 4 * U, Hud.hairline(d), 0x73FF5AD9);
				Paint.icon(d, "skull", bx1 + 3 * U, cy + (ch - 7.5f * U) / 2f, 7.5f * U, Tokens.PINK);
				float cap = 7.5f * U * 0.727f;
				Type.draw(d, "Eliminated", bx1 + 13 * U, cy + (ch - cap) / 2f, cap, Weight.BOLD, Tokens.PINK);
				Paint.opacity(old);
				return;
			}
			int combo = target == null ? 3 : Combo.count();
			int pops = target == null ? 1 : PopTracker.pops(target.getUUID());
			int armor = target == null ? 12 : target.getArmorValue();
			float cw = (bx2 - bx1 - 2 * 4 * U) / 3f;
			chip(d, bx1, cy, cw, ch, "flame", Integer.toString(combo), "combo", now);
			chip(d, bx1 + cw + 4 * U, cy, cw, ch, "shield-plus", Integer.toString(pops), pops == 1 ? "pop" : "pops", now);
			chip(d, bx1 + 2 * (cw + 4 * U), cy, cw, ch, "shield-half", Integer.toString(armor), "armour", now);
			Paint.opacity(old);
		}

		private void chip(Draw d, float x, float y, float w, float h, String icon, String value, String label, long now) {
			Paint.rect(d, x, y, x + w, y + h, 4 * U, 0x0DFFFFFF);
			Paint.stroke(d, x, y, x + w, y + h, 4 * U, Hud.hairline(d), 0x10FFFFFF);
			float cx = x + 3 * U;
			Paint.icon(d, icon, cx, y + (h - 7.5f * U) / 2f, 7.5f * U, Tokens.LILAC);
			cx += 7.5f * U + 2.5f * U;
			float cap = 8.5f * U * 0.727f;
			Type.draw(d, value, cx, y + (h - cap) / 2f, cap, Weight.SEMIBOLD, Tokens.TEXT);
			cx += Type.width(value, cap, Weight.SEMIBOLD) + 2 * U;
			float small = 6.5f * U * 0.727f;
			Type.draw(d, Type.fit(label, x + w - cx - U, small, Weight.MEDIUM), cx, y + (h - small) / 2f + 0.3f, small, Weight.MEDIUM, Tokens.TEXT_3);
		}

		private float heartbeat(long now) {
			float t = (now % 980) / 980f;
			if (t < 0.1f) return 1 + 0.22f * (t / 0.1f);
			if (t < 0.22f) return 1.22f - 0.18f * ((t - 0.1f) / 0.12f);
			if (t < 0.32f) return 1.04f + 0.12f * ((t - 0.22f) / 0.1f);
			if (t < 0.6f) return 1.16f - 0.16f * ((t - 0.32f) / 0.28f);
			return 1;
		}

		/** The violet corner brackets round the face, breathing in and out a little. */
		private void brackets(Draw d, float x, float y, float size, long now) {
			float breathe = (float) (0.5 - 0.5 * Math.cos((now % 2600) / 2600.0 * Math.PI * 2));
			float off = (2.5f + 0.5f * breathe) * U;
			float len = 5.5f * U;
			float th = 1.2f * U;
			int color = Paint.fade(0xFFC324F4, 0.45f + 0.55f * Math.max(0, 1 - (now - since) / 1000f));
			float x1 = x - off;
			float y1 = y - off;
			float x2 = x + size + off;
			float y2 = y + size + off;
			Paint.rect(d, x1, y1, x1 + len, y1 + th, 0.4f, color);
			Paint.rect(d, x1, y1, x1 + th, y1 + len, 0.4f, color);
			Paint.rect(d, x2 - len, y1, x2, y1 + th, 0.4f, color);
			Paint.rect(d, x2 - th, y1, x2, y1 + len, 0.4f, color);
			Paint.rect(d, x1, y2 - th, x1 + len, y2, 0.4f, color);
			Paint.rect(d, x1, y2 - len, x1 + th, y2, 0.4f, color);
			Paint.rect(d, x2 - len, y2 - th, x2, y2, 0.4f, color);
			Paint.rect(d, x2 - th, y2 - len, x2, y2, 0.4f, color);
		}

		private String trim(float value) {
			return value == Math.floor(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
		}
	};

	/** A player's skin from the tab list, or the default for their name. */
	private static PlayerSkin skinOf(Minecraft mc, Player player) {
		ClientPacketListener connection = mc.getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
		if (info != null) return info.getSkin();
		return DefaultPlayerSkin.get(player.getUUID());
	}

	/** Your status effects: each one's icon, name and level, the time left, and a bar that empties as it runs out. */
	public static final HudWidget POTIONS = new HudWidget("potions", Modules.POTIONS, Anchor.MIDDLE_RIGHT) {
		private static final float ROW = 17;
		private static final float BARS_ROW = 20;
		private static final float W = 104;
		private final Map<Holder<MobEffect>, Integer> longest = new HashMap<>();

		private List<MobEffectInstance> effects(Minecraft mc) {
			List<MobEffectInstance> list = new ArrayList<>();
			if (mc.player != null) {
				for (MobEffectInstance effect : mc.player.getActiveEffects()) {
					if (effect.showIcon()) list.add(effect);
				}
			}
			list.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
			return list;
		}

		private float rowHeight() {
			return Modules.POTIONS_BARS.get() ? BARS_ROW : ROW;
		}

		@Override
		public boolean visible(Minecraft mc) {
			return !effects(mc).isEmpty();
		}

		@Override
		public int width(Minecraft mc) {
			return Math.round(W);
		}

		@Override
		public int height(Minecraft mc) {
			int rows = Math.max(2, effects(mc).size());
			return Math.round(6 + rows * rowHeight() + 2);
		}

		@Override
		public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
			List<MobEffectInstance> effects = effects(mc);
			boolean shadow = textShadow();
			glass(d, x, y, W, height(mc), Hud.RADIUS);
			if (effects.isEmpty() && preview) {
				sample(d, x, y + 4, "Speed II", "1:24", 0.7f, 0xFF7CAFC6, shadow);
				sample(d, x, y + 4 + rowHeight(), "Strength", "0:42", 0.35f, 0xFFFFC700, shadow);
				return;
			}
			float ry = y + 4;
			for (MobEffectInstance effect : effects) {
				Holder<MobEffect> holder = effect.getEffect();
				int duration = effect.getDuration();
				int most = Math.max(duration, longest.getOrDefault(holder, 0));
				longest.put(holder, most);
				float left = effect.isInfiniteDuration() ? 1 : most <= 0 ? 0 : duration / (float) most;
				int sx = Math.round(x + Hud.PAD_X);
				int sy = Math.round(ry + (ROW - 9) / 2f);
				d.sprite(Compat.mobEffectSprite(holder), sx, sy, 9, 9);
				String name = holder.value().getDisplayName().getString() + roman(effect.getAmplifier());
				String time = effect.isInfiniteDuration() ? "∞" : time(duration);
				row(d, x, ry, name, time, left, holder.value().getColor() | 0xFF000000, shadow, duration < 200 && !effect.isInfiniteDuration());
				ry += rowHeight();
			}
			longest.keySet().removeIf(holder -> mc.player == null || !mc.player.hasEffect(holder));
		}

		private void sample(Draw d, float x, float y, String name, String time, float left, int color, boolean shadow) {
			Paint.circle(d, x + Hud.PAD_X + 4.5f, y + ROW / 2f, 3.5f, color);
			row(d, x, y, name, time, left, color, shadow, false);
		}

		private void row(Draw d, float x, float y, String name, String time, float left, int color, boolean shadow, boolean ending) {
			float cy = y + ROW / 2f;
			float tx = x + Hud.PAD_X + 9 + 5;
			float timeCap = Hud.CAP * 0.85f;
			float tw = Hud.width(time, timeCap, Weight.SEMIBOLD);
			float right = x + W - Hud.PAD_X;
			int timeColor = ending && (System.currentTimeMillis() / 400) % 2 == 0 ? Tokens.LOW : Tokens.TEXT_2;
			Hud.text(d, time, right - tw, cy - timeCap / 2f, timeCap, Weight.SEMIBOLD, timeColor, shadow);
			Hud.text(d, Type.fit(name, right - tw - 4 - tx, Hud.CAP * 0.9f, Weight.SEMIBOLD), tx, cy - Hud.CAP * 0.45f, Hud.CAP * 0.9f,
					Weight.SEMIBOLD, Tokens.TEXT, shadow);
			if (Modules.POTIONS_BARS.get()) {
				Hud.bar(d, tx, y + ROW - 0.5f, right, 1.4f, left, new Grad(Accent.lighten(color, 0.1f), color, 90));
			}
		}

		private String roman(int amplifier) {
			return switch (amplifier) {
				case 0 -> "";
				case 1 -> " II";
				case 2 -> " III";
				case 3 -> " IV";
				case 4 -> " V";
				default -> " " + (amplifier + 1);
			};
		}

		private String time(int ticks) {
			int seconds = ticks / 20;
			return seconds >= 3600 ? seconds / 3600 + ":" + String.format(Locale.ROOT, "%02d:%02d", seconds / 60 % 60, seconds % 60)
					: seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
		}
	};

	public static final HudWidget JUMP_RESET = new HudWidget("jump_reset", Modules.JUMP_RESET, Anchor.ABOVE_HOTBAR) {
		@Override
		public int width(Minecraft mc) {
			return (int) Math.ceil(Hud.PAD_X + 8 + Hud.GAP + Type.spacedWidth("Jump reset", Hud.SMALL, 0.05f, Weight.SEMIBOLD) + 4
					+ Hud.width("Perfect +1t", Hud.CAP, Weight.BOLD) + Hud.PAD_X);
		}

		@Override
		public int height(Minecraft mc) {
			return Math.round(Hud.PILL_H);
		}

		@Override
		public boolean visible(Minecraft mc) {
			return JumpReset.result() != null;
		}

		@Override
		public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
			JumpReset.Result result = JumpReset.result();
			String text = result == null ? "Perfect" : result.text();
			int color = result == null ? Tokens.MINT : result.color();
			boolean label = showLabel();
			boolean shadow = textShadow();
			float w = Hud.PAD_X + 8 + Hud.GAP + (label ? Type.spacedWidth("Jump reset", Hud.SMALL, 0.05f, Weight.SEMIBOLD) + 4 : 0)
					+ Hud.width(text, Hud.CAP, Weight.BOLD) + Hud.PAD_X;
			float cx = x + (width(mc) - w) / 2f;
			float cy = y + Hud.PILL_H / 2f;
			glass(d, cx, y, w, Hud.PILL_H, Hud.RADIUS);
			cx += Hud.PAD_X;
			Paint.icon(d, "arrow-big-up-dash", cx, cy - 4, 8, Accent.text());
			cx += 8 + Hud.GAP;
			if (label) {
				Type.spaced(d, "Jump reset", cx, cy - Hud.SMALL / 2f, Hud.SMALL, 0.05f, Weight.SEMIBOLD, Tokens.TEXT_3);
				cx += Type.spacedWidth("Jump reset", Hud.SMALL, 0.05f, Weight.SEMIBOLD) + 4;
			}
			Hud.text(d, text, cx, cy - Hud.CAP / 2f, Hud.CAP, Weight.BOLD, color, shadow);
		}
	};

	public static final HudWidget TOTEM_WARNING = new HudWidget("totem_warning", Modules.TOTEM_WARNING, Anchor.ABOVE_CROSSHAIR) {
		private static final float H = 22;

		private int spare(Minecraft mc) {
			LocalPlayer player = mc.player;
			if (player == null) return 0;
			Inventory inventory = player.getInventory();
			int total = 0;
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.is(Items.TOTEM_OF_UNDYING)) total += stack.getCount();
			}
			return total;
		}

		private String message() {
			return "No totem in your offhand";
		}

		private String detail(int spare) {
			return spare + " in inventory";
		}

		@Override
		public int width(Minecraft mc) {
			return (int) Math.ceil(8 + 16 + 6 + Hud.width(message(), Hud.CAP, Weight.SEMIBOLD) + 6 + Hud.width(detail(64), Hud.SMALL, Weight.MEDIUM) + 9);
		}

		@Override
		public int height(Minecraft mc) {
			return Math.round(H);
		}

		@Override
		public boolean visible(Minecraft mc) {
			LocalPlayer player = mc.player;
			if (player == null || player.isSpectator() || player.isCreative()) return false;
			if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return false;
			if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) return false;
			return spare(mc) > 0;
		}

		@Override
		public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
			int spare = mc.player == null || preview && !visible(mc) ? 4 : spare(mc);
			float w = width(mc);
			float pulse = (float) (0.5 - 0.5 * Math.cos((System.currentTimeMillis() % 1050) / 1050.0 * Math.PI * 2));
			float r = radius(H / 2f);
			Paint.shadow(d, x, y, x + w, y + H, r, 0, 0, 12, -2, Paint.fade(0xFFFF5AD9, 0.25f + 0.35f * pulse));
			Paint.rect(d, x, y, x + w, y + H, r, 0xD60E0B1C);
			Paint.rect(d, x, y, x + w, y + H, r, 0x38FF5AD9);
			Paint.stroke(d, x, y, x + w, y + H, r, Hud.hairline(d), Draw.mix(0x59FF5AD9, 0xB3FF5AD9, pulse));
			ItemStack totem = SafeItems.stack(Items.TOTEM_OF_UNDYING);
			if (totem != null) d.item(totem, x + 6, y + 3);
			float tx = x + 8 + 16 + 4;
			float cy = y + H / 2f;
			tx += Hud.text(d, message(), tx, cy - Hud.CAP / 2f, Hud.CAP, Weight.SEMIBOLD, Tokens.TEXT, false) + 6;
			Hud.text(d, detail(spare), tx, cy - Hud.SMALL / 2f, Hud.SMALL, Weight.MEDIUM, Tokens.PINK, false);
		}
	};

	public static final HudWidget SCOREBOARD = new ScoreboardWidget();
	public static final HudWidget MENU_LOGO = new MenuLogoWidget();
	public static final HudWidget MUSIC = new MusicWidget();
	public static final HudWidget WATERMARK = new WatermarkWidget();
	public static final HudWidget PLAYER_LIST = new PlayerListWidget();
	public static final HudWidget STAFF_LIST = new StaffListWidget();
	public static final HudWidget MODULE_LIST = new ModuleListWidget();
	public static final HudWidget LYRICS = new LyricsWidget();

	/** A click with the cursor free (chat open), for the Music Player's buttons; returns whether it used it. */
	public static boolean musicClick(double x, double y) {
		return ((MusicWidget) MUSIC).click(x, y);
	}

	/** Drawing order; widgets that share an anchor stack in this order (upwards at the bottom). */
	public static final List<HudWidget> ALL = List.of(WATERMARK, PLAYER_NAME, FPS, CPS, PING, TPS, MEMORY, SERVER, CLOCK, COMBO, COORDS, KEYSTROKES, ITEMS, ARMOR, MUSIC, MODULE_LIST, POPS,
			PLAYER_LIST, STAFF_LIST, POTIONS, TARGET, JUMP_RESET, TOTEM_WARNING, MENU_LOGO, LYRICS, SCOREBOARD);
}
