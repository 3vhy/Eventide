package dev.eventide.client.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuQuery;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.TimerQuery;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import dev.eventide.client.Hooks;
import dev.eventide.client.EventideClient;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.module.Modules;
import dev.eventide.client.render.ShaderPacks;
import dev.eventide.client.render.SkyMesh;
import dev.eventide.client.ui.Ease;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The Skies module's renderer. While Minecraft draws the sky, the chosen sky's shader runs once over the
 * whole screen in place of Minecraft's sky colour, sunset glow and stars: every pixel works out which way
 * it looks and asks the shader for that direction's colour, so there are no images, seams or stretching.
 * Switching sky crossfades over {@link #FADE_MS}. With Sky resolution below Full it's drawn into a smaller
 * texture first and stretched over the screen, which costs a quarter (or a sixteenth) as much.
 *
 * <p>The shader's settings ride in the transform block's texture matrix and colour slot (see
 * {@code sky_lib.glsl}), so the skies need no buffers of their own. The settings' thumbnails are the same shaders drawn small, each into
 * its own texture, with a fixed camera. With an Iris shader pack on, the pack draws the sky instead.
 */
public final class LivingSky {
	private static final long FADE_MS = 800;
	private static final Map<SkyType, RenderPipeline> PIPELINES = new EnumMap<>(SkyType.class);
	private static final Map<SkyType, SkyCanvas> THUMBS = new EnumMap<>(SkyType.class);
	private static final SkyCanvas WORLD = new SkyCanvas("Eventide sky");
	private static final SkyCanvas PREVIEW = new SkyCanvas("Eventide sky preview");
	private static final SkyCanvas MENU = new SkyCanvas("Eventide main menu sky");
	private static final Identifier MENU_ID = Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "sky/menu");
	private static boolean menuRegistered = false;
	private static final Identifier PREVIEW_ID = Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "sky/preview");
	private static RenderPipeline blit;
	private static boolean previewRegistered = false;
	private static SkyType shown = null;
	private static SkyType previous = null;
	private static long switchedAt = 0;
	private static double time = 0;
	private static long lastNanos = 0;
	private static long thumbsDrawnAt = 0;
	private static float sunAngle = 0;
	private static float rainBrightness = 1;
	private static boolean failed = false;
	private static final GpuQuery[] QUERIES = new GpuQuery[4];
	private static int query = 0;
	private static double gpuMillis = -1;
	private static boolean timing = true;

	private LivingSky() {
	}

	/** Whether the Skies module draws the sky right now (on, and no shader pack drawing it instead). */
	public static boolean active() {
		return Modules.SKIES.enabled() && !failed && !ShaderPacks.inUse();
	}

	/** Whether the End gets your sky too. */
	public static boolean activeInEnd() {
		return active() && Modules.SKIES_END.is("Your sky");
	}

	/** Whether Minecraft's sun and moon still show over the sky. */
	public static boolean sunAndMoon() {
		return Modules.SKIES_SUN_MOON.get();
	}

	public static SkyType selected() {
		return SkyType.byLabel(Modules.SKIES_SKY.get());
	}

	public static void select(SkyType sky) {
		while (!Modules.SKIES_SKY.is(sky.label)) Modules.SKIES_SKY.next();
	}

	/** What Minecraft worked out about the sky this frame: where the sun is, and how much rain hides it. */
	public static void capture(float sunAngleRadians, float rain) {
		sunAngle = sunAngleRadians;
		rainBrightness = rain;
	}

	private static void advance() {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min(0.25f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		time = (time + dt * Modules.SKIES_SPEED.get() / 100.0) % 7200.0;
		SkyType wanted = selected();
		if (shown == null) {
			shown = wanted;
		} else if (wanted != shown) {
			previous = shown;
			shown = wanted;
			switchedAt = System.currentTimeMillis();
		}
	}

	/** How far the switch to the current sky has got, 0..1 (1 once it's done). */
	private static float fade() {
		if (previous == null) return 1;
		float t = Math.min(1, (System.currentTimeMillis() - switchedAt) / (float) FADE_MS);
		if (t >= 1) previous = null;
		return Ease.IN_OUT.apply(t);
	}

	private static float sunAngleNow() {
		return switch (Modules.SKIES_TIME.get()) {
			case "Night" -> (float) Math.PI;
			case "Dusk" -> 1.42f;
			case "Day" -> -0.5f;
			default -> sunAngle;
		};
	}

	private static Vector3f sun(float angle) {
		return new Vector3f((float) -Math.sin(angle), (float) Math.cos(angle), 0.08f).normalize();
	}

	private static float daylight(Vector3f sun) {
		float t = Math.max(0, Math.min(1, (sun.y + 0.15f) / 0.5f));
		return t * t * (3 - 2 * t);
	}

	private static RenderPipeline pipeline(SkyType sky) {
		return PIPELINES.computeIfAbsent(sky, type -> Compat.livingSkyPipeline("sky_" + type.id, shader("sky_living"), shader("sky_" + type.id), false));
	}

	private static RenderPipeline blit() {
		if (blit == null) blit = Compat.livingSkyPipeline("sky_blit", shader("sky_blit"), shader("sky_blit"), true);
		return blit;
	}

	private static Identifier shader(String name) {
		return Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "core/" + name);
	}

	/**
	 * The shader's settings, as a matrix whose columns are: time, brightness, opacity, stars; tint and rain;
	 * the sun's direction and daylight; and, for thumbnails, the camera's spread and a thumbnail flag.
	 */
	private static Matrix4f params(float alpha, Vector3f sun, float day, float thumbX, float thumbY, boolean thumb) {
		int tint = Modules.SKIES_TINT.get();
		float rain = Modules.SKIES_TIME.is("World time") ? 1 - rainBrightness : 0;
		float stars = Modules.SKIES_STARS.get() ? (float) (Modules.SKIES_STAR_DENSITY.get() / 100.0) : 0;
		return new Matrix4f((float) time, (float) (Modules.SKIES_BRIGHTNESS.get() / 100.0), alpha, stars,
				((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, thumb ? 0 : rain,
				sun.x, sun.y, sun.z, day,
				thumbX, thumbY, 0, thumb ? 1 : 0);
	}

	/** The atmosphere settings the skies read from the colour slot: nebula, glow and shooting stars (0..2 each). */
	private static Vector4f atmosphere() {
		return new Vector4f((float) (Modules.SKIES_NEBULA.get() / 100.0), (float) (Modules.SKIES_GLOW.get() / 100.0),
				(float) (Modules.SKIES_METEORS.get() / 100.0), 1);
	}

	private static void pass(GpuTextureView target, RenderPipeline pipeline, Matrix4f view, Matrix4f params, GpuTextureView texture,
			SkyCanvas sampled) {
		BufferBuilder builder = SkyMesh.begin(5, DefaultVertexFormat.POSITION);
		builder.addVertex(-1, -1, 0);
		builder.addVertex(1, -1, 0);
		builder.addVertex(1, 1, 0);
		builder.addVertex(-1, 1, 0);
		MeshData mesh = builder.build();
		if (mesh == null) return;
		try {
			GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(view, texture == null ? atmosphere() : new Vector4f(1, 1, 1, 1),
					new Vector3f(), params);
			GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
			GpuBuffer indexBuffer = indices.getBuffer(mesh.drawState().indexCount());
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Eventide sky", target, OptionalInt.empty())) {
				pass.setPipeline(pipeline);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transforms);
				if (texture != null) pass.bindTexture("Sampler0", texture, sampled.getSampler());
				pass.setVertexBuffer(0, vertices);
				pass.setIndexBuffer(indexBuffer, indices.type());
				pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
			}
		} finally {
			mesh.close();
		}
	}

	/** The current sky (crossfading from the last one) drawn into {@code target}, looking the way {@code view} does. */
	private static void drawSky(GpuTextureView target, Matrix4f view, Vector3f sun, float day, float thumbX, float thumbY, boolean thumb) {
		float fade = fade();
		if (previous != null && fade < 1) {
			pass(target, pipeline(previous), view, params(1, sun, previous.day && thumb ? 1 : day, thumbX, thumbY, thumb), null, null);
			pass(target, pipeline(shown), view, params(fade, sun, shown.day && thumb ? 1 : day, thumbX, thumbY, thumb), null, null);
		} else {
			pass(target, pipeline(shown), view, params(1, sun, shown.day && thumb ? 1 : day, thumbX, thumbY, thumb), null, null);
		}
	}

	/** Draws the sky over the world's sky (from the sky pass); Minecraft's own sky colour isn't drawn. */
	public static void drawWorld() {
		GpuQuery timer = startTimer();
		try {
			drawWorldTimed();
		} finally {
			stopTimer(timer);
		}
	}

	/** How long the sky takes the graphics card a frame, in milliseconds (smoothed), or -1 if unknown. */
	public static double gpuMillis() {
		return gpuMillis;
	}

	private static GpuQuery startTimer() {
		if (!timing || TimerQuery.getInstance().isRecording()) return null;
		for (int i = 0; i < QUERIES.length; i++) {
			GpuQuery done = QUERIES[i];
			if (done == null) continue;
			java.util.OptionalLong value = done.getValue();
			if (value.isPresent()) {
				double ms = value.getAsLong() / 1e6;
				gpuMillis = gpuMillis < 0 ? ms : gpuMillis + (ms - gpuMillis) * 0.1;
				done.close();
				QUERIES[i] = null;
			}
		}
		if (QUERIES[query] != null) return null;
		try {
			return RenderSystem.getDevice().createCommandEncoder().timerQueryBegin();
		} catch (RuntimeException e) {
			timing = false;
			return null;
		}
	}

	private static void stopTimer(GpuQuery timer) {
		if (timer == null) return;
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.timerQueryEnd(timer);
		QUERIES[query] = timer;
		query = (query + 1) % QUERIES.length;
	}

	private static void drawWorldTimed() {
		try {
			advance();
			Hooks.skyDrawn = true;
			Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
			Vector3f sun = sun(sunAngleNow());
			float day = daylight(sun);
			RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
			int divisor = switch (Modules.SKIES_RESOLUTION.get()) {
				case "Half" -> 2;
				case "Quarter" -> 4;
				default -> 1;
			};
			if (divisor == 1) {
				drawSky(main.getColorTextureView(), view, sun, day, 0, 0, false);
				return;
			}
			WORLD.size(main.width / divisor, main.height / divisor);
			drawSky(WORLD.getTextureView(), view, sun, day, 0, 0, false);
			pass(main.getColorTextureView(), blit(), view, new Matrix4f(), WORLD.getTextureView(), WORLD);
		} catch (RuntimeException e) {
			failed = true;
			EventideClient.LOG.error("Couldn't draw the sky; Skies is off until the game restarts", e);
		}
	}

	private static Matrix4f lookAt(float yawDegrees, float pitchDegrees) {
		return new Matrix4f().rotateX((float) Math.toRadians(-pitchDegrees)).rotateY((float) Math.toRadians(-yawDegrees));
	}

	/** The texture a sky's thumbnail is drawn into; see {@link #drawThumbnails}. */
	public static Identifier thumbnail(SkyType sky) {
		return Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "sky/thumb_" + sky.id);
	}

	/** The texture the big preview of the current sky (crossfading as it switches) is drawn into. */
	public static Identifier preview() {
		return PREVIEW_ID;
	}

	/**
	 * Draws every sky's thumbnail ({@code width} x {@code height} pixels) and the big preview, live, for the
	 * settings; once a frame at most. Returns false if they couldn't be drawn.
	 */
	public static boolean drawThumbnails(int width, int height, int previewWidth, int previewHeight) {
		if (failed) return false;
		long now = System.nanoTime();
		if (now - thumbsDrawnAt < 8_000_000L) return true;
		thumbsDrawnAt = now;
		try {
			if (Minecraft.getInstance().level == null || !active()) advance();
			float tanHalf = (float) Math.tan(Math.toRadians(36));
			for (SkyType sky : SkyType.values()) {
				SkyCanvas canvas = THUMBS.get(sky);
				if (canvas == null) {
					canvas = new SkyCanvas("Eventide sky " + sky.id);
					THUMBS.put(sky, canvas);
					canvas.size(width, height);
					Minecraft.getInstance().getTextureManager().register(thumbnail(sky), canvas);
				}
				canvas.size(width, height);
				Vector3f sun = sun(sky.day ? -0.6f : (float) Math.PI * 0.9f);
				Matrix4f params = params(1, sun, sky.day ? 1 : 0, tanHalf * width / height, tanHalf, true);
				pass(canvas.getTextureView(), pipeline(sky), lookAt(sky.yaw, sky.pitch), params, null, null);
			}
			if (!previewRegistered) {
				PREVIEW.size(previewWidth, previewHeight);
				Minecraft.getInstance().getTextureManager().register(PREVIEW_ID, PREVIEW);
				previewRegistered = true;
			}
			PREVIEW.size(previewWidth, previewHeight);
			SkyType sky = shown == null ? selected() : shown;
			Vector3f sun = sun(sky.day ? -0.6f : (float) Math.PI * 0.9f);
			drawSky(PREVIEW.getTextureView(), lookAt(sky.yaw, sky.pitch), sun, sky.day ? 1 : 0, tanHalf * previewWidth / previewHeight, tanHalf, true);
			return true;
		} catch (RuntimeException e) {
			failed = true;
			EventideClient.LOG.error("Couldn't draw the sky thumbnails; Skies is off until the game restarts", e);
			return false;
		}
	}

	/**
	 * Draws {@code sky} as the main menu's backdrop into a {@code width} x {@code height} texture, looking
	 * {@code yaw}/{@code pitch} degrees, and returns the texture (null if it couldn't be drawn). Works with no
	 * world loaded; the Skies module needn't be on.
	 */
	public static Identifier drawMenu(SkyType sky, int width, int height, float yaw, float pitch) {
		if (failed || width < 2 || height < 2) return null;
		try {
			if (Minecraft.getInstance().level == null || !active()) advance();
			if (!menuRegistered) {
				MENU.size(width, height);
				Minecraft.getInstance().getTextureManager().register(MENU_ID, MENU);
				menuRegistered = true;
			}
			MENU.size(width, height);
			float tanHalf = (float) Math.tan(Math.toRadians(38));
			Vector3f sun = sun((float) Math.PI * 0.9f);
			pass(MENU.getTextureView(), pipeline(sky), lookAt(yaw, pitch), params(1, sun, 0, tanHalf * width / height, tanHalf, true), null, null);
			return MENU_ID;
		} catch (RuntimeException e) {
			failed = true;
			EventideClient.LOG.error("Couldn't draw the main menu's sky; it shows the panorama instead", e);
			return null;
		}
	}

	/**
	 * Tints distant fog toward the sky's horizon, so land at the edge of your render distance fades into the
	 * sky with no seam. {@code color} is Minecraft's fog colour (red, green, blue in 0..1), changed in place.
	 */
	public static void tintFog(Vector4f color) {
		if (!active() || shown == null) return;
		Vector3f sun = sun(sunAngleNow());
		float day = daylight(sun);
		float fade = fade();
		float[] now = horizon(shown, day);
		float[] was = previous == null ? now : horizon(previous, day);
		float brightness = (float) (Modules.SKIES_BRIGHTNESS.get() / 100.0);
		int tint = Modules.SKIES_TINT.get();
		float r = (was[0] + (now[0] - was[0]) * fade) * brightness * ((tint >> 16) & 255) / 255f;
		float g = (was[1] + (now[1] - was[1]) * fade) * brightness * ((tint >> 8) & 255) / 255f;
		float b = (was[2] + (now[2] - was[2]) * fade) * brightness * (tint & 255) / 255f;
		float k = 0.85f;
		color.x += (Math.min(1, r) - color.x) * k;
		color.y += (Math.min(1, g) - color.y) * k;
		color.z += (Math.min(1, b) - color.z) * k;
	}

	private static float[] horizon(SkyType sky, float day) {
		float scale = sky.day ? 0.4f + 0.6f * day : 1 + 0.45f * day;
		return new float[] {((sky.horizon >> 16) & 255) / 255f * scale, ((sky.horizon >> 8) & 255) / 255f * scale, (sky.horizon & 255) / 255f * scale};
	}
}
