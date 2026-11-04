#moj_import <minecraft:dynamictransforms.glsl>

#define SKY_TIME TextureMat[0].x
#define SKY_BRIGHTNESS TextureMat[0].y
#define SKY_ALPHA TextureMat[0].z
#define SKY_STARS TextureMat[0].w
#define SKY_TINT TextureMat[1].rgb
#define SKY_RAIN TextureMat[1].w
#define SKY_SUN TextureMat[2].xyz
#define SKY_DAY TextureMat[2].w
#define SKY_NEBULA ColorModulator.x
#define SKY_GLOW ColorModulator.y
#define SKY_METEORS ColorModulator.z

const float PI = 3.14159265;

float hash13(vec3 p) {
	p = fract(p * 0.1031);
	p += dot(p, p.zyx + 31.32);
	return fract((p.x + p.y) * p.z);
}

float hash12(vec2 p) {
	vec3 q = fract(vec3(p.xyx) * 0.1031);
	q += dot(q, q.yzx + 33.33);
	return fract((q.x + q.y) * q.z);
}

float noise3(vec3 p) {
	vec3 i = floor(p);
	vec3 f = fract(p);
	vec3 u = f * f * (3.0 - 2.0 * f);
	float a = mix(hash13(i), hash13(i + vec3(1, 0, 0)), u.x);
	float b = mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), u.x);
	float c = mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), u.x);
	float d = mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), u.x);
	return mix(mix(a, b, u.y), mix(c, d, u.y), u.z);
}

float noise2(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash12(i), hash12(i + vec2(1, 0)), u.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), u.x), u.y);
}

float fbm3(vec3 p, int octaves) {
	float sum = 0.0;
	float amp = 0.5;
	for (int i = 0; i < octaves; i++) {
		sum += amp * noise3(p);
		p = p * 2.03 + vec3(1.7, 9.2, 3.1);
		amp *= 0.5;
	}
	return sum;
}

float fbm2(vec2 p, int octaves) {
	float sum = 0.0;
	float amp = 0.5;
	for (int i = 0; i < octaves; i++) {
		sum += amp * noise2(p);
		p = mat2(1.6, 1.2, -1.2, 1.6) * p + vec2(3.1, 1.7);
		amp *= 0.5;
	}
	return sum;
}

float ridge(float n) {
	return 1.0 - abs(n * 2.0 - 1.0);
}

float elevation(vec3 d) {
	return asin(clamp(d.y, -1.0, 1.0));
}

float azimuth(vec3 d) {
	return atan(d.z, d.x);
}

vec2 dome(vec3 d, float height) {
	return d.xz / (max(d.y, 0.0) + height);
}

float stars(vec3 d, float density, float time) {
	vec3 p = d * density;
	vec3 cell = floor(p);
	vec3 f = fract(p) - 0.5;
	float h = hash13(cell);
	if (h < 0.86) {
		return 0.0;
	}
	vec3 offset = vec3(hash13(cell + 1.31), hash13(cell + 7.17), hash13(cell + 3.73)) - 0.5;
	float dist = length(f - offset * 0.6);
	float twinkle = 0.65 + 0.35 * sin(time * (0.7 + 2.5 * h) + h * 61.0);
	float size = 0.05 + 0.07 * (h - 0.86) / 0.14;
	return smoothstep(size, 0.0, dist) * twinkle * (0.35 + 0.65 * (h - 0.86) / 0.14);
}

float starLayer(vec3 d, float time) {
	return stars(d, 90.0, time) + 0.6 * stars(d.zxy, 170.0, time * 1.3);
}

// A denser, coloured field of stars in three layers that drift at their own pace (near ones faster, for
// depth), scaled by density (0..2, the Star density setting). Returns their light.
vec3 starField(vec3 d, float time, float density) {
	if (density <= 0.0) {
		return vec3(0.0);
	}
	float k = clamp(density, 0.0, 2.0);
	vec3 col = vec3(0.0);
	for (int i = 0; i < 3; i++) {
		float fi = float(i);
		float a = time * (0.0015 + 0.001 * fi);
		vec3 q = vec3(d.x * cos(a) - d.z * sin(a), d.y, d.x * sin(a) + d.z * cos(a));
		float cells = 70.0 + 60.0 * fi;
		vec3 p = (i == 1 ? q.zxy : i == 2 ? q.yzx : q) * cells;
		vec3 cell = floor(p);
		vec3 f = fract(p) - 0.5;
		float h = hash13(cell + fi * 17.0);
		float threshold = 1.0 - 0.07 * k * (1.0 + fi * 0.5);
		if (h < threshold) {
			continue;
		}
		vec3 offset = vec3(hash13(cell + 1.31), hash13(cell + 7.17), hash13(cell + 3.73)) - 0.5;
		float dist = length(f - offset * 0.6);
		float bright = (h - threshold) / max(1.0 - threshold, 1e-3);
		float size = 0.04 + 0.08 * bright * (1.0 - fi * 0.25);
		float twinkle = 0.6 + 0.4 * sin(time * (0.8 + 2.4 * h) + h * 61.0);
		vec3 tint = mix(vec3(0.75, 0.82, 1.0), mix(vec3(1.0, 0.85, 1.0), vec3(0.7, 0.95, 1.0), hash13(cell + 9.1)), 0.5);
		float core = smoothstep(size, 0.0, dist);
		float halo = smoothstep(size * 3.5, 0.0, dist) * 0.25 * bright;
		col += tint * (core + halo) * twinkle * (0.35 + 0.65 * bright) * (1.0 - fi * 0.2);
	}
	return col;
}

// Streaks of light falling slantwise across the sky (Shooting stars), a scatter of them at once, each
// with a fading tail. amount (0..2) is how many.
float streaks(vec3 d, float time, float amount) {
	if (amount <= 0.0 || d.y < -0.05) {
		return 0.0;
	}
	vec2 p = d.xz / (d.y + 0.35);
	float ang = 0.55;
	p = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * p;
	float total = 0.0;
	for (int layer = 0; layer < 2; layer++) {
		float fl = float(layer);
		float scale = 2.4 + fl * 1.7;
		vec2 q = p * scale + vec2(0.0, time * (0.9 + fl * 0.5));
		vec2 cell = floor(q);
		vec2 f = fract(q);
		float h = hash12(cell + fl * 31.0);
		if (h > 0.18 * amount) {
			continue;
		}
		float x = 0.2 + 0.6 * hash12(cell + 4.7);
		float len = 0.35 + 0.4 * hash12(cell + 8.3);
		float along = clamp((f.y - (1.0 - len)) / len, 0.0, 1.0);
		float inSeg = step(1.0 - len, f.y);
		float across = abs(f.x - x);
		float width = 0.012 + 0.012 * (1.0 - fl);
		total += smoothstep(width, 0.0, across) * inSeg * along * along * (0.6 + 0.4 * h / 0.18);
	}
	return total * smoothstep(-0.05, 0.25, d.y);
}

float horizonGlow(vec3 d, float width) {
	return exp(-abs(d.y) / width);
}

vec3 finish(vec3 color, vec3 d) {
	color = mix(color, vec3(dot(color, vec3(0.299, 0.587, 0.114))) * 0.75, SKY_RAIN * 0.45);
	color *= SKY_TINT * SKY_BRIGHTNESS;
	return max(color, 0.0);
}
