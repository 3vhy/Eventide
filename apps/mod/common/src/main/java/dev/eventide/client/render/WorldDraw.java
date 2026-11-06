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
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.eventide.client.Draw;
import dev.eventide.client.EventideClient;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.gui.Smooth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Draws Eventide's 2D menus onto a flat panel in the world, for Floating mode. It implements {@link Draw},
 * so the menu draws itself exactly as it does on screen; each shape, picture and letter is placed on the
 * panel (a corner and two edge directions in camera-relative world space) instead of the screen. Clipping
 * (scrolling lists) is done on the CPU, cutting shapes to the clip box, since a scissor can't follow a
 * panel seen at an angle. Everything is gathered into one buffer and drawn in one pass, in order, switching
 * texture only where the menu does; with no depth test, the panel shows over the world like a hologram.
 *
 * <p>Items and GUI sprites aren't drawn on the panel (Minecraft draws those only on screen). Render thread only.
 */
public final class WorldDraw implements Draw {
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "world_draw/white");
	private static final int[] WHITE_PIXEL = {0xFFFFFFFF};

	/** A run of quads drawn with one texture. */
	private record Segment(GpuTextureView texture, boolean intensity, boolean smooth, int first, int count) {
	}

	private final ByteBufferBuilder memory = new ByteBufferBuilder(1 << 20);
	private final List<Segment> segments = new ArrayList<>();
	private final float[] stack = new float[3 * 64];
	private final float[] clips = new float[4 * 32];
	private final Glyphs glyphs = new Glyphs();
	private final Vector3f origin = new Vector3f();
	private final Vector3f right = new Vector3f();
	private final Vector3f down = new Vector3f();
	private final float[] px = new float[16];
	private final float[] py = new float[16];
	private final float[] pu = new float[16];
	private final float[] pv = new float[16];
	private final int[] pc = new int[16];
	private final float[] qx = new float[16];
	private final float[] qy = new float[16];
	private final float[] qu = new float[16];
	private final float[] qv = new float[16];
	private final int[] qc = new int[16];
	private BufferBuilder builder;
	private boolean whiteMade = false;
	private int depth = 0;
	private int clipDepth = 0;
	private float tx;
	private float ty;
	private float s;
	private float canvasW;
	private float canvasH;
	private float density;
	private int quads;
	private GpuTextureView segTexture;
	private boolean segIntensity;
	private boolean segSmooth;
	private int segFirst;

	/**
	 * Starts a frame on a panel: {@code corner} is where the canvas's top left lands, {@code across} and
	 * {@code downward} the world distance one canvas unit covers going right and down, {@code w} x {@code h}
	 * the canvas size, and {@code pixels} how many screen pixels one canvas unit covers (for sharp edges).
	 */
	public void begin(Vector3f corner, Vector3f across, Vector3f downward, float w, float h, float pixels) {
		origin.set(corner);
		right.set(across);
		down.set(downward);
		canvasW = w;
		canvasH = h;
		density = Math.max(0.05f, pixels);
		tx = 0;
		ty = 0;
		s = 1;
		depth = 0;
		clipDepth = 0;
		quads = 0;
		segments.clear();
		segTexture = null;
		builder = Compat.quads(memory, DefaultVertexFormat.POSITION_TEX_COLOR);
	}

	/** Draws what was gathered with {@code view} (the camera's rotation), faded by {@code alpha}. */
	public void end(Matrix4f view, float alpha) {
		closeSegment();
		BufferBuilder done = builder;
		builder = null;
		if (done == null) return;
		MeshData mesh = done.build();
		if (mesh == null) return;
		try {
			GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(view, new Vector4f(1, 1, 1, alpha), new Vector3f(), new Matrix4f());
			RenderPipeline plain = SkyMesh.text();
			RenderPipeline intensity = SkyMesh.textIntensity();
			RenderTarget target = Compat.mainTarget();
			try (Compat.QuadPass pass = new Compat.QuadPass("Eventide floating menu", mesh, target.getColorTextureView(),
					target.getDepthTextureView())) {
				RenderPipeline current = null;
				for (Segment segment : segments) {
					RenderPipeline pipeline = segment.intensity() ? intensity : plain;
					if (pipeline != current) {
						pass.pipeline(pipeline, transforms);
						current = pipeline;
					}
					pass.texture("Sampler0", segment.texture(),
							RenderSystem.getSamplerCache().getClampToEdge(segment.smooth() ? FilterMode.LINEAR : FilterMode.NEAREST));
					pass.draw(segment.first(), segment.count());
				}
			}
		} finally {
			mesh.close();
		}
	}

	private GpuTextureView white() {
		if (!whiteMade) {
			whiteMade = true;
			Compat.uploadTexture(WHITE, Smooth.png(1, 1, WHITE_PIXEL));
		}
		return view(WHITE);
	}

	private static GpuTextureView view(Identifier id) {
		try {
			return Minecraft.getInstance().getTextureManager().getTexture(id).getTextureView();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private void use(GpuTextureView texture, boolean intensity, boolean smooth) {
		if (texture == segTexture && intensity == segIntensity && smooth == segSmooth) return;
		closeSegment();
		segTexture = texture;
		segIntensity = intensity;
		segSmooth = smooth;
		segFirst = quads;
	}

	private void closeSegment() {
		if (segTexture != null && quads > segFirst) segments.add(new Segment(segTexture, segIntensity, segSmooth, segFirst, quads - segFirst));
		segFirst = quads;
	}

	@Override
	public int width() {
		return Math.round(canvasW);
	}

	@Override
	public int height() {
		return Math.round(canvasH);
	}

	@Override
	public void fill(int x1, int y1, int x2, int y2, int argb) {
		GpuTextureView w = white();
		if (w == null) return;
		use(w, false, true);
		rect(x1, y1, x2, y2, 0, 0, 1, 1, argb);
	}

	private void rect(float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int argb) {
		px[0] = x1;
		py[0] = y1;
		pu[0] = u1;
		pv[0] = v1;
		px[1] = x1;
		py[1] = y2;
		pu[1] = u1;
		pv[1] = v2;
		px[2] = x2;
		py[2] = y2;
		pu[2] = u2;
		pv[2] = v2;
		px[3] = x2;
		py[3] = y1;
		pu[3] = u2;
		pv[3] = v1;
		pc[0] = argb;
		pc[1] = argb;
		pc[2] = argb;
		pc[3] = argb;
		polygon(4, true);
	}

	@Override
	public void text(String text, int x, int y, int argb, boolean shadow) {
		text(Component.literal(text), x, y, argb, shadow);
	}

	@Override
	public void text(Component text, int x, int y, int argb, boolean shadow) {
		if (((argb >>> 24) & 255) < 4) return;
		Font font = Minecraft.getInstance().font;
		Font.PreparedText prepared = font.prepareText(text.getVisualOrderText(), x, y, argb, shadow, false, 0);
		prepared.visit(glyphs);
	}

	@Override
	public void item(ItemStack stack, int x, int y) {
	}

	@Override
	public void entity(net.minecraft.world.entity.LivingEntity entity, int x0, int y0, int x1, int y1, int scale, float lookX, float lookY) {
	}

	@Override
	public void itemOverlay(ItemStack stack, int x, int y, String countLabel) {
	}

	@Override
	public void sprite(Identifier sprite, int x, int y, int w, int h) {
	}

	@Override
	public void texture(Identifier texture, int x, int y, int w, int h, int textureWidth, int textureHeight) {
		GpuTextureView view = view(texture);
		if (view == null) return;
		use(view, false, true);
		rect(x, y, x + w, y + h, 0, 0, 1, 1, 0xFFFFFFFF);
	}

	@Override
	public void mask(Identifier texture, int x, int y, int u, int v, int w, int h, int textureWidth, int textureHeight, int argb) {
		GpuTextureView view = view(texture);
		if (view == null) return;
		use(view, false, false);
		rect(x, y, x + w, y + h, u / (float) textureWidth, v / (float) textureHeight, (u + w) / (float) textureWidth, (v + h) / (float) textureHeight, argb);
	}

	@Override
	public float poseScale() {
		return s * density / guiScale();
	}

	@Override
	public float poseX() {
		return tx * density / guiScale();
	}

	@Override
	public float poseY() {
		return ty * density / guiScale();
	}

	private static float guiScale() {
		return (float) Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}

	@Override
	public void quads(float[] xy, float[] uv, int[] colors, int vertices, Identifier texture, boolean smooth) {
		GpuTextureView view = texture == null ? white() : view(texture);
		if (view == null) return;
		use(view, false, smooth || texture == null);
		for (int q = 0; q + 3 < vertices; q += 4) {
			for (int k = 0; k < 4; k++) {
				int i = q + k;
				px[k] = xy[2 * i];
				py[k] = xy[2 * i + 1];
				pu[k] = texture == null ? 0.5f : uv[2 * i];
				pv[k] = texture == null ? 0.5f : uv[2 * i + 1];
				pc[k] = colors[i];
			}
			polygon(4, true);
		}
	}

	@Override
	public void face(PlayerSkin skin, int x, int y, int size) {
		Identifier texture = skin.body().texturePath();
		mask(texture, x, y, 8, 8, size, size, 64, 64, 0xFFFFFFFF);
		mask(texture, x, y, 40, 8, size, size, 64, 64, 0xFFFFFFFF);
	}

	@Override
	public void push() {
		if (depth * 3 + 3 > stack.length) return;
		stack[depth * 3] = tx;
		stack[depth * 3 + 1] = ty;
		stack[depth * 3 + 2] = s;
		depth++;
	}

	@Override
	public void pop() {
		if (depth == 0) return;
		depth--;
		tx = stack[depth * 3];
		ty = stack[depth * 3 + 1];
		s = stack[depth * 3 + 2];
	}

	@Override
	public void translate(float x, float y) {
		tx += x * s;
		ty += y * s;
	}

	@Override
	public void scale(float scale) {
		s *= scale;
	}

	@Override
	public void enableScissor(int x1, int y1, int x2, int y2) {
		float ax = tx + x1 * s;
		float ay = ty + y1 * s;
		float bx = tx + x2 * s;
		float by = ty + y2 * s;
		if (clipDepth > 0) {
			int at = (clipDepth - 1) * 4;
			ax = Math.max(ax, clips[at]);
			ay = Math.max(ay, clips[at + 1]);
			bx = Math.min(bx, clips[at + 2]);
			by = Math.min(by, clips[at + 3]);
		}
		if (clipDepth * 4 + 4 > clips.length) return;
		int at = clipDepth * 4;
		clips[at] = ax;
		clips[at + 1] = ay;
		clips[at + 2] = bx;
		clips[at + 3] = by;
		clipDepth++;
	}

	@Override
	public void disableScissor() {
		if (clipDepth > 0) clipDepth--;
	}

	/**
	 * Places the first {@code n} points in {@link #px} (canvas units, before the transform when
	 * {@code transform}) on the panel, cut to the clip box, as quads.
	 */
	private void polygon(int n, boolean transform) {
		if (builder == null) return;
		if (transform) {
			for (int i = 0; i < n; i++) {
				px[i] = tx + px[i] * s;
				py[i] = ty + py[i] * s;
			}
		}
		int count = n;
		if (clipDepth > 0) {
			int at = (clipDepth - 1) * 4;
			float cx1 = clips[at];
			float cy1 = clips[at + 1];
			float cx2 = clips[at + 2];
			float cy2 = clips[at + 3];
			float minX = Float.MAX_VALUE;
			float minY = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE;
			float maxY = -Float.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				minX = Math.min(minX, px[i]);
				maxX = Math.max(maxX, px[i]);
				minY = Math.min(minY, py[i]);
				maxY = Math.max(maxY, py[i]);
			}
			if (maxX <= cx1 || minX >= cx2 || maxY <= cy1 || minY >= cy2 || cx2 <= cx1 || cy2 <= cy1) return;
			if (minX < cx1 || maxX > cx2 || minY < cy1 || maxY > cy2) {
				count = clipEdge(count, 0, cx1, false);
				count = clipEdge(count, 0, cx2, true);
				count = clipEdge(count, 1, cy1, false);
				count = clipEdge(count, 1, cy2, true);
				if (count < 3) return;
			}
		}
		for (int i = 1; i + 1 < count; i += 2) {
			int c = Math.min(i + 2, count - 1);
			vertex(0);
			vertex(i);
			vertex(i + 1);
			vertex(c);
			quads++;
		}
	}

	/** Cuts the polygon in {@link #px} against one side of the clip box (Sutherland-Hodgman); returns the new point count. */
	private int clipEdge(int n, int axis, float limit, boolean max) {
		if (n < 3) return n;
		int m = 0;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float a = axis == 0 ? px[i] : py[i];
			float b = axis == 0 ? px[j] : py[j];
			boolean inA = max ? a <= limit : a >= limit;
			boolean inB = max ? b <= limit : b >= limit;
			if (inA) copy(i, m++);
			if (inA != inB && m < qx.length) {
				float t = (limit - a) / (b - a);
				qx[m] = px[i] + (px[j] - px[i]) * t;
				qy[m] = py[i] + (py[j] - py[i]) * t;
				qu[m] = pu[i] + (pu[j] - pu[i]) * t;
				qv[m] = pv[i] + (pv[j] - pv[i]) * t;
				qc[m] = Draw.mix(pc[i], pc[j], t);
				m++;
			}
			if (m >= qx.length - 1) break;
		}
		for (int i = 0; i < m; i++) {
			px[i] = qx[i];
			py[i] = qy[i];
			pu[i] = qu[i];
			pv[i] = qv[i];
			pc[i] = qc[i];
		}
		return m;
	}

	private void copy(int from, int to) {
		qx[to] = px[from];
		qy[to] = py[from];
		qu[to] = pu[from];
		qv[to] = pv[from];
		qc[to] = pc[from];
	}

	private void vertex(int i) {
		float x = px[i];
		float y = py[i];
		builder.addVertex(origin.x + right.x * x + down.x * y, origin.y + right.y * x + down.y * y, origin.z + right.z * x + down.z * y)
				.setUv(pu[i], pv[i]).setColor(pc[i]);
	}

	/** Takes Minecraft's glyphs one at a time and places their quads on the panel. */
	private final class Glyphs implements Font.GlyphVisitor, VertexConsumer {
		private final Matrix4f identity = new Matrix4f();
		private int pending = 0;

		@Override
		public void acceptGlyph(TextRenderable.Styled glyph) {
			GpuTextureView texture = glyph.textureView();
			if (texture == null || builder == null) return;
			use(texture, Compat.grayscaleGlyph(glyph.guiPipeline()), true);
			pending = 0;
			glyph.render(identity, this, SkyMesh.FULL_BRIGHT, false);
			if (pending == 4) polygon(4, true);
			pending = 0;
		}

		@Override
		public void acceptEffect(TextRenderable effect) {
			GpuTextureView texture = effect.textureView();
			if (texture == null || builder == null) return;
			use(texture, Compat.grayscaleGlyph(effect.guiPipeline()), true);
			pending = 0;
			effect.render(identity, this, SkyMesh.FULL_BRIGHT, false);
			if (pending == 4) polygon(4, true);
			pending = 0;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			if (pending == 4) {
				polygon(4, true);
				pending = 0;
			}
			px[pending] = x;
			py[pending] = y;
			pu[pending] = 0;
			pv[pending] = 0;
			pc[pending] = 0xFFFFFFFF;
			pending++;
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			return setColor(a << 24 | r << 16 | g << 8 | b);
		}

		@Override
		public VertexConsumer setColor(int argb) {
			if (pending > 0) pc[pending - 1] = argb;
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			if (pending > 0) {
				pu[pending - 1] = u;
				pv[pending - 1] = v;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}
	}
}
