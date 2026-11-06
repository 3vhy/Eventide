package dev.eventide.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import dev.eventide.client.compat.Compat;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * A texture a sky is drawn into: the world's sky at reduced resolution (then stretched over the screen) or
 * a thumbnail for the settings. Registered like any texture, so the menu draws it like a picture.
 */
final class SkyCanvas extends AbstractTexture {
	private final String label;
	private int width;
	private int height;

	SkyCanvas(String label) {
		this.label = label;
		this.sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR,
				FilterMode.LINEAR, false);
	}

	/** Makes sure the texture is {@code width} x {@code height}, remaking it if not. */
	void size(int width, int height) {
		width = Math.max(1, width);
		height = Math.max(1, height);
		if (texture != null && this.width == width && this.height == height) return;
		close();
		this.width = width;
		this.height = height;
		texture = Compat.colorTarget(label, width, height);
		textureView = RenderSystem.getDevice().createTextureView(texture);
	}

	boolean ready() {
		return textureView != null;
	}

	int width() {
		return width;
	}

	int height() {
		return height;
	}
}
