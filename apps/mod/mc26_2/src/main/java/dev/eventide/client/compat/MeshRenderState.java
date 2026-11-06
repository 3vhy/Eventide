package dev.eventide.client.compat;

import dev.eventide.client.ui.VertexArena;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;

/**
 * One of Eventide's shapes for Minecraft's GUI renderer: a batch of quads, each corner with its own colour
 * (and texture coordinates when textured). Smooth boxes, gradients, glows and icons are all made of
 * these. Their vertices live in the frame's {@link VertexArena}. Minecraft 26.2 version.
 */
record MeshRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, VertexArena.Block block, int offset, int count,
		boolean textured, ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
	@Override
	public void buildVertices(VertexConsumer consumer) {
		float[] xy = block.xy;
		float[] uv = block.uv;
		int[] colors = block.colors;
		for (int i = offset, end = offset + count; i < end; i++) {
			VertexConsumer vertex = consumer.addVertexWith2DPose(pose, xy[2 * i], xy[2 * i + 1]);
			if (textured) vertex.setUv(uv[2 * i], uv[2 * i + 1]);
			vertex.setColor(colors[i]);
		}
	}
}
