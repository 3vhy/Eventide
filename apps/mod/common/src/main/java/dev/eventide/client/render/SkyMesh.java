package dev.eventide.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.eventide.client.EventideClient;
import dev.eventide.client.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Draws Eventide's own meshes into the world while Minecraft draws the sky, with pipelines that blend but
 * neither test nor write depth: everything drawn after the sky (blocks, entities, the hand, water,
 * clouds, rain) then covers or blends over them, exactly as it does the sky itself. Meshes are built
 * camera-relative, in the sky's rotation-only view. Render thread only.
 */
public final class SkyMesh {
	/** Full brightness, for vertex formats that carry a light value. */
	public static final int FULL_BRIGHT = 0xF000F0;
	private static RenderPipeline text;
	private static RenderPipeline textIntensity;
	private static RenderPipeline glow;
	private static final ByteBufferBuilder[] POOL = new ByteBufferBuilder[6];

	private SkyMesh() {
	}

	/** Glyphs from a colour font atlas, tinted by vertex colour. */
	public static RenderPipeline text() {
		if (text == null) text = Compat.skyPipeline("sky_text", shader("sky_text"), DefaultVertexFormat.POSITION_TEX_COLOR, true, null);
		return text;
	}

	/** Glyphs from a TrueType font atlas, which stores coverage in one channel (Inter is one). */
	public static RenderPipeline textIntensity() {
		if (textIntensity == null) {
			textIntensity = Compat.skyPipeline("sky_text_intensity", shader("sky_text"), DefaultVertexFormat.POSITION_TEX_COLOR, true, "INTENSITY");
		}
		return textIntensity;
	}

	/** Soft colour, for glows behind text. */
	public static RenderPipeline glow() {
		if (glow == null) glow = Compat.skyPipeline("sky_glow", shader("sky_glow"), DefaultVertexFormat.POSITION_COLOR, false, null);
		return glow;
	}

	private static Identifier shader(String name) {
		return Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "core/" + name);
	}

	/** A quad builder on one of a few reused memory blocks; {@code slot} keeps builders used at the same time apart. */
	public static BufferBuilder begin(int slot, VertexFormat format) {
		ByteBufferBuilder memory = POOL[slot];
		if (memory == null) {
			memory = new ByteBufferBuilder(16384);
			POOL[slot] = memory;
		}
		return Compat.quads(memory, format);
	}

	/** Draws what {@code builder} holds (if anything) with {@code pipeline}, sampling {@code texture} when given. */
	public static void draw(BufferBuilder builder, RenderPipeline pipeline, GpuTextureView texture) {
		MeshData mesh = builder.build();
		if (mesh == null) return;
		try {
			GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
					.writeTransform(Compat.modelView(), new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
			RenderTarget target = Compat.mainTarget();
			try (Compat.QuadPass pass = new Compat.QuadPass("Eventide sky mesh", mesh, target.getColorTextureView(), target.getDepthTextureView())) {
				pass.pipeline(pipeline, transforms);
				if (texture != null) pass.texture("Sampler0", texture, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
				pass.drawAll();
			}
		} finally {
			mesh.close();
		}
	}
}
