package dev.eventide.client.music;

import com.mojang.blaze3d.platform.NativeImage;
import dev.eventide.client.Draw;
import dev.eventide.client.Hooks;
import dev.eventide.client.EventideClient;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.gui.Smooth;
import dev.eventide.client.ui.Paint;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * A picture from the music helper (the song's cover or the app's icon), drawn in the screen's real
 * pixels: it's resampled smoothly to exactly the size it's shown at, with its round corners worked
 * into its edges, so it never shows blocky pixels or stray lines along its edges at any GUI scale.
 */
public final class MusicArt {
	public static final MusicArt COVER = new MusicArt("music_art");
	public static final MusicArt ICON = new MusicArt("music_icon");
	/** The main menu's Continue card: the icon of the world or server you last played on. */
	public static final MusicArt PLACE = new MusicArt("last_played");
	private static final int SAMPLES = 4;

	private final String path;
	private long decodedVersion = -1;
	private int[] source = null;
	private int sourceSize = 0;
	/** This picture's textures by pixel size and rounding (null where one couldn't be made). */
	private final Map<String, Identifier> uploaded = new HashMap<>();

	private MusicArt(String path) {
		this.path = path;
	}

	/**
	 * Draws the picture {@code size} units square at (x, y) with corners rounded by {@code radius}
	 * (half of {@code size} makes a circle), faded with {@link Paint#opacity()}. Returns false when there's
	 * no picture (yet). Render thread only.
	 */
	public boolean draw(Draw d, byte[] png, long version, float x, float y, float size, float radius) {
		if (png == null) return false;
		if (version != decodedVersion) {
			decodedVersion = version;
			uploaded.clear();
			decode(png);
		}
		if (source == null) return false;
		float k = Smooth.density(d);
		int s = Math.max(1, Math.round(size * k));
		int r = Math.max(0, Math.min(s / 2, Math.round(radius * k)));
		String key = s + "_" + r;
		if (!uploaded.containsKey(key)) uploaded.put(key, upload(key, s, r));
		Identifier id = uploaded.get(key);
		if (id == null) return false;
		d.push();
		d.scale(1f / k);
		d.mask(id, Math.round(x * k), Math.round(y * k), 0, 0, s, s, s, s, Paint.fade(0xFFFFFFFF, Paint.opacity()));
		d.pop();
		return true;
	}

	private Identifier upload(String key, int s, int r) {
		Identifier id = Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, path + "_" + key);
		if (Compat.uploadTexture(id, Smooth.png(s, s, resample(s, r))) == null) return null;
		if (this == COVER) Hooks.musicArtUploaded = true;
		else Hooks.musicIconUploaded = true;
		return id;
	}

	/** Reads the PNG into {@link #source}, cropped to a square from the middle. */
	private void decode(byte[] png) {
		source = null;
		try (NativeImage image = NativeImage.read(png)) {
			int side = Math.min(image.getWidth(), image.getHeight());
			int left = (image.getWidth() - side) / 2;
			int top = (image.getHeight() - side) / 2;
			int[] pixels = new int[side * side];
			for (int py = 0; py < side; py++) {
				for (int px = 0; px < side; px++) pixels[py * side + px] = image.getPixel(left + px, top + py);
			}
			source = pixels;
			sourceSize = side;
		} catch (Exception e) {
			EventideClient.LOG.warn("Couldn't read the music HUD's {}", path, e);
		}
	}

	/**
	 * The picture at {@code s} x {@code s}: each pixel averages a grid of smooth (bilinear) samples
	 * across the part of the picture it covers, so shrinking it keeps detail without shimmer; the
	 * edges are clamped, never wrapped. The corners outside the {@code r} rounding fade out.
	 */
	private int[] resample(int s, int r) {
		int n = Math.max(1, (int) Math.ceil(sourceSize / (double) s));
		double step = sourceSize / (double) s;
		int[] out = new int[s * s];
		for (int y = 0; y < s; y++) {
			for (int x = 0; x < s; x++) {
				double a = 0;
				double red = 0;
				double green = 0;
				double blue = 0;
				for (int sy = 0; sy < n; sy++) {
					for (int sx = 0; sx < n; sx++) {
						int pixel = bilinear((x + (sx + 0.5) / n) * step - 0.5, (y + (sy + 0.5) / n) * step - 0.5);
						double alpha = (pixel >>> 24) / 255.0;
						a += alpha;
						red += ((pixel >> 16) & 255) * alpha;
						green += ((pixel >> 8) & 255) * alpha;
						blue += (pixel & 255) * alpha;
					}
				}
				double coverage = coverage(x, y, s, r);
				int alpha = (int) Math.round(a / (n * n) * coverage * 255);
				if (alpha <= 0 || a <= 0) continue;
				out[y * s + x] = alpha << 24 | channel(red / a) << 16 | channel(green / a) << 8 | channel(blue / a);
			}
		}
		return out;
	}

	private int bilinear(double u, double v) {
		int last = sourceSize - 1;
		double cu = Math.max(0, Math.min(last, u));
		double cv = Math.max(0, Math.min(last, v));
		int x0 = (int) Math.floor(cu);
		int y0 = (int) Math.floor(cv);
		int x1 = Math.min(last, x0 + 1);
		int y1 = Math.min(last, y0 + 1);
		double fx = cu - x0;
		double fy = cv - y0;
		int p00 = source[y0 * sourceSize + x0];
		int p10 = source[y0 * sourceSize + x1];
		int p01 = source[y1 * sourceSize + x0];
		int p11 = source[y1 * sourceSize + x1];
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			double top = ((p00 >>> shift) & 255) * (1 - fx) + ((p10 >>> shift) & 255) * fx;
			double bottom = ((p01 >>> shift) & 255) * (1 - fx) + ((p11 >>> shift) & 255) * fx;
			out |= channel(top * (1 - fy) + bottom * fy) << shift;
		}
		return out;
	}

	/** How much of pixel (x, y) is inside an {@code s} square with corners rounded by {@code r}. */
	private static double coverage(int x, int y, int s, int r) {
		if (r <= 0) return 1;
		boolean nearX = x < r || x >= s - r;
		boolean nearY = y < r || y >= s - r;
		if (!nearX || !nearY) return 1;
		double cx = x < r ? r : s - r;
		double cy = y < r ? r : s - r;
		int hits = 0;
		for (int sy = 0; sy < SAMPLES; sy++) {
			for (int sx = 0; sx < SAMPLES; sx++) {
				double dx = x + (sx + 0.5) / SAMPLES - cx;
				double dy = y + (sy + 0.5) / SAMPLES - cy;
				if (dx * dx + dy * dy <= (double) r * r) hits++;
			}
		}
		return hits / (double) (SAMPLES * SAMPLES);
	}

	private static int channel(double value) {
		return Math.max(0, Math.min(255, (int) Math.round(value)));
	}
}
