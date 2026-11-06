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

/** Minecraft 26.2 / Fabric API (official names) glue. */
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
		chatBox().addClientSystemMessage(message);
	}

	/** The screen that's open, or null while playing. */
	public static Screen screen() {
		return Minecraft.getInstance().gui.screen();
	}

	/** Opens {@code screen}, or goes back to the game when it's null. */
	public static void setScreen(Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}

	/** Whether a loading overlay (a resource reload, say) covers the game. */
	public static boolean overlayShown() {
		return Minecraft.getInstance().gui.overlay() != null;
	}

	/** Whether F1 has hidden the HUD. */
	public static boolean hudHidden() {
		return Minecraft.getInstance().gui.hud.isHidden();
	}

	public static void setHudHidden(boolean hidden) {
		net.minecraft.client.gui.Hud hud = Minecraft.getInstance().gui.hud;
		if (hud.isHidden() != hidden) hud.toggle();
	}

	public static net.minecraft.client.Camera camera() {
		return Minecraft.getInstance().gameRenderer.mainCamera();
	}

	/** The render target the world and the GUI are drawn into. */
	public static com.mojang.blaze3d.pipeline.RenderTarget mainTarget() {
		return Minecraft.getInstance().gameRenderer.mainRenderTarget();
	}

	public static net.minecraft.client.gui.components.ChatComponent chatBox() {
		return Minecraft.getInstance().gui.hud.getChat();
	}

	public static net.minecraft.client.gui.components.BossHealthOverlay bossOverlay() {
		return Minecraft.getInstance().gui.hud.getBossOverlay();
	}

	public static net.minecraft.client.gui.components.PlayerTabOverlay tabList() {
		return Minecraft.getInstance().gui.hud.getTabList();
	}

	/** The sidebar slot a team's colour shows, or null if it has none. */
	public static net.minecraft.world.scores.DisplaySlot teamSidebar(net.minecraft.world.scores.PlayerTeam team) {
		return team.getColor().map(net.minecraft.world.scores.TeamColor::displaySlot).orElse(null);
	}

	public static net.minecraft.client.gui.components.toasts.ToastManager toasts() {
		return Minecraft.getInstance().gui.toastManager();
	}

	/** The HUD icon of a status effect. */
	public static Identifier mobEffectSprite(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
		return net.minecraft.client.gui.Hud.getMobEffectSprite(effect);
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
				.builder()
				.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION)
				.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/" + name))
				.withVertexShader(shader)
				.withFragmentShader(shader)
				.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
				.withDepthStencilState(java.util.Optional.empty())
				.withCull(false)
				.withVertexBinding(0, format)
				.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS);
		if (textured) builder.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0);
		if (define != null) builder.withShaderDefine(define);
		return builder.build();
	}

	/**
	 * A pipeline for the Skies module: a full-screen quad (positions already on screen) with its own vertex and
	 * fragment shader, blended, with no depth test. {@code blit} ones sample Sampler0 and need no matrices.
	 */
	public static com.mojang.blaze3d.pipeline.RenderPipeline livingSkyPipeline(String name, Identifier vertex, Identifier fragment, boolean blit) {
		com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder = blit ? com.mojang.blaze3d.pipeline.RenderPipeline.builder()
				: com.mojang.blaze3d.pipeline.RenderPipeline.builder()
					.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION);
		builder.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/" + name))
				.withVertexShader(vertex)
				.withFragmentShader(fragment)
				.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
				.withDepthStencilState(java.util.Optional.empty())
				.withCull(false)
				.withVertexBinding(0, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION)
				.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS);
		if (blit) builder.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0);
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
					.builder()
					.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION)
					.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/knife"))
					.withVertexShader(Identifier.fromNamespaceAndPath("eventide", "core/knife"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("eventide", "core/knife"))
					.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER2)
					.withDepthStencilState(com.mojang.blaze3d.pipeline.DepthStencilState.DEFAULT)
					.withCull(false)
					.withVertexBinding(0, com.mojang.blaze3d.vertex.DefaultVertexFormat.ENTITY)
					.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
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
					.builder()
					.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION)
					.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/knife_glow"))
					.withVertexShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.ADDITIVE))
					.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.GREATER_THAN_OR_EQUAL, false))
					.withCull(false)
					.withVertexBinding(0, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL)
					.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
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
					.builder()
					.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION)
					.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/knife_outline"))
					.withVertexShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withShaderDefine("OUTLINE")
					.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.ADDITIVE))
					.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.GREATER_THAN_OR_EQUAL, false))
					.withCull(false)
					.withVertexBinding(0, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL)
					.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
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
					.builder()
					.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION)
					.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/knife_line"))
					.withVertexShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withShaderDefine("OUTLINE")
					.withShaderDefine("LINE")
					.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.GREATER_THAN_OR_EQUAL, false))
					.withCull(false)
					.withVertexBinding(0, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL)
					.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
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
					.builder()
					.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION)
					.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/world_fx"))
					.withVertexShader(Identifier.withDefaultNamespace("core/position_color"))
					.withFragmentShader(Identifier.withDefaultNamespace("core/position_color"))
					.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.GREATER_THAN_OR_EQUAL, false))
					.withCull(false)
					.withVertexBinding(0, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR)
					.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
					.build();
			worldFx = net.minecraft.client.renderer.rendertype.RenderType.create("eventide_world_fx",
					net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline).createRenderSetup());
		}
		return worldFx;
	}

	/** A quad builder writing into {@code memory}. */
	public static com.mojang.blaze3d.vertex.BufferBuilder quads(com.mojang.blaze3d.vertex.ByteBufferBuilder memory,
			com.mojang.blaze3d.vertex.VertexFormat format) {
		return new com.mojang.blaze3d.vertex.BufferBuilder(memory, com.mojang.blaze3d.PrimitiveTopology.QUADS, format);
	}

	/** The model-view matrix Minecraft has set up for what it's drawing now (in the sky pass, the camera's rotation). */
	public static org.joml.Matrix4f modelView() {
		return com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy();
	}

	/** Whether a glyph comes from a one-channel (coverage) atlas, as TrueType fonts like Inter do. */
	public static boolean grayscaleGlyph(com.mojang.blaze3d.pipeline.RenderPipeline guiPipeline) {
		return guiPipeline == net.minecraft.client.renderer.RenderPipelines.GUI_TEXT_GRAYSCALE;
	}

	/**
	 * A perspective projection set up the way Minecraft sets up its own. From 26.2 depth runs backwards (near is 1,
	 * far is 0), so pipelines that test depth pass when greater or equal.
	 */
	public static org.joml.Matrix4f perspective(org.joml.Matrix4f out, float fovRadians, float aspect, float near, float far) {
		return out.setPerspective(fovRadians, aspect, far, near,
				com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
	}

	/** A colour texture Eventide draws into and then samples. */
	public static com.mojang.blaze3d.textures.GpuTexture colorTarget(String label, int width, int height) {
		return com.mojang.blaze3d.systems.RenderSystem.getDevice().createTexture(() -> label,
				com.mojang.blaze3d.textures.GpuTexture.USAGE_RENDER_ATTACHMENT | com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING,
				com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, width, height, 1, 1);
	}

	/**
	 * A built quad mesh drawn in one render pass into {@code color}, tested against {@code depth} when one is
	 * given: pick a pipeline, bind its textures, then draw runs of quads. Render thread only.
	 */
	public static final class QuadPass implements AutoCloseable {
		private static final java.util.Map<String, com.mojang.blaze3d.buffers.GpuBuffer> VERTEX_BUFFERS = new java.util.HashMap<>();
		private final com.mojang.blaze3d.systems.RenderPass pass;
		private final com.mojang.blaze3d.buffers.GpuBufferSlice vertices;
		private final com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer indices;
		private final com.mojang.blaze3d.buffers.GpuBuffer indexBuffer;
		private final int indexCount;

		public QuadPass(String label, com.mojang.blaze3d.vertex.MeshData mesh, com.mojang.blaze3d.textures.GpuTextureView color,
				com.mojang.blaze3d.textures.GpuTextureView depth) {
			indexCount = mesh.drawState().indexCount();
			vertices = upload(label, mesh.vertexBuffer());
			indices = com.mojang.blaze3d.systems.RenderSystem.getSequentialBuffer(com.mojang.blaze3d.PrimitiveTopology.QUADS);
			indexBuffer = indices.getBuffer(indexCount);
			com.mojang.blaze3d.systems.CommandEncoder encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
			pass = depth == null ? encoder.createRenderPass(() -> label, color, java.util.Optional.empty())
					: encoder.createRenderPass(() -> label, color, java.util.Optional.empty(), depth, java.util.OptionalDouble.empty());
		}

		/**
		 * Copies the vertices into a buffer kept for {@code label}, grown when it's too small. Copies and draws run in
		 * order on the graphics card, so a buffer is reused safely within a frame.
		 */
		private static com.mojang.blaze3d.buffers.GpuBufferSlice upload(String label, java.nio.ByteBuffer data) {
			com.mojang.blaze3d.systems.GpuDevice device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
			int size = data.remaining();
			com.mojang.blaze3d.buffers.GpuBuffer buffer = VERTEX_BUFFERS.get(label);
			if (buffer == null || buffer.size() < size) {
				if (buffer != null) buffer.close();
				long capacity = Math.max(16384, Long.highestOneBit(size - 1L) << 1);
				buffer = device.createBuffer(() -> label + " vertices",
						com.mojang.blaze3d.buffers.GpuBuffer.USAGE_VERTEX | com.mojang.blaze3d.buffers.GpuBuffer.USAGE_COPY_DST, capacity);
				VERTEX_BUFFERS.put(label, buffer);
			}
			com.mojang.blaze3d.buffers.GpuBufferSlice slice = buffer.slice(0, size);
			device.createCommandEncoder().writeToBuffer(slice, data);
			return slice;
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
			pass.drawIndexed(count * 6, 1, first * 6, 0, 0);
		}

		public void drawAll() {
			pass.drawIndexed(indexCount, 1, 0, 0, 0);
		}

		@Override
		public void close() {
			pass.close();
		}
	}

	/** Times work on the graphics card between {@link #begin} and {@link #end}; results arrive a few frames late. */
	public static final class GpuTimer {
		private static final int SLOTS = 4;
		private com.mojang.blaze3d.systems.GpuQueryPool pool;
		private final boolean[] pending = new boolean[SLOTS];
		private boolean running = false;
		private int next = 0;
		private boolean broken = false;
		private double millis = -1;

		public void begin() {
			running = false;
			if (broken) return;
			try {
				com.mojang.blaze3d.systems.GpuDevice device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
				if (pool == null) pool = device.createTimestampQueryPool(SLOTS * 2);
				for (int i = 0; i < SLOTS; i++) {
					if (!pending[i]) continue;
					java.util.OptionalLong[] stamps = pool.getValues(i * 2, 2);
					if (stamps[0].isPresent() && stamps[1].isPresent()) {
						record((stamps[1].getAsLong() - stamps[0].getAsLong()) * (double) device.getDeviceInfo().timestampPeriod() / 1e6);
						pending[i] = false;
					}
				}
				if (pending[next]) return;
				device.createCommandEncoder().writeTimestamp(pool, next * 2);
				running = true;
			} catch (RuntimeException e) {
				broken = true;
			}
		}

		public void end() {
			if (!running) return;
			running = false;
			com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pool, next * 2 + 1);
			pending[next] = true;
			next = (next + 1) % SLOTS;
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
