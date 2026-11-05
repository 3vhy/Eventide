package dev.eventide.client.knife;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One rigid piece of a knife, as quads with smooth normals, built once from code: ground blades,
 * lofted handles and guards, rings, rounded plates and pins. Units are pixels (a sixteenth of a block).
 * A knife's blade points up (+Y) with its edge towards +X and its flats facing ±Z. Every vertex records
 * its {@link KnifeShading} material, how near the cutting edge it is, and where it is on the part for the
 * finish pattern (on a blade: across it from its middle line, and along it from its root, in pixels, so
 * patterns follow a curved blade); the finish itself is chosen when it's drawn.
 */
final class Shape {
	private static final int STRIDE = 10;
	private float[] data = new float[STRIDE * 4 * 128];
	private int quads = 0;

	int quads() {
		return quads;
	}

	/** Widens {@code box} (min x, min y, max x, max y) to take in every point of the shape. */
	void bounds(float[] box) {
		for (int i = 0; i < quads * 4; i++) {
			float x = data[i * STRIDE];
			float y = data[i * STRIDE + 1];
			box[0] = Math.min(box[0], x);
			box[1] = Math.min(box[1], y);
			box[2] = Math.max(box[2], x);
			box[3] = Math.max(box[3], y);
		}
	}

	/** A vertex: position, normal, pattern position, edge nearness (0..1) and material. */
	static float[] vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v, float edge, int material) {
		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length < 1e-6f) {
			length = 1;
			nz = 1;
		}
		return new float[] {x, y, z, nx / length, ny / length, nz / length, u, v, edge, material};
	}

	void quad(float[] a, float[] b, float[] c, float[] d) {
		if ((quads + 1) * STRIDE * 4 > data.length) data = Arrays.copyOf(data, data.length * 2);
		int at = quads * STRIDE * 4;
		System.arraycopy(a, 0, data, at, STRIDE);
		System.arraycopy(b, 0, data, at + STRIDE, STRIDE);
		System.arraycopy(c, 0, data, at + STRIDE * 2, STRIDE);
		System.arraycopy(d, 0, data, at + STRIDE * 3, STRIDE);
		quads++;
	}

	void triangle(float[] a, float[] b, float[] c) {
		quad(a, b, c, c);
	}

	/** Adds every quad of {@code other}, moved by {@code (dx, dy, dz)}. */
	void add(Shape other, float dx, float dy, float dz) {
		for (int q = 0; q < other.quads; q++) {
			float[][] corners = new float[4][];
			for (int k = 0; k < 4; k++) {
				float[] v = Arrays.copyOfRange(other.data, (q * 4 + k) * STRIDE, (q * 4 + k + 1) * STRIDE);
				v[0] += dx;
				v[1] += dy;
				v[2] += dz;
				corners[k] = v;
			}
			quad(corners[0], corners[1], corners[2], corners[3]);
		}
	}

	/** Draws the shape with Eventide's shader: {@code finish} is the finish, {@code hue} and {@code saturation} the edge glow's colour. */
	void emit(PoseStack.Pose pose, VertexConsumer out, int light, int finish, float hue, float saturation, int frame) {
		int lastMaterial = -1;
		int lastEdge = -1;
		int color = 0;
		for (int i = 0; i < quads * 4; i++) {
			int at = i * STRIDE;
			int material = (int) data[at + 9];
			int edge = Math.round(data[at + 8] * 255);
			if (material != lastMaterial || edge != lastEdge) {
				color = KnifeShading.color(material, finish, hue, saturation, data[at + 8]);
				lastMaterial = material;
				lastEdge = edge;
			}
			out.addVertex(pose, data[at], data[at + 1], data[at + 2]).setColor(color).setUv(data[at + 6], data[at + 7]).setOverlay(frame)
					.setLight(light).setNormal(pose, data[at + 3], data[at + 4], data[at + 5]);
		}
	}

	/** Draws the shape with Minecraft's entity shading and a plain colour per material (for shader packs); the edge takes {@code glow}. */
	void emitPlain(PoseStack.Pose pose, VertexConsumer out, int light, int overlay, int finish, int glow) {
		for (int i = 0; i < quads * 4; i++) {
			int at = i * STRIDE;
			int material = (int) data[at + 9];
			int color = KnifeShading.plain(material, finish, glow);
			out.addVertex(pose, data[at], data[at + 1], data[at + 2]).setColor(color).setUv(0.98f, 0.98f).setOverlay(overlay).setLight(light)
					.setNormal(pose, data[at + 3], data[at + 4], data[at + 5]);
		}
	}

	/**
	 * Draws a glowing shell around the shape for the luminous outline: every vertex pushed {@code width} pixels out
	 * along its normal, in {@code color}. The glow shader lights it most where it's seen edge-on, so it reads as a
	 * soft rim of light round the knife's silhouette.
	 */
	void emitGlow(PoseStack.Pose pose, VertexConsumer out, int color, float width) {
		for (int i = 0; i < quads * 4; i++) {
			int at = i * STRIDE;
			float nx = data[at + 3];
			float ny = data[at + 4];
			float nz = data[at + 5];
			out.addVertex(pose, data[at] + nx * width, data[at + 1] + ny * width, data[at + 2] + nz * width).setColor(color).setNormal(pose, nx, ny, nz);
		}
	}

	/**
	 * A ground blade between its spine and edge outlines, base first. Each station gives the spine point,
	 * the edge point and the blade's half thickness at the spine. Across the blade the flat runs from the
	 * spine to {@code grind}, then a bevel falls to the edge; an optional false edge bevels the spine near
	 * the tip, and an optional fuller is a rounded groove along the flat.
	 */
	static final class Blade {
		final List<float[]> stations = new ArrayList<>();
		float grind = 0.5f;
		float edgeHalf = 0.03f;
		boolean doubleEdged = false;
		float swedgeFrom = 2;
		float swedgeWidth = 0.25f;
		float fullerFrom = 2;
		float fullerTo = 2;
		float fullerCentre = 0.3f;
		float fullerWidth = 0.15f;
		float fullerDepth = 0;
		int flatColumns = 4;
		int bevelColumns = 3;
		/** Each station's distance along the blade's middle line, and its width, filled in when it's built. */
		float[] along;
		float[] width;

		Blade station(float sx, float sy, float ex, float ey, float half) {
			stations.add(new float[] {sx, sy, ex, ey, half});
			return this;
		}
	}

	void blade(Blade b) {
		int n = b.stations.size();
		float[][] st = b.stations.toArray(new float[0][]);
		b.along = new float[n];
		b.width = new float[n];
		float run = (st[0][1] + st[0][3]) / 2;
		for (int i = 0; i < n; i++) {
			if (i > 0) {
				run += (float) Math.hypot((st[i][0] + st[i][2] - st[i - 1][0] - st[i - 1][2]) / 2, (st[i][1] + st[i][3] - st[i - 1][1] - st[i - 1][3]) / 2);
			}
			b.along[i] = run;
			b.width[i] = (float) Math.hypot(st[i][2] - st[i][0], st[i][3] - st[i][1]);
		}
		List<float[]> regions = new ArrayList<>();
		if (b.doubleEdged) {
			regions.add(new float[] {0, b.grind, b.bevelColumns, KnifeShading.EDGE});
			regions.add(new float[] {b.grind, 1 - b.grind, Math.max(1, b.flatColumns / 2), KnifeShading.BLADE});
			regions.add(new float[] {1 - b.grind, 1, b.bevelColumns, KnifeShading.EDGE});
		} else {
			if (b.swedgeFrom < 1) {
				regions.add(new float[] {0, b.swedgeWidth, 2, KnifeShading.BLADE});
				regions.add(new float[] {b.swedgeWidth, b.grind, b.flatColumns, KnifeShading.BLADE});
			} else {
				regions.add(new float[] {0, b.grind, b.flatColumns, KnifeShading.BLADE});
			}
			regions.add(new float[] {b.grind, 1, b.bevelColumns, KnifeShading.EDGE});
		}
		for (float[] region : regions) {
			int columns = (int) region[2];
			float[] fs = new float[columns + 1];
			for (int j = 0; j <= columns; j++) fs[j] = region[0] + (region[1] - region[0]) * j / columns;
			for (int side = -1; side <= 1; side += 2) bladeRegion(b, st, fs, side, (int) region[3]);
		}
		bladeWall(b, st, 0, KnifeShading.BLADE);
		bladeWall(b, st, 1, KnifeShading.EDGE);
		bladeCap(b, st, 0, -1);
		float[] last = st[n - 1];
		if (Math.hypot(last[0] - last[2], last[1] - last[3]) > 0.05) bladeCap(b, st, n - 1, 1);
	}

	private static float bladeZ(Blade b, float[][] st, int i, float f) {
		float h = st[i][4];
		float z;
		if (b.doubleEdged) {
			float d = Math.min(f, 1 - f);
			z = d >= b.grind ? h : b.edgeHalf + (h - b.edgeHalf) * d / b.grind;
		} else {
			z = f <= b.grind ? h : b.edgeHalf + (h - b.edgeHalf) * (1 - f) / (1 - b.grind);
			float at = i / (float) (st.length - 1);
			if (at > b.swedgeFrom) {
				float w = Math.min(1, (at - b.swedgeFrom) / 0.08f);
				float swedge = b.edgeHalf + (h - b.edgeHalf) * Math.min(1, f / b.swedgeWidth);
				z = Math.min(z, h + (swedge - h) * w);
			}
		}
		if (b.fullerDepth > 0) {
			float at = i / (float) (st.length - 1);
			if (at > b.fullerFrom && at < b.fullerTo) {
				float along = Math.min(1, Math.min(at - b.fullerFrom, b.fullerTo - at) / 0.06f);
				float across = (f - b.fullerCentre) / (b.fullerWidth / 2);
				if (Math.abs(across) < 1) z -= b.fullerDepth * along * (float) (0.5 + 0.5 * Math.cos(Math.PI * across));
			}
		}
		return Math.max(z, b.edgeHalf);
	}

	/** How near the blade's outline a point {@code f} across it is (0..1): its edges and, on a single-edged blade, its spine too, which glow. */
	private static float edgeOf(Blade b, float f) {
		if (b.doubleEdged) return 1 - 2 * Math.min(f, 1 - f);
		return Math.max(0, Math.max((f - 0.55f) / 0.45f, 1 - f / 0.22f));
	}

	private static float[] bladePoint(Blade b, float[][] st, int i, float f, int side) {
		float[] s = st[i];
		return new float[] {s[0] + (s[2] - s[0]) * f, s[1] + (s[3] - s[1]) * f, side * bladeZ(b, st, i, f)};
	}

	private void bladeRegion(Blade b, float[][] st, float[] fs, int side, int material) {
		int n = st.length;
		int m = fs.length;
		float[][][] p = new float[n][m][];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < m; j++) p[i][j] = bladePoint(b, st, i, fs[j], side);
		}
		float[][][] normal = new float[n][m][];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < m; j++) {
				int i0 = Math.max(0, i - 1);
				int i1 = Math.min(n - 1, i + 1);
				int j0 = Math.max(0, j - 1);
				int j1 = Math.min(m - 1, j + 1);
				float[] along = sub(p[i1][j], p[i0][j]);
				float[] across = sub(p[i][j1], p[i][j0]);
				float[] nrm = cross(across, along);
				if (length(nrm) < 1e-7f && i > 0) nrm = normal[i - 1][j];
				if (length(nrm) < 1e-7f) nrm = new float[] {0, 0, side};
				if (nrm[2] * side < 0) nrm = scale(nrm, -1);
				normal[i][j] = normalize(nrm);
			}
		}
		for (int i = 0; i + 1 < n; i++) {
			for (int j = 0; j + 1 < m; j++) {
				quad(bv(p[i][j], normal[i][j], i, fs[j], material, b), bv(p[i + 1][j], normal[i + 1][j], i + 1, fs[j], material, b),
						bv(p[i + 1][j + 1], normal[i + 1][j + 1], i + 1, fs[j + 1], material, b), bv(p[i][j + 1], normal[i][j + 1], i, fs[j + 1], material, b));
			}
		}
	}

	private static float[] bv(float[] p, float[] n, int i, float f, int material, Blade b) {
		return vertex(p[0], p[1], p[2], n[0], n[1], n[2], (f - 0.5f) * b.width[i], b.along[i], edgeOf(b, f), material);
	}

	/** Where a point on station {@code i}, {@code f} across, is on the blade's pattern: across from the middle line, then along. */
	private static float[] bladeUv(Blade b, int i, float f) {
		return new float[] {(f - 0.5f) * b.width[i], b.along[i]};
	}

	/** The spine ({@code f} 0) or edge ({@code f} 1) wall, its normals smooth along gentle curves and sharp at teeth. */
	private void bladeWall(Blade b, float[][] st, float f, int material) {
		int n = st.length;
		float[][] point = new float[n][];
		for (int i = 0; i < n; i++) point[i] = bladePoint(b, st, i, f, 1);
		float[][] segment = new float[n - 1][];
		for (int i = 0; i + 1 < n; i++) {
			float dx = point[i + 1][0] - point[i][0];
			float dy = point[i + 1][1] - point[i][1];
			float[] out = normalize(new float[] {dy, -dx, 0});
			float ox = f == 0 ? st[i][0] - st[i][2] : st[i][2] - st[i][0];
			float oy = f == 0 ? st[i][1] - st[i][3] : st[i][3] - st[i][1];
			if (out[0] * ox + out[1] * oy < 0) out = scale(out, -1);
			segment[i] = out;
		}
		for (int i = 0; i + 1 < n; i++) {
			float[] na = wallNormal(segment, i, true);
			float[] nb = wallNormal(segment, i, false);
			float[] a = point[i];
			float[] c = point[i + 1];
			float[] ua = bladeUv(b, i, f);
			float[] uc = bladeUv(b, i + 1, f);
			float edge = 1;
			quad(vertex(a[0], a[1], a[2], na[0], na[1], na[2], ua[0], ua[1], edge, material),
					vertex(c[0], c[1], c[2], nb[0], nb[1], nb[2], uc[0], uc[1], edge, material),
					vertex(c[0], c[1], -c[2], nb[0], nb[1], nb[2], uc[0], uc[1], edge, material),
					vertex(a[0], a[1], -a[2], na[0], na[1], na[2], ua[0], ua[1], edge, material));
		}
	}

	private static float[] wallNormal(float[][] segment, int i, boolean start) {
		int other = start ? i - 1 : i + 1;
		if (other < 0 || other >= segment.length) return segment[i];
		float dot = segment[i][0] * segment[other][0] + segment[i][1] * segment[other][1];
		if (dot < 0.9f) return segment[i];
		return normalize(new float[] {segment[i][0] + segment[other][0], segment[i][1] + segment[other][1], 0});
	}

	/** Closes the blade across one station (the tip glows with the outline; the root, hidden in the guard, doesn't). */
	private void bladeCap(Blade b, float[][] st, int i, int direction) {
		int other = Math.max(0, Math.min(st.length - 1, i - direction));
		float tx = (st[i][0] + st[i][2] - st[other][0] - st[other][2]) / 2;
		float ty = (st[i][1] + st[i][3] - st[other][1] - st[other][3]) / 2;
		float[] n = normalize(new float[] {tx, ty, 0});
		int steps = 8;
		for (int k = 0; k < steps; k++) {
			float f0 = k / (float) steps;
			float f1 = (k + 1) / (float) steps;
			float[] a = bladePoint(b, st, i, f0, 1);
			float[] c = bladePoint(b, st, i, f1, 1);
			float[] ua = bladeUv(b, i, f0);
			float[] uc = bladeUv(b, i, f1);
			float glow = direction > 0 ? 1 : 0;
			quad(vertex(a[0], a[1], a[2], n[0], n[1], 0, ua[0], ua[1], glow, KnifeShading.BLADE),
					vertex(c[0], c[1], c[2], n[0], n[1], 0, uc[0], uc[1], glow, KnifeShading.BLADE),
					vertex(c[0], c[1], -c[2], n[0], n[1], 0, uc[0], uc[1], glow, KnifeShading.BLADE),
					vertex(a[0], a[1], -a[2], n[0], n[1], 0, ua[0], ua[1], glow, KnifeShading.BLADE));
		}
	}

	/** One ring of a loft: its centre, the direction it faces, the direction its X radius points (or null to pick one) and its radii. */
	record Ring(float x, float y, float z, float ax, float ay, float az, float rx, float rz, float[] side) {
		static Ring along(float x, float y, float z, float rx, float rz) {
			return new Ring(x, y, z, 0, 1, 0, rx, rz, null);
		}

		static Ring facing(float x, float y, float z, float ax, float ay, float az, float rx, float rz) {
			return new Ring(x, y, z, ax, ay, az, rx, rz, null);
		}
	}

	/**
	 * A smooth tube through {@code rings} with a rounded cross-section ({@code squareness} 0 for an ellipse,
	 * up to about 0.8 for a rounded box). Ends are closed flat ({@code cap}) or domed ({@code dome}).
	 * {@code planar} patterns use the part's own X and Y (so a guard's finish lines up with its blade);
	 * otherwise the pattern runs along the tube and around it.
	 */
	void loft(Ring[] rings, int segments, float squareness, int material, int startEnd, int endEnd, boolean planar) {
		List<Ring> all = new ArrayList<>(List.of(rings));
		if (startEnd == DOME) all.addAll(0, dome(rings[0], rings.length > 1 ? rings[1] : rings[0], true));
		if (endEnd == DOME) all.addAll(dome(rings[rings.length - 1], rings.length > 1 ? rings[rings.length - 2] : rings[0], false));
		int count = all.size();
		float[][][] p = new float[count][segments + 1][];
		float[][] centre = new float[count][];
		float travelled = 0;
		float[] along = new float[count];
		for (int r = 0; r < count; r++) {
			Ring ring = all.get(r);
			float[] frame = frame(ring);
			centre[r] = new float[] {ring.x, ring.y, ring.z};
			if (r > 0) travelled += length(sub(centre[r], centre[r - 1]));
			along[r] = travelled;
			for (int s = 0; s <= segments; s++) {
				double angle = 2 * Math.PI * s / segments;
				float c = (float) Math.cos(angle);
				float sn = (float) Math.sin(angle);
				float k = 2f / (2f + squareness * 6f);
				float cx = Math.signum(c) * (float) Math.pow(Math.abs(c), k) * ring.rx;
				float cz = Math.signum(sn) * (float) Math.pow(Math.abs(sn), k) * ring.rz;
				p[r][s] = new float[] {ring.x + frame[0] * cx + frame[3] * cz, ring.y + frame[1] * cx + frame[4] * cz, ring.z + frame[2] * cx + frame[5] * cz};
			}
		}
		float[][][] normal = new float[count][segments + 1][];
		for (int r = 0; r < count; r++) {
			for (int s = 0; s <= segments; s++) {
				int r0 = Math.max(0, r - 1);
				int r1 = Math.min(count - 1, r + 1);
				int sPrev = s == 0 ? segments - 1 : s - 1;
				int sNext = s == segments ? 1 : s + 1;
				float[] around = sub(p[r][sNext], p[r][sPrev]);
				float[] lengthwise = sub(p[r1][s], p[r0][s]);
				float[] nrm = cross(around, lengthwise);
				float[] outward = sub(p[r][s], centre[r]);
				if (length(nrm) < 1e-7f) nrm = outward;
				if (dot(nrm, outward) < 0 && length(outward) > 1e-5f) nrm = scale(nrm, -1);
				if (length(outward) < 1e-5f) {
					Ring ring = all.get(r);
					nrm = new float[] {ring.ax, ring.ay, ring.az};
					if (r == 0) nrm = scale(nrm, -1);
				}
				normal[r][s] = normalize(nrm);
			}
		}
		float circumference = 0;
		for (int s = 0; s < segments; s++) circumference += length(sub(p[count / 2][s + 1], p[count / 2][s]));
		for (int r = 0; r + 1 < count; r++) {
			for (int s = 0; s < segments; s++) {
				quad(lv(p[r][s], normal[r][s], along[r], circumference * s / segments, material, planar),
						lv(p[r][s + 1], normal[r][s + 1], along[r], circumference * (s + 1) / segments, material, planar),
						lv(p[r + 1][s + 1], normal[r + 1][s + 1], along[r + 1], circumference * (s + 1) / segments, material, planar),
						lv(p[r + 1][s], normal[r + 1][s], along[r + 1], circumference * s / segments, material, planar));
			}
		}
		if (startEnd == CAP) flatCap(p[0], all.get(0), centre[0], material, planar, -1);
		if (endEnd == CAP) flatCap(p[count - 1], all.get(count - 1), centre[count - 1], material, planar, 1);
	}

	static final int OPEN = 0;
	static final int CAP = 1;
	static final int DOME = 2;

	private static float[] lv(float[] p, float[] n, float along, float around, int material, boolean planar) {
		return planar ? vertex(p[0], p[1], p[2], n[0], n[1], n[2], p[0], p[1], 0, material)
				: vertex(p[0], p[1], p[2], n[0], n[1], n[2], along, around, 0, material);
	}

	private static List<Ring> dome(Ring end, Ring neighbour, boolean start) {
		float ax = end.x - neighbour.x;
		float ay = end.y - neighbour.y;
		float az = end.z - neighbour.z;
		float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
		if (len < 1e-5f) {
			ax = end.ax;
			ay = end.ay;
			az = end.az;
			len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
			if (start) {
				ax = -ax;
				ay = -ay;
				az = -az;
			}
		}
		ax /= len;
		ay /= len;
		az /= len;
		float depth = Math.min(end.rx, end.rz);
		List<Ring> out = new ArrayList<>();
		int steps = 4;
		for (int k = 1; k <= steps; k++) {
			double angle = Math.PI / 2 * k / steps;
			float shrink = (float) Math.cos(angle);
			float push = (float) Math.sin(angle) * depth;
			out.add(new Ring(end.x + ax * push, end.y + ay * push, end.z + az * push, end.ax, end.ay, end.az, Math.max(0.0005f, end.rx * shrink),
					Math.max(0.0005f, end.rz * shrink), end.side));
		}
		if (start) java.util.Collections.reverse(out);
		return out;
	}

	private void flatCap(float[][] ring, Ring at, float[] centre, int material, boolean planar, int direction) {
		float[] n = normalize(new float[] {at.ax * direction, at.ay * direction, at.az * direction});
		float[] c = vertex(centre[0], centre[1], centre[2], n[0], n[1], n[2], planar ? centre[0] : 0, planar ? centre[1] : 0, 0, material);
		for (int s = 0; s + 1 < ring.length; s++) {
			float[] a = ring[s];
			float[] b = ring[s + 1];
			triangle(c, vertex(a[0], a[1], a[2], n[0], n[1], n[2], planar ? a[0] : 1, planar ? a[1] : s, 0, material),
					vertex(b[0], b[1], b[2], n[0], n[1], n[2], planar ? b[0] : 1, planar ? b[1] : s + 1, 0, material));
		}
	}

	private static float[] frame(Ring ring) {
		float[] a = normalize(new float[] {ring.ax, ring.ay, ring.az});
		float[] r = ring.side != null ? ring.side.clone() : Math.abs(a[0]) > 0.9f ? new float[] {0, 1, 0} : new float[] {1, 0, 0};
		r = sub(r, scale(a, dot(r, a)));
		r = normalize(r);
		float[] z = cross(a, r);
		return new float[] {r[0], r[1], r[2], z[0], z[1], z[2]};
	}

	/** A ring (torus) in the XY plane around (cx, cy, cz): {@code radius} to the tube's middle, {@code tube} thick. */
	void torus(float cx, float cy, float cz, float radius, float tube, float tubeDepth, int around, int segments, int material) {
		Ring[] rings = new Ring[around + 1];
		for (int i = 0; i <= around; i++) {
			double a = 2 * Math.PI * i / around;
			float x = cx + (float) Math.cos(a) * radius;
			float y = cy + (float) Math.sin(a) * radius;
			rings[i] = new Ring(x, y, cz, -(float) Math.sin(a), (float) Math.cos(a), 0, tube, tubeDepth, new float[] {(float) Math.cos(a), (float) Math.sin(a), 0});
		}
		loft(rings, segments, 0, material, OPEN, OPEN, true);
	}

	/** A short cylinder along Z (a pin, screw head or pivot), capped both ends. */
	void pin(float x, float y, float z0, float z1, float radius, int material) {
		loft(new Ring[] {Ring.facing(x, y, z0, 0, 0, 1, radius, radius), Ring.facing(x, y, z1, 0, 0, 1, radius, radius)}, 12, 0, material, CAP, CAP, true);
	}

	/**
	 * A flat plate: the outline (x, y pairs, either winding) between {@code z0} and {@code z1}, its edge rounded
	 * by {@code round}. For handle scales, guards and the like.
	 */
	void plate(float[] outline, float z0, float z1, float round, int material) {
		int n = outline.length / 2;
		float[] xs = new float[n];
		float[] ys = new float[n];
		for (int i = 0; i < n; i++) {
			xs[i] = outline[i * 2];
			ys[i] = outline[i * 2 + 1];
		}
		if (area(xs, ys) < 0) {
			reverse(xs);
			reverse(ys);
		}
		float r = Math.min(round, (z1 - z0) / 2);
		float[][] normal2 = new float[n][];
		for (int i = 0; i < n; i++) {
			int prev = (i + n - 1) % n;
			int next = (i + 1) % n;
			float[] e0 = normalize(new float[] {ys[i] - ys[prev], -(xs[i] - xs[prev]), 0});
			float[] e1 = normalize(new float[] {ys[next] - ys[i], -(xs[next] - xs[i]), 0});
			float[] avg = normalize(new float[] {e0[0] + e1[0], e0[1] + e1[1], 0});
			float cos = Math.max(0.35f, dot(avg, e1));
			normal2[i] = new float[] {avg[0] / cos, avg[1] / cos};
		}
		float[] ix = new float[n];
		float[] iy = new float[n];
		for (int i = 0; i < n; i++) {
			ix[i] = xs[i] - normal2[i][0] * r;
			iy[i] = ys[i] - normal2[i][1] * r;
		}
		int[] tris = triangulate(ix, iy);
		for (int side = -1; side <= 1; side += 2) {
			float z = side > 0 ? z1 : z0;
			for (int t = 0; t < tris.length; t += 3) {
				int a = tris[t];
				int b = tris[t + 1];
				int c = tris[t + 2];
				triangle(vertex(ix[a], iy[a], z, 0, 0, side, ix[a], iy[a], 0, material), vertex(ix[b], iy[b], z, 0, 0, side, ix[b], iy[b], 0, material),
						vertex(ix[c], iy[c], z, 0, 0, side, ix[c], iy[c], 0, material));
			}
		}
		int steps = 6;
		float zc0 = z0 + r;
		float zc1 = z1 - r;
		float[][] profile = new float[steps * 2 + 2][];
		int k = 0;
		for (int s = 0; s <= steps; s++) {
			double angle = Math.PI / 2 * s / steps;
			profile[k++] = new float[] {(float) Math.sin(angle), zc1 + r * (float) Math.cos(angle), (float) Math.sin(angle), (float) Math.cos(angle)};
		}
		for (int s = 0; s <= steps; s++) {
			double angle = Math.PI / 2 + Math.PI / 2 * s / steps;
			profile[k++] = new float[] {(float) Math.sin(angle), zc0 + r * (float) Math.cos(angle), (float) Math.sin(angle), (float) Math.cos(angle)};
		}
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			for (int s = 0; s + 1 < profile.length; s++) {
				float[] pa = profile[s];
				float[] pb = profile[s + 1];
				quad(edgeVertex(ix, iy, normal2, i, pa, r, material), edgeVertex(ix, iy, normal2, j, pa, r, material),
						edgeVertex(ix, iy, normal2, j, pb, r, material), edgeVertex(ix, iy, normal2, i, pb, r, material));
			}
		}
	}

	private static float[] edgeVertex(float[] ix, float[] iy, float[][] normal2, int i, float[] profile, float r, int material) {
		float[] n2 = normalize(new float[] {normal2[i][0], normal2[i][1], 0});
		float x = ix[i] + normal2[i][0] * r * profile[0];
		float y = iy[i] + normal2[i][1] * r * profile[0];
		return vertex(x, y, profile[1], n2[0] * profile[2], n2[1] * profile[2], profile[3], x, y, 0, material);
	}

	private static float area(float[] xs, float[] ys) {
		float sum = 0;
		for (int i = 0; i < xs.length; i++) {
			int j = (i + 1) % xs.length;
			sum += xs[i] * ys[j] - xs[j] * ys[i];
		}
		return sum / 2;
	}

	private static void reverse(float[] a) {
		for (int i = 0, j = a.length - 1; i < j; i++, j--) {
			float t = a[i];
			a[i] = a[j];
			a[j] = t;
		}
	}

	/** Ear clipping for a simple polygon wound counter-clockwise: triangle corner indices, three at a time. */
	private static int[] triangulate(float[] xs, float[] ys) {
		int n = xs.length;
		List<Integer> left = new ArrayList<>();
		for (int i = 0; i < n; i++) left.add(i);
		int[] out = new int[Math.max(0, (n - 2) * 3)];
		int k = 0;
		int guard = 0;
		while (left.size() > 3 && guard++ < n * n) {
			boolean clipped = false;
			for (int i = 0; i < left.size(); i++) {
				int a = left.get((i + left.size() - 1) % left.size());
				int b = left.get(i);
				int c = left.get((i + 1) % left.size());
				float cross = (xs[b] - xs[a]) * (ys[c] - ys[a]) - (ys[b] - ys[a]) * (xs[c] - xs[a]);
				if (cross <= 1e-7f) continue;
				boolean inside = false;
				for (int other : left) {
					if (other == a || other == b || other == c) continue;
					if (inTriangle(xs[other], ys[other], xs[a], ys[a], xs[b], ys[b], xs[c], ys[c])) {
						inside = true;
						break;
					}
				}
				if (inside) continue;
				out[k++] = a;
				out[k++] = b;
				out[k++] = c;
				left.remove(i);
				clipped = true;
				break;
			}
			if (!clipped) break;
		}
		if (left.size() == 3) {
			out[k++] = left.get(0);
			out[k++] = left.get(1);
			out[k++] = left.get(2);
		}
		return Arrays.copyOf(out, k);
	}

	private static boolean inTriangle(float px, float py, float ax, float ay, float bx, float by, float cx, float cy) {
		float d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
		float d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
		float d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
		boolean neg = d1 < 0 || d2 < 0 || d3 < 0;
		boolean pos = d1 > 0 || d2 > 0 || d3 > 0;
		return !(neg && pos);
	}

	static float[] add(float[] a, float[] b) {
		return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
	}

	static float[] sub(float[] a, float[] b) {
		return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	static float[] scale(float[] a, float k) {
		return new float[] {a[0] * k, a[1] * k, a[2] * k};
	}

	static float dot(float[] a, float[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	static float[] cross(float[] a, float[] b) {
		return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	static float length(float[] a) {
		return (float) Math.sqrt(dot(a, a));
	}

	static float[] normalize(float[] a) {
		float l = length(a);
		return l < 1e-9f ? new float[] {0, 0, 1} : scale(a, 1 / l);
	}
}
