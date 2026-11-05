package dev.eventide.client.compat;

import java.util.function.Consumer;
import dev.eventide.client.Draw;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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

/** Minecraft 1.21.11 / Fabric API (Yarn-era names) glue. */
public final class Compat {
	private Compat() {
	}

	public static KeyMapping registerKey(KeyMapping key) {
		return KeyBindingHelper.registerKeyBinding(key);
	}

	public static void registerHud(Identifier id, Consumer<Draw> drawer) {
		HudElementRegistry.addLast(id, (graphics, deltaTracker) -> drawer.accept(new DrawImpl(graphics)));
	}

	public static void addWidget(Screen screen, AbstractWidget widget) {
		Screens.getButtons(screen).add(widget);
	}

	public static void chat(Component message) {
		Minecraft.getInstance().gui.getChat().addMessage(message);
	}

	/** Applies a changed GUI scale right away, like resizing the window does. */
	public static void resizeGui(Minecraft mc) {
		mc.resizeDisplay();
	}

	/** Minecraft's Options screen, opened from a menu (not from inside a world). */
	public static Screen optionsScreen(Screen parent) {
		return new OptionsScreen(parent, Minecraft.getInstance().options);
	}

	/** Draws in place of one of Minecraft's HUD pieces, e.g. the crosshair or the scoreboard. */
	public static void replaceHud(Identifier id, HudOverride override) {
		HudElementRegistry.replaceElement(id, original -> (graphics, deltaTracker) ->
				override.draw(new DrawImpl(graphics), () -> original.render(graphics, deltaTracker)));
	}

	public static List<AbstractWidget> widgets(Screen screen) {
		return Screens.getButtons(screen);
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
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register(
				(drawn, graphics, mouseX, mouseY, delta) -> drawer.accept(new DrawImpl(graphics)));
	}

	/** Draws over a screen after it, with the mouse position. */
	public static void overlay(Screen screen, Draw.Overlay overlay) {
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register(
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
				.withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
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
				.withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
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
					.withCull(false)
					.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
					.build();
			knifeShaded = net.minecraft.client.renderer.rendertype.RenderType.create("eventide_knife",
					net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline).useLightmap().createRenderSetup());
		}
		return knifeShaded;
	}

	/** How a knife's parts are drawn: lit and two-sided, or glowing and see-through for energy blades. */
	public static net.minecraft.client.renderer.rendertype.RenderType knifeRenderType(Identifier texture, boolean glow) {
		return glow ? net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture)
				: net.minecraft.client.renderer.rendertype.RenderTypes.entityCutoutNoCull(texture);
	}

	private static net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer floatingProjection;

	/** A projection matrix uploaded for drawing, for Floating mode's panel (its own field of view). Render thread only. */
	public static com.mojang.blaze3d.buffers.GpuBufferSlice projectionBuffer(org.joml.Matrix4f matrix) {
		if (floatingProjection == null) floatingProjection = new net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer("Eventide floating menu");
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
					.withBlend(com.mojang.blaze3d.pipeline.BlendFunction.ADDITIVE)
					.withDepthWrite(false)
					.withCull(false)
					.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
					.build();
			knifeGlow = net.minecraft.client.renderer.rendertype.RenderType.create("eventide_knife_glow",
					net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline).createRenderSetup());
		}
		return knifeGlow;
	}

	private static net.minecraft.client.renderer.rendertype.RenderType knifeOutline;

	/** The glow shader drawing only far faces (its OUTLINE define), for a line of light round a box's silhouette. */
	public static net.minecraft.client.renderer.rendertype.RenderType knifeOutline() {
		if (knifeOutline == null) {
			com.mojang.blaze3d.pipeline.RenderPipeline pipeline = com.mojang.blaze3d.pipeline.RenderPipeline
					.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath("eventide", "pipeline/knife_outline"))
					.withVertexShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("eventide", "core/knife_glow"))
					.withShaderDefine("OUTLINE")
					.withBlend(com.mojang.blaze3d.pipeline.BlendFunction.ADDITIVE)
					.withDepthWrite(false)
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
					.withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
					.withDepthWrite(false)
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
		net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AFTER_ENTITIES.register(context ->
				hook.draw(context.matrices(), context.commandQueue()));
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
					.withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
					.withDepthWrite(false)
					.withCull(false)
					.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
					.build();
			worldFx = net.minecraft.client.renderer.rendertype.RenderType.create("eventide_world_fx",
					net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline).createRenderSetup());
		}
		return worldFx;
	}
}
