package dev.eventide.client.gui.menu;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.eventide.client.EventideClient;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.module.Modules;
import dev.eventide.client.render.WorldDraw;
import dev.eventide.client.ui.Ease;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Floating mode: the mod menu as a panel hanging in the air in front of you, like a holographic screen.
 * It's anchored in the world where you were looking when it opened (and moves with you, not with your
 * view), so you can turn your head by right-dragging and see it from the side. It opens with a line of
 * light sweeping down it and closes the same way up. The bar under it moves it around you, the button
 * beside the bar brings it back in front, and Floating distance and size in Theme set how far away and
 * how big it is.
 *
 * <p>It's drawn after the world (before the HUD and screens) by {@link WorldDraw}, with its own fixed field
 * of view so zoom and sprinting don't warp it; mouse positions are turned into points on the panel by
 * casting a ray through the same projection, so what you click is what you see.
 */
public final class FloatingMenu {
	/** The panel's width in blocks at 100% size. */
	private static final float WIDTH = 2.5f;
	private static final float FOV = 70;
	private static final float NEAR = 0.05f;
	private static final float FAR = 100;
	private static final long OPEN_MS = 340;
	private static final long CLOSE_MS = 220;
	/** The bar under the window, in the window's layout units. */
	private static final float BAR_W = 170;
	private static final float BAR_H = 12;
	private static final float BAR_GAP = 22;
	private static final float BUTTON = 34;

	private static final WorldDraw DRAW = new WorldDraw();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Quaternionf ROTATION = new Quaternionf();

	private static MenuView view;
	private static boolean open = false;
	private static long changedAt = 0;
	private static float yaw;
	private static float pitch;
	private static float mouseX = -9999;
	private static float mouseY = -9999;
	private static boolean overBar = false;
	private static boolean overRecenter = false;
	private static boolean failed = false;
	private static long drawnFrames = 0;

	private FloatingMenu() {
	}

	/** Shows {@code menu} on a panel in front of where the camera looks. */
	public static void show(MenuView menu) {
		boolean wasShown = view != null && open;
		view = menu;
		if (!wasShown) {
			recenter();
			changedAt = System.currentTimeMillis();
		}
		open = true;
	}

	/** Starts the closing sweep; the panel disappears once it's done. */
	public static void hide() {
		if (!open) return;
		open = false;
		changedAt = System.currentTimeMillis();
	}

	/** Puts the panel straight in front of where the camera looks now. */
	public static void recenter() {
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		yaw = camera.yRot();
		pitch = Math.max(-25, Math.min(25, camera.xRot() * 0.6f));
	}

	/** Whether the panel is up (or still sweeping away). */
	public static boolean visible() {
		return view != null && (open || System.currentTimeMillis() - changedAt < CLOSE_MS);
	}

	/** How many frames the panel has been drawn in the world, for the in-game test. */
	public static long drawnFrames() {
		return drawnFrames;
	}

	/** How far the panel's reveal has got (0 hidden, 1 fully shown). */
	private static float reveal() {
		long age = System.currentTimeMillis() - changedAt;
		if (open) return Ease.OUT.apply(age / (double) OPEN_MS);
		return 1 - Ease.OUT.apply(age / (double) CLOSE_MS);
	}

	private static float distance() {
		return (float) Modules.THEME_FLOAT_DISTANCE.get();
	}

	private static float worldWidth() {
		return WIDTH * (float) (Modules.THEME_FLOAT_SCALE.get() / 100.0);
	}

	/** World units per layout unit. */
	private static float unit() {
		return worldWidth() / MenuView.WIN_W;
	}

	private static Vector3f forward(float yawDeg, float pitchDeg) {
		double y = Math.toRadians(yawDeg);
		double p = Math.toRadians(pitchDeg);
		return new Vector3f((float) (-Math.sin(y) * Math.cos(p)), (float) -Math.sin(p), (float) (Math.cos(y) * Math.cos(p)));
	}

	/** The panel's frame, camera-relative: {window top left, right per layout unit, down per layout unit, facing}. */
	private static Vector3f[] frame() {
		Vector3f f = forward(yaw, pitch);
		Vector3f r = new Vector3f(f).cross(0, 1, 0).normalize();
		Vector3f up = new Vector3f(r).cross(f).normalize();
		float k = unit();
		float bob = (float) Math.sin(System.currentTimeMillis() / 1000.0 * 1.3) * 0.008f;
		Vector3f centre = new Vector3f(f).mul(distance()).add(new Vector3f(up).mul(bob));
		Vector3f corner = new Vector3f(centre).sub(new Vector3f(r).mul(MenuView.WIN_W * k / 2f)).add(new Vector3f(up).mul(MenuView.WIN_H * k / 2f));
		return new Vector3f[] {corner, new Vector3f(r).mul(k), new Vector3f(up).mul(-k), f};
	}

	private static void projection(Matrix4f out) {
		var window = Minecraft.getInstance().getWindow();
		float aspect = window.getWidth() / (float) Math.max(1, window.getHeight());
		out.setPerspective((float) Math.toRadians(FOV), aspect, NEAR, FAR);
	}

	/** Draws the panel; called once a frame after the world is drawn. */
	public static void render() {
		Minecraft mc = Minecraft.getInstance();
		if (failed || view == null || mc.level == null) return;
		if (!visible()) {
			if (!open) view = null;
			return;
		}
		float t = reveal();
		if (t <= 0.001f) return;
		Camera camera = mc.gameRenderer.getMainCamera();
		ROTATION.set(camera.rotation()).conjugate();
		VIEW.identity().rotation(ROTATION);
		projection(PROJECTION);
		Vector3f[] f = frame();
		float k = unit();
		var window = mc.getWindow();
		float pixels = window.getHeight() / (2f * (float) Math.tan(Math.toRadians(FOV / 2)) * distance()) * k;
		float scale = 0.94f + 0.06f * t;
		Vector3f centre = new Vector3f(f[0]).add(new Vector3f(f[1]).mul(MenuView.WIN_W / 2f)).add(new Vector3f(f[2]).mul(MenuView.WIN_H / 2f));
		Vector3f corner = new Vector3f(centre).sub(new Vector3f(f[1]).mul(MenuView.WIN_W / 2f * scale)).sub(new Vector3f(f[2]).mul(MenuView.WIN_H / 2f * scale));
		Vector3f across = new Vector3f(f[1]).mul(scale);
		Vector3f downward = new Vector3f(f[2]).mul(scale);
		RenderSystem.backupProjectionMatrix();
		try {
			RenderSystem.setProjectionMatrix(Compat.projectionBuffer(PROJECTION), ProjectionType.PERSPECTIVE);
			DRAW.begin(corner, across, downward, MenuView.WIN_W, MenuView.WIN_H + BAR_GAP + BUTTON + 10, pixels * scale);
			float old = Paint.opacity(Math.min(1, t * 1.6f));
			drawAura(t);
			float sweep = MenuView.WIN_H * t;
			DRAW.enableScissor(-200, -200, Math.round(MenuView.WIN_W + 200), Math.round(sweep));
			view.drawPanel(DRAW, mouseX, mouseY);
			DRAW.disableScissor();
			drawSweep(sweep, t);
			drawBar(t);
			Paint.opacity(old);
			DRAW.end(VIEW, 1);
			drawnFrames++;
		} catch (RuntimeException e) {
			failed = true;
			EventideClient.LOG.error("Couldn't draw the floating menu; it'll open on screen instead", e);
		} finally {
			RenderSystem.restoreProjectionMatrix();
		}
	}

	/** Whether drawing the panel failed, so the menu should open on screen instead. */
	public static boolean failed() {
		return failed;
	}

	/** A soft violet light behind the panel and thin glowing brackets at its corners. */
	private static void drawAura(float t) {
		int accent = Accent.get();
		float w = MenuView.WIN_W;
		float h = MenuView.WIN_H;
		Paint.shadow(DRAW, 0, 0, w, h, 22, 0, 0, 120, 10, Paint.fade(accent, 0.22f * t));
		float len = 46;
		float gap = 12;
		float thick = 2;
		int edge = Paint.fade(Accent.lighten(accent, 0.3f), 0.85f * t);
		int glow = Paint.fade(accent, 0.6f * t);
		float[][] corners = {{-gap, -gap, 1, 1}, {w + gap, -gap, -1, 1}, {-gap, h + gap, 1, -1}, {w + gap, h + gap, -1, -1}};
		for (float[] c : corners) {
			float x = c[0];
			float y = c[1];
			float sx = c[2];
			float sy = c[3];
			float hx1 = Math.min(x, x + sx * len);
			float hx2 = Math.max(x, x + sx * len);
			float vy1 = Math.min(y, y + sy * len);
			float vy2 = Math.max(y, y + sy * len);
			Paint.shadow(DRAW, hx1, y - thick / 2f, hx2, y + thick / 2f, 1, 0, 0, 10, 0, glow);
			Paint.shadow(DRAW, x - thick / 2f, vy1, x + thick / 2f, vy2, 1, 0, 0, 10, 0, glow);
			Paint.rect(DRAW, hx1, y - thick / 2f, hx2, y + thick / 2f, 1, edge);
			Paint.rect(DRAW, x - thick / 2f, vy1, x + thick / 2f, vy2, 1, edge);
		}
	}

	/** The line of light at the edge of the reveal while it opens or closes. */
	private static void drawSweep(float y, float t) {
		if (t >= 0.999f) return;
		float strength = (float) Math.sin(Math.PI * t);
		int accent = Accent.get();
		Paint.shadow(DRAW, 0, y - 1, MenuView.WIN_W, y + 1, 1, 0, 0, 30, 0, Paint.fade(accent, 0.9f * strength));
		Paint.rect(DRAW, 20, y - 1, MenuView.WIN_W - 20, y + 1, 1, Paint.fade(Tokens.ICE, strength));
	}

	/** The bar under the panel that moves it, and the button beside it that brings it back in front. */
	private static void drawBar(float t) {
		float cx = MenuView.WIN_W / 2f;
		float y = MenuView.WIN_H + BAR_GAP;
		float bx1 = cx - BAR_W / 2f;
		float bx2 = cx + BAR_W / 2f;
		float a = Math.max(0, (t - 0.6f) / 0.4f);
		float old = Paint.opacity(Paint.opacity() * a);
		Paint.shadow(DRAW, bx1, y, bx2, y + BAR_H, BAR_H / 2f, 0, 0, 16, 0, Paint.fade(Accent.get(), overBar ? 0.55f : 0.25f));
		Paint.rect(DRAW, bx1, y, bx2, y + BAR_H, BAR_H / 2f, overBar ? 0xE6FFFFFF : 0x99FFFFFF);
		float rx = bx2 + 16;
		float ry = y + BAR_H / 2f - BUTTON / 2f;
		Paint.circle(DRAW, rx + BUTTON / 2f, ry + BUTTON / 2f, BUTTON / 2f, overRecenter ? 0xF2221A42 : 0xD9110C22);
		Paint.ring(DRAW, rx + BUTTON / 2f, ry + BUTTON / 2f, BUTTON / 2f, 1, overRecenter ? Paint.fade(Accent.get(), 0.9f) : 0x33FFFFFF);
		Paint.icon(DRAW, "locate", rx + BUTTON / 2f - 8, ry + BUTTON / 2f - 8, 16, overRecenter ? 0xFFFFFFFF : Tokens.TEXT_2);
		String hint = "Drag to move · Right-drag to look around";
		float cap = 9;
		Type.center(DRAW, hint, cx, y + BAR_H + 12, cap, Weight.SEMIBOLD, Paint.fade(Tokens.TEXT_2, 0.85f));
		Paint.opacity(old);
	}

	/**
	 * Turns a mouse position (GUI units) into a point on the window, in its layout units, by casting a
	 * ray from the camera through the panel's projection; null if the ray misses the panel's plane.
	 */
	public static float[] pick(double guiX, double guiY) {
		Minecraft mc = Minecraft.getInstance();
		var window = mc.getWindow();
		double scale = window.getGuiScale();
		float nx = (float) (guiX * scale / window.getWidth() * 2 - 1);
		float ny = (float) (1 - guiY * scale / window.getHeight() * 2);
		float tan = (float) Math.tan(Math.toRadians(FOV / 2));
		float aspect = window.getWidth() / (float) Math.max(1, window.getHeight());
		Vector3f ray = new Vector3f(nx * tan * aspect, ny * tan, -1);
		mc.gameRenderer.getMainCamera().rotation().transform(ray);
		Vector3f[] f = frame();
		Vector3f normal = new Vector3f(f[3]).negate();
		float denom = ray.dot(normal);
		if (Math.abs(denom) < 1e-5f) return null;
		float hit = new Vector3f(f[0]).dot(normal) / denom;
		if (hit <= 0) return null;
		Vector3f point = new Vector3f(ray).mul(hit).sub(f[0]);
		float k = unit();
		float lx = point.dot(f[1]) / (k * k);
		float ly = point.dot(f[2]) / (k * k);
		return new float[] {lx, ly};
	}

	/** Where a point on the window (layout units) shows on screen, in GUI units, or null if it's behind the camera; for the in-game test. */
	public static float[] project(float lx, float ly) {
		Minecraft mc = Minecraft.getInstance();
		Vector3f[] f = frame();
		Vector3f world = new Vector3f(f[0]).add(new Vector3f(f[1]).mul(lx)).add(new Vector3f(f[2]).mul(ly));
		Quaternionf inverse = new Quaternionf(mc.gameRenderer.getMainCamera().rotation()).conjugate();
		inverse.transform(world);
		if (world.z >= -1e-4f) return null;
		var window = mc.getWindow();
		float tan = (float) Math.tan(Math.toRadians(FOV / 2));
		float aspect = window.getWidth() / (float) Math.max(1, window.getHeight());
		float nx = world.x / (-world.z * tan * aspect);
		float ny = world.y / (-world.z * tan);
		double scale = window.getGuiScale();
		return new float[] {(float) ((nx + 1) / 2 * window.getWidth() / scale), (float) ((1 - ny) / 2 * window.getHeight() / scale)};
	}

	/** Tells the panel where the mouse is; returns the point on the window (layout units). */
	public static float[] hover(double guiX, double guiY) {
		float[] p = pick(guiX, guiY);
		if (p == null) {
			mouseX = -9999;
			mouseY = -9999;
			overBar = false;
			overRecenter = false;
			return null;
		}
		mouseX = p[0];
		mouseY = p[1];
		overBar = onBar(p[0], p[1]);
		overRecenter = onRecenter(p[0], p[1]);
		return p;
	}

	static boolean onBar(float x, float y) {
		float cx = MenuView.WIN_W / 2f;
		float top = MenuView.WIN_H + BAR_GAP - 8;
		return x >= cx - BAR_W / 2f - 10 && x < cx + BAR_W / 2f + 10 && y >= top && y < top + BAR_H + 16;
	}

	static boolean onRecenter(float x, float y) {
		float rx = MenuView.WIN_W / 2f + BAR_W / 2f + 16;
		float ry = MenuView.WIN_H + BAR_GAP + BAR_H / 2f - BUTTON / 2f;
		return x >= rx && x < rx + BUTTON && y >= ry && y < ry + BUTTON;
	}

	/** Moves the panel by a mouse drag of (dx, dy) GUI units, around you on a sphere. */
	static void move(double dx, double dy) {
		var window = Minecraft.getInstance().getWindow();
		double scale = window.getGuiScale();
		float degreesPerPixel = FOV / window.getHeight();
		yaw += (float) (dx * scale * degreesPerPixel);
		pitch = Math.max(-60, Math.min(60, pitch + (float) (dy * scale * degreesPerPixel)));
	}
}
