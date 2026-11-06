package dev.eventide.client.compat;

import dev.eventide.client.ui.VertexArena;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.eventide.client.Draw;
import java.util.ArrayDeque;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

/** Draw for Minecraft 26.1 (GuiGraphicsExtractor). */
public final class DrawImpl implements Draw {
	private final GuiGraphicsExtractor graphics;
	/** The scissor rectangles in screen space, so Eventide's own shapes are clipped like Minecraft's. */
	private final ArrayDeque<ScreenRectangle> scissors = new ArrayDeque<>();

	public DrawImpl(GuiGraphicsExtractor graphics) {
		this.graphics = graphics;
	}

	@Override
	public int width() {
		return graphics.guiWidth();
	}

	@Override
	public int height() {
		return graphics.guiHeight();
	}

	@Override
	public void fill(int x1, int y1, int x2, int y2, int argb) {
		graphics.fill(x1, y1, x2, y2, argb);
	}

	@Override
	public void text(String text, int x, int y, int argb, boolean shadow) {
		graphics.text(Minecraft.getInstance().font, text, x, y, argb, shadow);
	}

	@Override
	public void text(Component text, int x, int y, int argb, boolean shadow) {
		graphics.text(Minecraft.getInstance().font, text, x, y, argb, shadow);
	}

	public GuiGraphicsExtractor graphics() {
		return graphics;
	}

	@Override
	public void item(ItemStack stack, int x, int y) {
		graphics.fakeItem(stack, x, y);
	}

	@Override
	public void entity(net.minecraft.world.entity.LivingEntity entity, int x0, int y0, int x1, int y1, int scale, float lookX, float lookY) {
		net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x0, y0, x1, y1, scale, 0.0625f, lookX, lookY, entity);
	}

	@Override
	public void itemOverlay(ItemStack stack, int x, int y, String countLabel) {
		graphics.itemDecorations(Minecraft.getInstance().font, stack, x, y, countLabel);
	}

	@Override
	public void push() {
		graphics.pose().pushMatrix();
	}

	@Override
	public void pop() {
		graphics.pose().popMatrix();
	}

	@Override
	public void translate(float x, float y) {
		graphics.pose().translate(x, y);
	}

	@Override
	public void scale(float scale) {
		graphics.pose().scale(scale, scale);
	}

	@Override
	public void scale(float x, float y) {
		graphics.pose().scale(x, y);
	}

	@Override
	public boolean sharpText() {
		return true;
	}

	@Override
	public void enableScissor(int x1, int y1, int x2, int y2) {
		graphics.enableScissor(x1, y1, x2, y2);
		ScreenRectangle rect = new ScreenRectangle(x1, y1, x2 - x1, y2 - y1).transformAxisAligned(graphics.pose());
		if (!scissors.isEmpty()) rect = Objects.requireNonNullElse(rect.intersection(scissors.peek()), ScreenRectangle.empty());
		scissors.push(rect);
	}

	@Override
	public void disableScissor() {
		graphics.disableScissor();
		scissors.poll();
	}

	@Override
	public float poseX() {
		return graphics.pose().m20();
	}

	@Override
	public float poseY() {
		return graphics.pose().m21();
	}

	@Override
	public void quads(float[] xy, float[] uv, int[] colors, int vertices, Identifier texture, boolean smooth) {
		if (vertices < 4) return;
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (int i = 0; i < vertices; i++) {
			minX = Math.min(minX, xy[2 * i]);
			maxX = Math.max(maxX, xy[2 * i]);
			minY = Math.min(minY, xy[2 * i + 1]);
			maxY = Math.max(maxY, xy[2 * i + 1]);
		}
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		int left = (int) Math.floor(minX);
		int top = (int) Math.floor(minY);
		ScreenRectangle bounds = new ScreenRectangle(left, top, Math.max(1, (int) Math.ceil(maxX) - left),
				Math.max(1, (int) Math.ceil(maxY) - top)).transformMaxBounds(pose);
		ScreenRectangle scissor = scissors.peek();
		if (scissor != null) {
			bounds = bounds.intersection(scissor);
			if (bounds == null) return;
		}
		VertexArena.put(xy, texture == null ? null : uv, colors, vertices);
		TextureSetup setup;
		if (texture == null) {
			setup = TextureSetup.noTexture();
		} else {
			AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);
			setup = TextureSetup.singleTexture(loaded.getTextureView(),
					RenderSystem.getSamplerCache().getClampToEdge(smooth ? FilterMode.LINEAR : FilterMode.NEAREST));
		}
		graphics.guiRenderState.addGuiElement(new MeshRenderState(texture == null ? RenderPipelines.GUI : RenderPipelines.GUI_TEXTURED, setup, pose,
				VertexArena.block(), VertexArena.offset(), vertices, texture != null, scissor, bounds));
	}

	@Override
	public void mask(Identifier texture, int x, int y, int u, int v, int w, int h, int textureWidth, int textureHeight, int argb) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) u, (float) v, w, h, w, h, textureWidth, textureHeight, argb);
	}

	@Override
	public float poseScale() {
		return graphics.pose().m00();
	}

	@Override
	public void sprite(Identifier sprite, int x, int y, int w, int h) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
	}

	@Override
	public void texture(Identifier texture, int x, int y, int w, int h, int textureWidth, int textureHeight) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, textureWidth, textureHeight,
				textureWidth, textureHeight);
	}

	@Override
	public void face(PlayerSkin skin, int x, int y, int size) {
		Identifier texture = skin.body().texturePath();
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 8, 8, size, size, 8, 8, 64, 64);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 40, 8, size, size, 8, 8, 64, 64);
	}
}
