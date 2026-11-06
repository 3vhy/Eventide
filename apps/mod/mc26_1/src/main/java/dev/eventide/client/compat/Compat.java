package dev.eventide.client.compat;

import java.util.function.Consumer;
import dev.eventide.client.Draw;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import dev.eventide.client.hud.HudOverride;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Minecraft 26.1 / Fabric API (official names) glue. */
public final class Compat {
	private Compat() {
	}

	public static KeyMapping registerKey(KeyMapping key) {
		return KeyMappingHelper.registerKeyMapping(key);
	}

	public static void registerHud(Identifier id, Consumer<Draw> drawer) {
		HudElementRegistry.addLast(id, (graphics, deltaTracker) -> drawer.accept(new DrawImpl(graphics)));
	}

	public static void addWidget(Screen screen, AbstractWidget widget) {
		Screens.getWidgets(screen).add(widget);
	}

	public static void chat(Component message) {
		Minecraft.getInstance().gui.getChat().addClientSystemMessage(message);
	}

	/** The screen that's open, or null while playing. */
	public static Screen screen() {
		return Minecraft.getInstance().screen;
	}

	/** Opens {@code screen}, or goes back to the game when it's null. */
	public static void setScreen(Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	/** Whether a loading overlay (a resource reload, say) covers the game. */
	public static boolean overlayShown() {
		return Minecraft.getInstance().getOverlay() != null;
	}

	/** Whether F1 has hidden the HUD. */
	public static boolean hudHidden() {
		return Minecraft.getInstance().options.hideGui;
	}

	public static void setHudHidden(boolean hidden) {
		Minecraft.getInstance().options.hideGui = hidden;
	}

	public static net.minecraft.client.Camera camera() {
		return Minecraft.getInstance().gameRenderer.getMainCamera();
	}

	/** The render target the world and the GUI are drawn into. */
	public static com.mojang.blaze3d.pipeline.RenderTarget mainTarget() {
		return Minecraft.getInstance().getMainRenderTarget();
	}

	public static net.minecraft.client.gui.components.ChatComponent chatBox() {
		return Minecraft.getInstance().gui.getChat();
	}

	public static net.minecraft.client.gui.components.BossHealthOverlay bossOverlay() {
		return Minecraft.getInstance().gui.getBossOverlay();
	}

	public static net.minecraft.client.gui.components.PlayerTabOverlay tabList() {
		return Minecraft.getInstance().gui.getTabList();
	}

	/** The sidebar slot a team's colour shows, or null if it has none. */
	public static net.minecraft.world.scores.DisplaySlot teamSidebar(net.minecraft.world.scores.PlayerTeam team) {
		return net.minecraft.world.scores.DisplaySlot.teamColorToSlot(team.getColor());
	}

	public static net.minecraft.client.gui.components.toasts.ToastManager toasts() {
		return Minecraft.getInstance().getToastManager();
	}

	/** The HUD icon of a status effect. */
	public static Identifier mobEffectSprite(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
		return net.minecraft.client.gui.Gui.getMobEffectSprite(effect);
	}

	/** Applies a changed GUI scale right away, like resizing the window does. */
	public static void resizeGui(Minecraft mc) {
		mc.resizeGui();
	}

	/** Minecraft's Options screen, opened from a menu (not from inside a world). */
	public static Screen optionsScreen(Screen parent) {
		return new OptionsScreen(parent, Minecraft.getInstance().options, false);
	}

	/** Draws in place of one of Minecraft's HUD pieces, e.g. the crosshair or the scoreboard. */
	public static void replaceHud(Identifier id, HudOverride override) {
		HudElementRegistry.replaceElement(id, original -> (graphics, deltaTracker) ->
				override.draw(new DrawImpl(graphics), () -> original.extractRenderState(graphics, deltaTracker)));
	}

	public static List<AbstractWidget> widgets(Screen screen) {
		return Screens.getWidgets(screen);
	}

	public static UUID profileId(GameProfile profile) {
		return profile == null ? null : profile.id();
	}

	public static String profileName(GameProfile profile) {
		return profile == null ? null : profile.name();
	}

	/**
	 * Uploads a PNG as a texture under {@code id}, replacing (and freeing) what was there. Returns the
	 * image's {width, height}, or null if it couldn't be read. Render thread only.
	 */
	public static int[] uploadTexture(Identifier id, byte[] png) {
		try {
			NativeImage image = NativeImage.read(png);
			int[] size = {image.getWidth(), image.getHeight()};
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Eventide " + id, image));
			return size;
		} catch (Exception e) {
			dev.eventide.client.EventideClient.LOG.warn("Couldn't load a texture for {}", id, e);
			return null;
		}
	}

	/** Draws on top of a screen after it has drawn itself. */
	public static void afterScreen(Screen screen, Consumer<Draw> drawer) {
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register(
				(drawn, graphics, mouseX, mouseY, delta) -> drawer.accept(new DrawImpl(graphics)));
	}

	/** Draws over a screen after it, with the mouse position. */
	public static void overlay(Screen screen, Draw.Overlay overlay) {
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register(
				(drawn, graphics, mouseX, mouseY, delta) -> overlay.draw(new DrawImpl(graphics), mouseX, mouseY));
	}

	/**
	 * A pipeline for Eventide's meshes drawn during the sky pass: blended, two-sided, and with no depth test
	 * or depth write, so the world drawn afterwards covers them like it covers the sky.
	 */
	public static com.mojang.blaze3d.pipeline.RenderPipeline skyPipeline(String name, Identifier shader,
			com.mojang.blaze3d.vertex.VertexFormat format, boolean textured, String define) {
		com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder = com.mojang.blaze3d.pipeline.RenderPipeline
				.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_PROJECTION_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/" + name))
				.withVertexShader(shader)
				.withFragmentShader(shader)
				.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
				.withDepthStencilState(java.util.Optional.empty())
				.withCull(false)
				.withVertexFormat(format, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS);
		if (textured) builder.withSampler("Sampler0");
		if (define != null) builder.withShaderDefine(define);
		return builder.build();
	}

	/**
	 * A pipeline for the Skies module: a full-screen quad (positions already on screen) with its own vertex and
	 * fragment shader, blended, with no depth test. {@code blit} ones sample Sampler0 and need no matrices.
	 */
	public static com.mojang.blaze3d.pipeline.RenderPipeline livingSkyPipeline(String name, Identifier vertex, Identifier fragment, boolean blit) {
		com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder = blit ? com.mojang.blaze3d.pipeline.RenderPipeline.builder()
				: com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_PROJECTION_SNIPPET);
		builder.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/" + name))
				.withVertexShader(vertex)
				.withFragmentShader(fragment)
				.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
				.withDepthStencilState(java.util.Optional.empty())
				.withCull(false)
				.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS);
		if (blit) builder.withSampler("Sampler0");
		return builder.build();
	}

	private static net.minecraft.client.renderer.rendertype.RenderType knifeShaded;

	/**
	 * Eventide's own shading for knives and gloves (shaders/core/knife): reflections, highlights, finishes and
	 * wear worked out per pixel from what each vertex says it's made of. One render type, so a hand and
	 * the knife in it draw together.
	 */
	public static net.minecraft.client.renderer.rendertype.RenderType knifeShaded() {
		if (knifeShaded == null) {
			com.mojang.blaze3d.pipeline.RenderPipeline pipeline = com.mojang.blaze3d.pipeline.RenderPipeline
					.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/knife"))
					.withVertexShader(Identifier.fromNamespaceAndPath("eventide", "core/knife"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("eventide", "core/knife"))
					.withSampler("Sampler2")
					.withDepthStencilState(com.mojang.blaze3d.pipeline.DepthStencilState.DEFAULT)
					.withCull(false)
					.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.ENTITY, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
					.build();
			knifeShaded = net.minecraft.client.renderer.rendertype.RenderType.create("eventide_knife",
					net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline).useLightmap().createRenderSetup());
		}
		return knifeShaded;
	}

	/** How a knife's parts are drawn: lit and two-sided, or glowing and see-through for energy blades. */
	public static net.minecraft.client.renderer.rendertype.RenderType knifeRenderType(Identifier texture, boolean glow) {
		return glow ? net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture)
				: net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture);
	}

	private static net.minecraft.client.renderer.ProjectionMatrixBuffer floatingProjection;

	/** A projection matrix uploaded for drawing, for Floating mode's panel (its own field of view). Render thread only. */
	public static com.mojang.blaze3d.buffers.GpuBufferSlice projectionBuffer(org.joml.Matrix4f matrix) {
		if (floatingProjection == null) floatingProjection = new net.minecraft.client.renderer.ProjectionMatrixBuffer("Eventide floating menu");
		return floatingProjection.getBuffer(matrix);
	}

	private static net.minecraft.client.renderer.rendertype.RenderType knifeGlow;

	/**
	 * The soft shell of light round a knife (shaders/core/knife_glow): added on top of what's behind it,
	 * brightest where it's seen edge-on, two-sided and leaving the depth alone.
	 */
	public static net.minecraft.client.renderer.rendertype.RenderType knifeGlow() {
		if (knifeGlow == null) {
			com.mojang.blaze3d.pipeline.RenderPipeline pipeline = com.mojang.blaze3d.pipeline.RenderPipeline
					.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/knife_glow"))
					.withVertexShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.ADDITIVE))
					.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))
					.withCull(false)
					.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
					.build();
			knifeGlow = net.minecraft.client.renderer.rendertype.RenderType.create("eventide_knife_glow",
					net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline).createRenderSetup());
		}
		return knifeGlow;
	}

	private static net.minecraft.client.renderer.rendertype.RenderType knifeOutline;

	/**
	 * The glow shader drawing only the far side of a shell (its OUTLINE define), added on top: a soft glow round a
	 * knife's silhouette that never lies over the knife itself.
	 */
	public static net.minecraft.client.renderer.rendertype.RenderType knifeOutline() {
		if (knifeOutline == null) {
			com.mojang.blaze3d.pipeline.RenderPipeline pipeline = com.mojang.blaze3d.pipeline.RenderPipeline
					.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/knife_outline"))
					.withVertexShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withShaderDefine("OUTLINE")
					.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.ADDITIVE))
					.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))
					.withCull(false)
					.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
					.build();
			knifeOutline = net.minecraft.client.renderer.rendertype.RenderType.create("eventide_knife_outline",
					net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline).createRenderSetup());
		}
		return knifeOutline;
	}

	private static net.minecraft.client.renderer.rendertype.RenderType knifeLine;

	/**
	 * A crisp line of colour round a silhouette (the glow shader's OUTLINE and LINE defines): the far faces of a
	 * slightly bigger shell, blended over what's behind it so the colour holds on bright skies too.
	 */
	public static net.minecraft.client.renderer.rendertype.RenderType knifeLine() {
		if (knifeLine == null) {
			com.mojang.blaze3d.pipeline.RenderPipeline pipeline = com.mojang.blaze3d.pipeline.RenderPipeline
					.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/knife_line"))
					.withVertexShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withShaderDefine("OUTLINE")
					.withShaderDefine("LINE")
					.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))
					.withCull(false)
					.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
					.build();
			knifeLine = net.minecraft.client.renderer.rendertype.RenderType.create("eventide_knife_line",
					net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline).createRenderSetup());
		}
		return knifeLine;
	}

	/** Something drawn into the world each frame: {@code pose} is relative to the camera's position. */
	public interface WorldHook {
		void draw(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector);
	}

	/** Calls {@code hook} each frame once the world's entities are gathered, to add Eventide's own shapes to the world. */
	public static void onWorldDraw(WorldHook hook) {
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(context ->
				hook.draw(context.poseStack(), context.submitNodeCollector()));
	}

	private static net.minecraft.client.renderer.rendertype.RenderType worldFx;

	/** Flat coloured shapes in the world (rings on the ground): blended, hidden behind blocks, leaving the depth alone. */
	public static net.minecraft.client.renderer.rendertype.RenderType worldFx() {
		if (worldFx == null) {
			com.mojang.blaze3d.pipeline.RenderPipeline pipeline = com.mojang.blaze3d.pipeline.RenderPipeline
					.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/world_fx"))
					.withVertexShader(Identifier.withDefaultNamespace("core/position_color"))
					.withFragmentShader(Identifier.withDefaultNamespace("core/position_color"))
					.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))
					.withCull(false)
					.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
					.build();
			worldFx = net.minecraft.client.renderer.rendertype.RenderType.create("eventide_world_fx",
					net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline).createRenderSetup());
		}
		return worldFx;
	}

	/** A quad builder writing into {@code memory}. */
	public static com.mojang.blaze3d.vertex.BufferBuilder quads(com.mojang.blaze3d.vertex.ByteBufferBuilder memory,
			com.mojang.blaze3d.vertex.VertexFormat format) {
		return new com.mojang.blaze3d.vertex.BufferBuilder(memory, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, format);
	}

	/** The model-view matrix Minecraft has set up for what it's drawing now (in the sky pass, the camera's rotation). */
	public static org.joml.Matrix4f modelView() {
		return new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
	}

	/** Whether a glyph comes from a one-channel (coverage) atlas, as TrueType fonts like Inter do. */
	public static boolean grayscaleGlyph(com.mojang.blaze3d.pipeline.RenderPipeline guiPipeline) {
		return guiPipeline == net.minecraft.client.renderer.RenderPipelines.GUI_TEXT_INTENSITY;
	}

	/** A perspective projection set up the way Minecraft sets up its own. */
	public static org.joml.Matrix4f perspective(org.joml.Matrix4f out, float fovRadians, float aspect, float near, float far) {
		return out.setPerspective(fovRadians, aspect, near, far);
	}

	/** A colour texture Eventide draws into and then samples. */
	public static com.mojang.blaze3d.textures.GpuTexture colorTarget(String label, int width, int height) {
		return com.mojang.blaze3d.systems.RenderSystem.getDevice().createTexture(() -> label,
				com.mojang.blaze3d.textures.GpuTexture.USAGE_RENDER_ATTACHMENT | com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING,
				com.mojang.blaze3d.textures.TextureFormat.RGBA8, width, height, 1, 1);
	}

	/**
	 * A built quad mesh drawn in one render pass into {@code color}, tested against {@code depth} when one is
	 * given: pick a pipeline, bind its textures, then draw runs of quads. Render thread only.
	 */
	public static final class QuadPass implements AutoCloseable {
		private final com.mojang.blaze3d.systems.RenderPass pass;
		private final com.mojang.blaze3d.buffers.GpuBuffer vertices;
		private final com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer indices;
		private final com.mojang.blaze3d.buffers.GpuBuffer indexBuffer;
		private final int indexCount;

		public QuadPass(String label, com.mojang.blaze3d.vertex.MeshData mesh, com.mojang.blaze3d.textures.GpuTextureView color,
				com.mojang.blaze3d.textures.GpuTextureView depth) {
			indexCount = mesh.drawState().indexCount();
			vertices = mesh.drawState().format().uploadImmediateVertexBuffer(mesh.vertexBuffer());
			indices = com.mojang.blaze3d.systems.RenderSystem.getSequentialBuffer(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS);
			indexBuffer = indices.getBuffer(indexCount);
			com.mojang.blaze3d.systems.CommandEncoder encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
			pass = depth == null ? encoder.createRenderPass(() -> label, color, java.util.OptionalInt.empty())
					: encoder.createRenderPass(() -> label, color, java.util.OptionalInt.empty(), depth, java.util.OptionalDouble.empty());
		}

		/** Draws what follows with {@code pipeline}, its DynamicTransforms set to {@code transforms}. */
		public void pipeline(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, com.mojang.blaze3d.buffers.GpuBufferSlice transforms) {
			pass.setPipeline(pipeline);
			com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indexBuffer, indices.type());
		}

		public void texture(String name, com.mojang.blaze3d.textures.GpuTextureView view, com.mojang.blaze3d.textures.GpuSampler sampler) {
			pass.bindTexture(name, view, sampler);
		}

		/** Draws {@code count} quads, starting at quad {@code first}. */
		public void draw(int first, int count) {
			pass.drawIndexed(0, first * 6, count * 6, 1);
		}

		public void drawAll() {
			pass.drawIndexed(0, 0, indexCount, 1);
		}

		@Override
		public void close() {
			pass.close();
		}
	}

	/** Times work on the graphics card between {@link #begin} and {@link #end}; results arrive a few frames late. */
	public static final class GpuTimer {
		private final com.mojang.blaze3d.systems.GpuQuery[] queries = new com.mojang.blaze3d.systems.GpuQuery[4];
		private com.mojang.blaze3d.systems.GpuQuery running;
		private int next = 0;
		private boolean broken = false;
		private double millis = -1;

		public void begin() {
			running = null;
			if (broken || com.mojang.blaze3d.systems.TimerQuery.getInstance().isRecording()) return;
			for (int i = 0; i < queries.length; i++) {
				com.mojang.blaze3d.systems.GpuQuery done = queries[i];
				if (done == null) continue;
				java.util.OptionalLong value = done.getValue();
				if (value.isPresent()) {
					record(value.getAsLong() / 1e6);
					done.close();
					queries[i] = null;
				}
			}
			if (queries[next] != null) return;
			try {
				running = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().timerQueryBegin();
			} catch (RuntimeException e) {
				broken = true;
			}
		}

		public void end() {
			if (running == null) return;
			com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().timerQueryEnd(running);
			queries[next] = running;
			running = null;
			next = (next + 1) % queries.length;
		}

		/** Milliseconds a frame (smoothed), or -1 while unknown. */
		public double millis() {
			return millis;
		}

		private void record(double ms) {
			millis = millis < 0 ? ms : millis + (ms - millis) * 0.1;
		}
	}
}
