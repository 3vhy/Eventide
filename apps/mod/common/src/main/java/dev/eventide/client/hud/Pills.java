package dev.eventide.client.hud;

import dev.eventide.client.Draw;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.ui.Paint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * The HUD's glass pills (your name, FPS, ping, the server, the clock, coordinates and the rest) and how they stick together. A pill
 * stands alone, or, dropped against another in Edit HUD, snaps flush to it; pills that touch side by side (same
 * height, same size) are drawn as one long pill with a thin divider between each, and pulled apart they stand
 * alone again.
 */
public final class Pills {
	/** How near (GUI pixels) a dragged pill has to come to another's side to stick to it. */
	private static final int STICK = 10;

	/** A widget where it's drawn this frame, in its own scaled units. */
	public record Placed(HudWidget widget, int x, int y, int w, int h, float scale) {
		int right() {
			return x + w;
		}
	}

	private Pills() {
	}

	/** A pill's glass: see-through indigo washed with the theme colour, rounded corners and a light lavender edge. */
	public static void glass(Draw d, float x1, float y1, float x2, float y2, float strength) {
		if (strength <= 0.01f) return;
		float r = (y2 - y1) * 0.32f;
		int accent = Accent.get();
		float old = Paint.opacity(Paint.opacity() * Math.min(1, strength * 1.15f));
		float hair = Hud.hairline(d);
		Paint.shadow(d, x1, y1, x2, y2, r, 0, 1, 6, 0, 0x30000000);
		Paint.rect(d, x1, y1, x2, y2, r, 0x7A16123C);
		Paint.rect(d, x1, y1, x2, y2, r, Paint.fade(accent, 0.16f));
		Paint.stroke(d, x1, y1, x2, y2, r, hair * 1.25f, Paint.fade(Accent.lighten(accent, 0.55f), 0.6f));
		Paint.opacity(old);
	}

	/** The lavender of a pill's icons and axis letters: the theme colour, lightened. */
	public static int lavender() {
		return Accent.lighten(Accent.get(), 0.48f);
	}

	/** The thin line between two pills stuck together. */
	public static void divider(Draw d, float x, float y1, float y2) {
		float hair = Hud.hairline(d);
		float inset = (y2 - y1) * 0.27f;
		Paint.rect(d, x - hair / 2f, y1 + inset, x + hair / 2f, y2 - inset, 0, Paint.fade(lavender(), 0.5f));
	}

	/** Whether two placed pills touch side by side ({@code a} on the left), so they're drawn as one. */
	static boolean touching(Placed a, Placed b) {
		return a.widget.pill() && b.widget.pill() && Math.abs(a.scale - b.scale) < 0.001f && Math.abs(a.y - b.y) <= 1 && a.h == b.h
				&& Math.abs(a.right() - b.x) <= 1;
	}

	/** The pills among {@code placed} grouped into runs that touch, each run left to right; lone pills are runs of one. */
	static List<List<Placed>> runs(List<Placed> placed) {
		List<Placed> pills = new ArrayList<>();
		for (Placed p : placed) if (p.widget.pill()) pills.add(p);
		pills.sort(Comparator.comparingInt(Placed::x));
		List<List<Placed>> runs = new ArrayList<>();
		boolean[] used = new boolean[pills.size()];
		for (int i = 0; i < pills.size(); i++) {
			if (used[i]) continue;
			List<Placed> run = new ArrayList<>();
			run.add(pills.get(i));
			used[i] = true;
			boolean grew = true;
			while (grew) {
				grew = false;
				Placed last = run.get(run.size() - 1);
				for (int j = 0; j < pills.size(); j++) {
					if (!used[j] && touching(last, pills.get(j))) {
						run.add(pills.get(j));
						used[j] = true;
						grew = true;
						break;
					}
				}
			}
			runs.add(run);
		}
		return runs;
	}

	/** Draws {@code placed}: other widgets as they are, and the pills alone or stuck together into bars. */
	public static void draw(Draw d, Minecraft mc, List<Placed> placed, boolean preview) {
		for (Placed p : placed) {
			if (p.widget.pill()) continue;
			d.push();
			d.scale(p.scale);
			p.widget.draw(d, p.x, p.y, mc, preview);
			d.pop();
		}
		for (List<Placed> run : runs(placed)) {
			Placed first = run.get(0);
			d.push();
			d.scale(first.scale);
			if (run.size() == 1) {
				first.widget.draw(d, first.x, first.y, mc, preview);
			} else {
				Placed last = run.get(run.size() - 1);
				float strength = 0;
				for (Placed p : run) strength = Math.max(strength, p.widget.glassStrength());
				glass(d, first.x, first.y, last.right(), first.y + first.h, strength);
				for (int i = 0; i < run.size(); i++) {
					Placed p = run.get(i);
					p.widget.drawContent(d, p.x, p.y, mc, preview);
					if (i > 0) divider(d, p.x, p.y, p.y + p.h);
				}
				dev.eventide.client.Hooks.pillsStuck = true;
			}
			d.pop();
		}
	}

	/**
	 * Where a dragged pill sticks: flush against the side of another pill it's dropped near (and level with it),
	 * as {left, top} in the dragged widget's units, or null when it's near none.
	 */
	public static int[] stick(HudWidget dragging, double left, double top, int w, int h, float scale, List<Placed> others) {
		if (!dragging.pill()) return null;
		for (Placed o : others) {
			if (o.widget == dragging || !o.widget.pill() || Math.abs(o.scale - scale) > 0.001f || o.h != h) continue;
			if (Math.abs(top - o.y) > STICK) continue;
			if (Math.abs(left - o.right()) < STICK) return new int[] {o.right(), o.y};
			if (Math.abs(left + w - o.x) < STICK) return new int[] {o.x - w, o.y};
		}
		return null;
	}
}
