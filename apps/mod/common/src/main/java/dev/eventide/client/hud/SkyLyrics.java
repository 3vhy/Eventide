package dev.eventide.client.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.eventide.client.Hooks;
import dev.eventide.client.gui.UiFont;
import dev.eventide.client.module.Modules;
import dev.eventide.client.music.MusicBridge;
import dev.eventide.client.music.NowPlaying;
import dev.eventide.client.render.ShaderPacks;
import dev.eventide.client.render.SkyMesh;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Matrix4f;

/**
 * Sky Lyrics in the sky: the line being sung written large above the horizon, as part of the sky, white with
 * a soft violet glow, tilted a little as if hung on the sky, the next line smaller beneath it. They're drawn
 * while Minecraft draws the sky, before any block, with no depth test or write, so blocks, mobs, the hand,
 * water, glass, clouds and rain all cover or blend over them exactly as they do the sky, and underground or
 * indoors they can't be seen at all. Each line stays put in the world as you turn (with a slow drift); if
 * you look well away for a moment it glides back into view. The words fill in as they're sung, a glow
 * running along with the voice. Height, tilt, size, font, colours, glow, backdrop and how lines change
 * (rise, fade, scale or drift, at your speed) are all in the settings. The text layout is worked out once
 * per line; only the karaoke colours and fades change from frame to frame.
 */
public final class SkyLyrics {
	private static final float DISTANCE = 50;
	private static final float CAP_DEGREES = 2.9f;
	/** Rows wrap at this many capital heights. */
	private static final float WRAP = 24;
	private static final float LINE = 1.5f;
	private static final float RISE = 0.8f;
	private static final float AWAY_DEGREES = 55;
	private static final long AWAY_MS = 1200;
	private static final int CACHE = 32;
	private static final Matrix4f IDENTITY = new Matrix4f();

	private static final LyricTracker LYRICS = new LyricTracker();
	private static final Map<String, Row> ROWS = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Row> eldest) {
			return size() > CACHE;
		}
	};
	private static final Map<String, List<String>> WRAPS = new LinkedHashMap<>(32, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
			return size() > CACHE;
		}
	};
	private static final Mapper MAPPER = new Mapper();
	private static final List<GpuTextureView> TEXTURES = new ArrayList<>();
	private static final List<BufferBuilder> BUILDERS = new ArrayList<>();
	private static final List<RenderPipeline> PIPELINES = new ArrayList<>();
	private static final float[] RING_X = new float[64];
	private static final float[] RING_Y = new float[64];
	private static final float[] RING_X2 = new float[64];
	private static final float[] RING_Y2 = new float[64];
	private static final float[] GLOW_STEPS = {-1.6f, -0.8f, -0.3f, 0.2f, 0.8f, 1.5f, 2.4f};

	private static float anchorYaw = 0;
	private static float previousYaw = 0;
	private static boolean anchored = false;
	private static long awaySince = 0;
	private static long lastFrame = 0;
	private static float fontCap = Type.CAP;
	private static int fontChoice = -1;

	/** One row of text, laid out once: its glyphs and width in font units. */
	private record Row(Font.PreparedText glyphs, float width) {
	}

	private SkyLyrics() {
	}

	/** Whether lyrics go in the sky (Sky Lyrics on, set to Sky). */
	public static boolean wanted() {
		return Modules.LYRICS.enabled() && Modules.LYRICS_PLACE.is("Sky");
	}

	/**
	 * Whether the sky can't hold the lyrics here: a dimension without a sky (the Nether), where they go
	 * to the top of the screen instead.
	 */
	public static boolean noSky(Minecraft mc) {
		return mc.level != null && mc.level.dimensionType().skybox() == DimensionType.Skybox.NONE;
	}

	/** Whether a shader pack has taken over the sky, so lyrics can't be drawn in it. */
	public static boolean blocked() {
		return ShaderPacks.inUse();
	}

	/** Draws the lyrics into the sky. Called from the sky pass, after the sun, moon and stars. */
	public static void draw(float rainBrightness) {
		Minecraft mc = Minecraft.getInstance();
		if (!wanted() || mc.player == null || mc.level == null || mc.options.hideGui || blocked()) return;
		if (mc.getOverlay() != null) {
			ROWS.clear();
			WRAPS.clear();
			return;
		}
		int font = Modules.LYRICS_FONT.index();
		if (font != fontChoice) {
			fontChoice = font;
			ROWS.clear();
			WRAPS.clear();
		}
		fontCap = font == 2 ? 7f : Type.CAP;
		NowPlaying song = MusicBridge.current();
		float oldYaw = anchorYaw;
		boolean changed = LYRICS.update(song);
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		float cameraYaw = mc.gameRenderer.getMainCamera().yRot();
		follow(changed, cameraYaw, oldYaw, dt);
		if (LYRICS.current().isEmpty() && LYRICS.previous().isEmpty()) return;

		float size = (float) (Modules.LYRICS_SIZE.get() / 100.0);
		float worldCap = DISTANCE * (float) Math.tan(Math.toRadians(CAP_DEGREES * size));
		float scale = worldCap / fontCap;
		float seconds = (now / 1_000_000L % 3_600_000L) / 1000f;
		float driftYaw = (float) Math.sin(seconds * 0.09) * 1.1f;
		float elevation = (float) Modules.LYRICS_HEIGHT.get() + (float) Math.sin(seconds * 0.13) * 0.45f;
		float tilt = (float) Modules.LYRICS_TILT.get();
		float sky = 0.62f + 0.38f * Math.max(0, Math.min(1, rainBrightness));
		begin();

		float t = LYRICS.change((float) (Modules.LYRICS_SPEED.get() / 100.0));
		int style = Modules.LYRICS_STYLE.index();
		float cap = fontCap;
		if (!LYRICS.previous().isEmpty() && t < 1) {
			float out = 1 - t;
			float grow = style == 2 ? 1 + 0.12f * t : 1;
			MAPPER.place(previousYaw + driftYaw + (style == 3 ? -6 * t : 0), elevation, scale * grow, tilt);
			float lift = style == 0 ? -RISE * cap * t : 0;
			block(LYRICS.previous(), lift, 1, sky * out * (style == 1 ? out : 1), false, 0);
		}
		float grow = style == 2 ? 0.86f + 0.14f * t : 1;
		MAPPER.place(anchorYaw + driftYaw + (style == 3 ? 6 * (1 - t) : 0), elevation, scale * grow, tilt);
		String line = LYRICS.current();
		if (!line.isEmpty()) {
			float lineOpacity = sky * t;
			float top = style == 0 ? RISE * cap * (1 - t) : 0;
			float h;
			if (LYRICS.marker()) {
				float pulse = 0.8f + 0.2f * (float) Math.sin(seconds * 3.2);
				h = block(line, top, 1, lineOpacity * pulse, true, seconds);
			} else {
				h = block(line, top, LYRICS.sung(song), lineOpacity, false, seconds);
			}
			float y = top + h;
			int primary = Modules.LYRICS_COLOR.get() | 0xFF000000;
			if (Modules.LYRICS_NEXT.get() && !LYRICS.next().isBlank()) {
				float small = 0.6f;
				y += cap * small * 0.9f;
				String next = fitted(LYRICS.next(), WRAP * cap / small, Weight.SEMIBOLD);
				int dim = Paint.fade(primary, 0.62f);
				text(next, Weight.SEMIBOLD, -width(next, Weight.SEMIBOLD) * small / 2f, y, small, dim, dim, Float.MAX_VALUE, lineOpacity * 0.85f, true);
				y += cap * small * LINE;
			}
			if (!LYRICS.synced() && LYRICS.any()) {
				float tiny = 0.42f;
				y += cap * tiny * 0.8f;
				String tag = "≈ lyrics not synced";
				text(tag, Weight.SEMIBOLD, -width(tag, Weight.SEMIBOLD) * tiny / 2f, y, tiny, Tokens.TEXT_3, Tokens.TEXT_3, Float.MAX_VALUE,
						lineOpacity * 0.7f, true);
			}
		}
		finish();
		Hooks.lyricsDrawn = true;
	}

	/**
	 * Keeps the lyrics where they are in the world as you turn. A new line far from where you look is
	 * written in front of you; after looking well away for a moment, they glide back into view.
	 */
	private static void follow(boolean changed, float cameraYaw, float oldYaw, float dt) {
		if (changed) previousYaw = oldYaw;
		float off = Mth.wrapDegrees(cameraYaw - anchorYaw);
		if (!anchored || changed && Math.abs(off) > 40) {
			anchorYaw = cameraYaw;
			anchored = true;
			awaySince = 0;
			return;
		}
		if (Math.abs(off) > AWAY_DEGREES) {
			long now = System.currentTimeMillis();
			if (awaySince == 0) awaySince = now;
			if (now - awaySince > AWAY_MS) anchorYaw += off * (1 - (float) Math.exp(-dt / 0.45f));
		} else {
			awaySince = 0;
		}
	}

	/**
	 * One sung line, wrapped to two rows at most and centred: the backdrop (a soft glow in the accent colour, an
	 * outline round the letters, or nothing), then the text in the main colour as far as it's been sung
	 * ({@code sung}, 0..1, like karaoke) and faint after it, with a glow running along with the voice. Returns its height.
	 */
	private static float block(String text, float top, float sung, float opacity, boolean marker, float seconds) {
		if (opacity <= 0.003f) return 0;
		if (marker) return note(text, top, opacity);
		float cap = fontCap;
		List<String> rows = wrap(text);
		float total = 0;
		for (String row : rows) total += width(row, Weight.BOLD);
		float lit = total * sung;
		float y = top;
		int primary = Modules.LYRICS_COLOR.get() | 0xFF000000;
		int accent = Modules.LYRICS_ACCENT.get() | 0xFF000000;
		float glow = (float) (Modules.LYRICS_GLOW.get() / 100.0);
		int backdrop = Modules.LYRICS_BACKDROP.index();
		int dim = mixColor(Paint.fade(primary, 0.55f), Paint.fade(accent, 0.55f), 0.35f);
		for (String row : rows) {
			float w = width(row, Weight.BOLD);
			float x = -w / 2f;
			if (backdrop == 0) {
				glow(x - cap * 0.1f, y + cap * 0.1f, x + w + cap * 0.1f, y + cap * 0.9f, cap * 0.4f, cap * 1.8f, 0x500A0618, opacity);
				glow(x, y + cap * 0.15f, x + w, y + cap * 0.85f, cap * 0.35f, cap * 3.0f, Paint.fade(accent, Math.min(0.85f, 0.34f * glow)), opacity);
			} else if (backdrop == 2) {
				glow(x - cap * 0.6f, y - cap * 0.35f, x + w + cap * 0.6f, y + cap * 1.35f, cap * 0.5f, cap * 0.6f, 0x9A07040F, opacity);
			}
			float here = Math.max(-1, Math.min(w, lit));
			if (here > 0 && here < w && glow > 0) {
				glow(x + here - cap * 0.9f, y + cap * 0.1f, x + here + cap * 0.3f, y + cap * 0.9f, cap * 0.4f, cap * 1.6f,
						Paint.fade(Tokens.PINK, Math.min(0.7f, 0.3f * glow)), opacity);
			}
			if (backdrop == 1) outline(row, x, y, Paint.fade(accent, Math.min(1, 0.85f * Math.max(0.4f, glow))), opacity);
			text(row, Weight.BOLD, x, y, 1, primary, dim, Math.max(-1, lit), opacity, backdrop == 1);
			lit -= w;
			y += cap * LINE;
		}
		return y - top - cap * (LINE - 1);
	}

	/** An outline round a row's letters in {@code color}: the row drawn in that colour a hair out in eight directions. */
	private static void outline(String text, float x, float y, int color, float opacity) {
		Row row = row(text, Weight.BOLD);
		float o = fontCap * 0.09f;
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8;
			MAPPER.glyphs(x + (float) Math.cos(a) * o, y + (float) Math.sin(a) * o, 1, color, color, Float.MAX_VALUE, opacity);
			row.glyphs().visit(MAPPER);
		}
	}

	private static int mixColor(int a, int b, float t) {
		return Mapper.mix(a, b, t);
	}

	/**
	 * The music note shown through an intro or instrumental break, as the menus' Lucide icon with a soft
	 * glow (falling back to the ♪ glyph if the icon can't be made). Returns its height.
	 */
	private static float note(String text, float top, float opacity) {
		float cap = fontCap;
		float size = cap * 2.1f;
		float y1 = top - cap * 0.5f;
		glow(-size * 0.3f, y1 + size * 0.2f, size * 0.3f, y1 + size * 0.8f, size * 0.3f, cap * 3.2f, Paint.fade(Modules.LYRICS_ACCENT.get() | 0xFF000000, 0.42f),
				opacity);
		GpuTextureView icon = iconView();
		BufferBuilder builder = icon == null ? null : builderFor(icon, SkyMesh.text());
		if (builder == null) {
			text(text, Weight.BOLD, -width(text, Weight.BOLD) * 0.95f, y1, 1.9f, 0xFFFFFFFF, 0xFFFFFFFF, Float.MAX_VALUE, opacity, false);
			return cap * 1.4f;
		}
		int shadow = argb(0x80, 0x140A30, opacity);
		int white = argb(0xFF, 0xFFFFFF, opacity);
		float off = cap * 0.08f;
		MAPPER.quadTexture(builder, -size / 2f + off, y1 + off, size, shadow);
		MAPPER.quadTexture(builder, -size / 2f, y1, size, white);
		return size * 0.75f;
	}

	private static GpuTextureView iconView() {
		try {
			net.minecraft.resources.Identifier id = dev.eventide.client.ui.Paint.iconTexture("music", 96);
			return id == null ? null : Minecraft.getInstance().getTextureManager().getTexture(id).getTextureView();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * A row of text at ({@code x}, {@code y}) in font units times {@code scale}: a shadow, then the glyphs
	 * coloured {@code lit} up to {@code litWidth} across and {@code dim} after it, blending over a letter's width.
	 */
	private static void text(String text, Weight weight, float x, float y, float scale, int lit, int dim, float litWidth, float opacity,
			boolean faintShadow) {
		Row row = row(text, weight);
		float shadow = fontCap * 0.08f;
		MAPPER.glyphs(x + shadow * scale, y + shadow * scale, scale, 0xB3100828, 0xB3100828, Float.MAX_VALUE, opacity * (faintShadow ? 0.6f : 1));
		row.glyphs().visit(MAPPER);
		MAPPER.glyphs(x, y, scale, lit, dim, litWidth, opacity);
		row.glyphs().visit(MAPPER);
	}

	private static Row row(String text, Weight weight) {
		String key = weight.ordinal() + text;
		Row row = ROWS.get(key);
		if (row == null) {
			Font font = Minecraft.getInstance().font;
			net.minecraft.network.chat.Component component = component(text, weight);
			Font.PreparedText glyphs = font.prepareText(component.getVisualOrderText(), 0, 0, 0xFFFFFFFF, false, false, 0);
			row = new Row(glyphs, font.width(component));
			ROWS.put(key, row);
		}
		return row;
	}

	/** Text in the lyrics' font (Lyrics > Font): Inter, the Eventide display face, or Minecraft's own. */
	private static net.minecraft.network.chat.Component component(String text, Weight weight) {
		return switch (Modules.LYRICS_FONT.index()) {
			case 1 -> weight == Weight.BOLD ? UiFont.brand(text) : UiFont.semibold(text);
			case 2 -> weight == Weight.BOLD ? net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.ChatFormatting.BOLD)
					: net.minecraft.network.chat.Component.literal(text);
			default -> weight == Weight.BOLD ? UiFont.bold(text) : UiFont.semibold(text);
		};
	}

	/** How wide {@code text} is in the lyrics' font, in font units, without keeping its layout. */
	private static float measure(String text, Weight weight) {
		return Minecraft.getInstance().font.width(component(text, weight));
	}

	private static float width(String text, Weight weight) {
		return row(text, weight).width();
	}

	/** {@code text} cut to fit {@code max} font units, with an ellipsis. */
	private static String fitted(String text, float max, Weight weight) {
		if (measure(text, weight) <= max) return text;
		String cut = text;
		while (!cut.isEmpty() && measure(cut + "...", weight) > max) cut = cut.substring(0, cut.length() - 1);
		return cut.stripTrailing() + "...";
	}

	/** {@code text} in at most two rows that fit the sky's width, worked out once per line. */
	private static List<String> wrap(String text) {
		List<String> cached = WRAPS.get(text);
		if (cached != null) return cached;
		float max = WRAP * fontCap;
		List<String> rows = new ArrayList<>();
		StringBuilder row = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = row.isEmpty() ? word : row + " " + word;
			if (row.isEmpty() || measure(next, Weight.BOLD) <= max) {
				row = new StringBuilder(next);
			} else {
				rows.add(row.toString());
				row = new StringBuilder(word);
			}
		}
		if (!row.isEmpty()) rows.add(row.toString());
		if (rows.size() > 2) rows = List.of(rows.get(0), String.join(" ", rows.subList(1, rows.size())));
		List<String> fitted = new ArrayList<>();
		for (String each : rows) fitted.add(fitted(each, max, Weight.BOLD));
		List<String> result = List.copyOf(fitted);
		WRAPS.put(text, result);
		return result;
	}

	/**
	 * A soft glow behind a box like CSS's box-shadow: bands of a rounded box growing outwards, fading by
	 * a Gaussian falloff over {@code blur}.
	 */
	private static void glow(float x1, float y1, float x2, float y2, float r, float blur, int color, float opacity) {
		BufferBuilder builder = BUILDERS.get(0);
		float sigma = blur / 2f;
		int base = (color >>> 24) & 255;
		int rgb = color & 0xFFFFFF;
		for (int step = 0; step < GLOW_STEPS.length; step++) {
			float grow = GLOW_STEPS[step] * sigma;
			float alpha = (float) (0.5 * erfc(grow / (sigma * Math.sqrt(2))));
			int count = ring(step % 2 == 0 ? RING_X : RING_X2, step % 2 == 0 ? RING_Y : RING_Y2, x1, y1, x2, y2, r, grow);
			if (step == 0) {
				float cx = (x1 + x2) / 2f;
				float cy = (y1 + y2) / 2f;
				int c = argb(base, rgb, alpha * opacity);
				for (int i = 0; i < count; i += 2) {
					int b = (i + 1) % count;
					int d = (i + 2) % count;
					MAPPER.quadColor(builder, cx, cy, c, RING_X[i], RING_Y[i], c, RING_X[b], RING_Y[b], c, RING_X[d], RING_Y[d], c);
				}
			} else {
				float[] ax = step % 2 == 0 ? RING_X2 : RING_X;
				float[] ay = step % 2 == 0 ? RING_Y2 : RING_Y;
				float[] bx = step % 2 == 0 ? RING_X : RING_X2;
				float[] by = step % 2 == 0 ? RING_Y : RING_Y2;
				float previous = (float) (0.5 * erfc(GLOW_STEPS[step - 1] * sigma / (sigma * Math.sqrt(2))));
				int ca = argb(base, rgb, previous * opacity);
				int cb = argb(base, rgb, alpha * opacity);
				for (int i = 0; i < count; i++) {
					int j = (i + 1) % count;
					MAPPER.quadColor(builder, ax[i], ay[i], ca, bx[i], by[i], cb, bx[j], by[j], cb, ax[j], ay[j], ca);
				}
			}
		}
	}

	private static int argb(int baseAlpha, int rgb, float alpha) {
		int a = Math.round(baseAlpha * Math.max(0, Math.min(1, alpha)));
		return a << 24 | rgb;
	}

	/** The outline of a rounded box grown by {@code grow}, clockwise; returns the point count. */
	private static int ring(float[] px, float[] py, float x1, float y1, float x2, float y2, float r, float grow) {
		float gx1 = x1 - grow;
		float gy1 = y1 - grow;
		float gx2 = x2 + grow;
		float gy2 = y2 + grow;
		if (gx2 < gx1) gx1 = gx2 = (x1 + x2) / 2f;
		if (gy2 < gy1) gy1 = gy2 = (y1 + y2) / 2f;
		float rr = Math.max(0, Math.min(r + grow, Math.min(gx2 - gx1, gy2 - gy1) / 2f));
		int segments = 5;
		int n = 0;
		for (int corner = 0; corner < 4; corner++) {
			float cx = corner == 0 || corner == 3 ? gx1 + rr : gx2 - rr;
			float cy = corner < 2 ? gy1 + rr : gy2 - rr;
			double start = Math.PI * (1 + corner * 0.5);
			for (int i = 0; i <= segments; i++) {
				double a = start + Math.PI / 2 * i / segments;
				px[n] = cx + (float) Math.cos(a) * rr;
				py[n] = cy + (float) Math.sin(a) * rr;
				n++;
			}
		}
		return n;
	}

	private static double erfc(double x) {
		double z = Math.abs(x);
		double t = 1 / (1 + 0.5 * z);
		double r = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806
				+ t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
		return x >= 0 ? r : 2 - r;
	}

	private static void begin() {
		TEXTURES.clear();
		BUILDERS.clear();
		PIPELINES.clear();
		TEXTURES.add(null);
		BUILDERS.add(SkyMesh.begin(0, DefaultVertexFormat.POSITION_COLOR));
		PIPELINES.add(SkyMesh.glow());
	}

	/** The builder for glyphs on {@code glyph}'s texture, started the first time it's needed this frame. */
	private static BufferBuilder builderFor(TextRenderable glyph) {
		return builderFor(glyph.textureView(), glyph.guiPipeline() == RenderPipelines.GUI_TEXT_INTENSITY ? SkyMesh.textIntensity() : SkyMesh.text());
	}

	private static BufferBuilder builderFor(GpuTextureView texture, RenderPipeline pipeline) {
		for (int i = 1; i < TEXTURES.size(); i++) {
			if (TEXTURES.get(i) == texture) return BUILDERS.get(i);
		}
		if (BUILDERS.size() >= 6) return null;
		TEXTURES.add(texture);
		BufferBuilder builder = SkyMesh.begin(BUILDERS.size(), DefaultVertexFormat.POSITION_TEX_COLOR);
		BUILDERS.add(builder);
		PIPELINES.add(pipeline);
		return builder;
	}

	private static void finish() {
		for (int i = 0; i < BUILDERS.size(); i++) SkyMesh.draw(BUILDERS.get(i), PIPELINES.get(i), TEXTURES.get(i));
		BUILDERS.clear();
	}

	/**
	 * Places 2D points (font units, y down) on the sky, and takes glyphs from Minecraft's font there: each
	 * vertex gets its karaoke colour from how far across it is, and fades out near the horizon.
	 */
	private static final class Mapper implements VertexConsumer, Font.GlyphVisitor {
		private float ox;
		private float oy;
		private float oz;
		private float rx;
		private float ry;
		private float rz;
		private float ux;
		private float uy;
		private float uz;
		private float scale;
		private float baseX;
		private float baseY;
		private float glyphScale;
		private int lit;
		private int dim;
		private float litWidth;
		private float opacity;
		private float lastU;
		private float lastFade;
		private VertexConsumer target;

		/**
		 * Puts the text's origin {@code yaw}/{@code elevation} degrees away, facing you; {@code scale} is world units
		 * per font unit, and {@code tilt} (degrees) leans the text as if hung on the sky: rolled a little and its
		 * plane turned so one end is further away.
		 */
		void place(float yaw, float elevation, float scale, float tilt) {
			double y = Math.toRadians(yaw);
			double e = Math.toRadians(elevation);
			float fx = (float) (-Math.sin(y) * Math.cos(e));
			float fy = (float) Math.sin(e);
			float fz = (float) (Math.cos(y) * Math.cos(e));
			ox = fx * DISTANCE;
			oy = fy * DISTANCE;
			oz = fz * DISTANCE;
			float rl = (float) Math.sqrt(fz * fz + fx * fx);
			float bx = -fz / rl;
			float bz = fx / rl;
			float cx = -(bz * fy);
			float cy = bz * fx - bx * fz;
			float cz = bx * fy;
			float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
			cx /= cl;
			cy /= cl;
			cz /= cl;
			double roll = Math.toRadians(-tilt);
			float cr = (float) Math.cos(roll);
			float sr = (float) Math.sin(roll);
			float r1x = bx * cr + cx * sr;
			float r1y = cy * sr;
			float r1z = bz * cr + cz * sr;
			ux = cx * cr - bx * sr;
			uy = cy * cr;
			uz = cz * cr - bz * sr;
			double turn = Math.toRadians(tilt * 1.6);
			float ct = (float) Math.cos(turn);
			float st = (float) Math.sin(turn);
			rx = r1x * ct + fx * st;
			ry = r1y * ct + fy * st;
			rz = r1z * ct + fz * st;
			this.scale = scale;
		}

		void glyphs(float x, float y, float glyphScale, int lit, int dim, float litWidth, float opacity) {
			this.baseX = x;
			this.baseY = y;
			this.glyphScale = glyphScale;
			this.lit = lit;
			this.dim = dim;
			this.litWidth = litWidth;
			this.opacity = opacity;
		}

		private float fade(float wy, float distance) {
			float elevation = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, wy / distance))));
			float t = Math.max(0, Math.min(1, (elevation - 1.5f) / 7f));
			return t * t * (3 - 2 * t);
		}

		void quadColor(BufferBuilder builder, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4,
				int c4) {
			vertexColor(builder, x1, y1, c1);
			vertexColor(builder, x2, y2, c2);
			vertexColor(builder, x3, y3, c3);
			vertexColor(builder, x4, y4, c4);
		}

		/** A square picture {@code size} wide with its top left at ({@code x}, {@code y}). */
		void quadTexture(BufferBuilder builder, float x, float y, float size, int color) {
			vertexTexture(builder, x, y, 0, 0, color);
			vertexTexture(builder, x, y + size, 0, 1, color);
			vertexTexture(builder, x + size, y + size, 1, 1, color);
			vertexTexture(builder, x + size, y, 1, 0, color);
		}

		private void vertexTexture(BufferBuilder builder, float x, float y, float u, float v, int color) {
			float wx = ox + (rx * x - ux * y) * scale;
			float wy = oy + (ry * x - uy * y) * scale;
			float wz = oz + (rz * x - uz * y) * scale;
			float fade = fade(wy, (float) Math.sqrt(wx * wx + wy * wy + wz * wz));
			int a = Math.round(((color >>> 24) & 255) * fade);
			builder.addVertex(wx, wy, wz).setUv(u, v).setColor(a << 24 | (color & 0xFFFFFF));
		}

		private void vertexColor(BufferBuilder builder, float x, float y, int color) {
			float wx = ox + (rx * x - ux * y) * scale;
			float wy = oy + (ry * x - uy * y) * scale;
			float wz = oz + (rz * x - uz * y) * scale;
			float fade = fade(wy, (float) Math.sqrt(wx * wx + wy * wy + wz * wz));
			int a = Math.round(((color >>> 24) & 255) * fade);
			builder.addVertex(wx, wy, wz).setColor(a << 24 | (color & 0xFFFFFF));
		}

		@Override
		public void acceptGlyph(TextRenderable.Styled glyph) {
			BufferBuilder builder = builderFor(glyph);
			if (builder == null) return;
			target = builder;
			glyph.render(IDENTITY, this, SkyMesh.FULL_BRIGHT, false);
			target = null;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			float u = baseX + x * glyphScale;
			float v = baseY + y * glyphScale;
			lastU = u - baseX;
			float wx = ox + (rx * u - ux * v) * scale;
			float wy = oy + (ry * u - uy * v) * scale;
			float wz = oz + (rz * u - uz * v) * scale;
			lastFade = fade(wy, (float) Math.sqrt(wx * wx + wy * wy + wz * wz));
			target.addVertex(wx, wy, wz);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			return setColor(a << 24 | r << 16 | g << 8 | b);
		}

		@Override
		public VertexConsumer setColor(int ignored) {
			float soft = fontCap * 0.6f * glyphScale;
			float t = litWidth == Float.MAX_VALUE ? 1 : Math.max(0, Math.min(1, (litWidth * glyphScale - lastU) / soft + 0.5f));
			int color = mix(dim, lit, t);
			int a = Math.round(((color >>> 24) & 255) * Math.max(0, Math.min(1, opacity)) * lastFade);
			target.setColor(a << 24 | (color & 0xFFFFFF));
			return this;
		}

		static int mix(int a, int b, float t) {
			int out = 0;
			for (int shift = 0; shift <= 24; shift += 8) {
				int ca = (a >>> shift) & 255;
				int cb = (b >>> shift) & 255;
				out |= (Math.round(ca + (cb - ca) * t) & 255) << shift;
			}
			return out;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			target.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}
	}
}
