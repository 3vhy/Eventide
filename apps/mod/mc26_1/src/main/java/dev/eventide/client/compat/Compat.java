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

	/** The glow shader drawing only far faces (its OUTLINE define), for a line of light round a box's silhouette. */
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
}
