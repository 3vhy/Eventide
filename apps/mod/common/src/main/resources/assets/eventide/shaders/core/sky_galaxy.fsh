#version 330

#moj_import <eventide:sky_lib.glsl>

in vec3 ray;

out vec4 fragColor;

// Eventide's own sky: deep space with a violet nebula band arching across it (dark dust lanes through it and
// a bright core low on the horizon), an eclipse ring burning violet and pink, layered stars and slanting
// streaks of light falling through it all.

const vec3 BAND = normalize(vec3(0.42, 0.62, -0.66));
const vec3 CORE = normalize(vec3(-0.12, 0.08, -1.0));
const vec3 ECLIPSE = normalize(vec3(0.85, 0.42, -0.55));

vec3 nebula(vec3 d, float t, float strength) {
	float band = dot(d, BAND);
	float spread = 0.32;
	float body = exp(-band * band / (2.0 * spread * spread));
	vec3 q = d * 2.2 + vec3(t * 0.004, 0.0, t * 0.002);
	float warp = fbm3(q * 1.2 + 3.0, 3);
	float clouds = fbm3(q * 1.6 + warp * 1.4, 5);
	float fine = fbm3(q * 5.0 + warp * 2.0, 3);
	float dust = smoothstep(0.45, 0.7, fbm3(q * 3.2 + 11.0, 4)) * smoothstep(0.25, 0.0, abs(band + 0.04));
	vec3 deep = vec3(0.22, 0.05, 0.42);
	vec3 bright = vec3(0.72, 0.28, 1.0);
	vec3 hot = vec3(1.0, 0.45, 0.9);
	vec3 col = mix(deep, bright, smoothstep(0.35, 0.75, clouds));
	col = mix(col, hot, smoothstep(0.62, 0.88, clouds * 0.7 + fine * 0.4) * 0.6);
	float light = body * (0.35 + 0.9 * smoothstep(0.3, 0.8, clouds)) * (0.75 + 0.5 * fine);
	vec3 neb = col * light * (1.0 - dust * 0.85);
	float core = pow(max(dot(d, CORE), 0.0), 22.0);
	float coreWide = pow(max(dot(d, CORE), 0.0), 5.0);
	neb += vec3(0.85, 0.95, 1.0) * core * 1.6 + vec3(0.55, 0.4, 1.0) * coreWide * 0.5;
	neb += vec3(0.2, 0.75, 0.95) * pow(max(dot(d, CORE), 0.0), 9.0) * 0.35;
	return neb * strength;
}

vec3 eclipse(vec3 d, vec3 color, float glow) {
	vec3 u = normalize(cross(vec3(0.0, 1.0, 0.0), ECLIPSE));
	vec3 v = cross(ECLIPSE, u);
	if (dot(d, ECLIPSE) < 0.7) {
		return color;
	}
	vec2 q = vec2(dot(d, u), dot(d, v)) / 0.085;
	mat2 tilt = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
	vec2 rq = tilt * q;
	vec2 eq = vec2(rq.x, rq.y / 0.62);
	float r = length(q);
	float re = length(eq);
	float aa = fwidth(re) * 1.5;
	float disc = smoothstep(1.0 + aa, 1.0 - aa, r);
	float ring = exp(-pow((re - 1.25) / 0.09, 2.0));
	float ringWide = exp(-pow((re - 1.25) / 0.45, 2.0));
	float halo = exp(-max(r - 1.0, 0.0) * 1.6);
	float side = 0.6 + 0.4 * smoothstep(-1.4, 1.4, rq.x);
	vec3 ringColor = mix(vec3(0.62, 0.3, 1.0), vec3(1.0, 0.5, 0.85), smoothstep(-1.0, 1.0, rq.y));
	color += ringColor * (ring * 1.4 * side + ringWide * 0.25) * glow;
	color += vec3(0.5, 0.25, 0.95) * halo * (1.0 - disc) * 0.18 * glow;
	color = mix(color, vec3(0.004, 0.0, 0.01), disc);
	color += vec3(0.8, 0.55, 1.0) * exp(-pow((r - 1.0) / 0.035, 2.0)) * 0.9 * glow;
	if (rq.y < 0.0) {
		color += ringColor * ring * disc * 1.2 * side * glow;
	}
	return color;
}

void main() {
	vec3 d = normalize(ray);
	float t = SKY_TIME;
	float e = d.y;
	vec3 top = vec3(0.012, 0.008, 0.035);
	vec3 low = vec3(0.09, 0.04, 0.2);
	vec3 color = mix(low, top, smoothstep(-0.05, 0.6, e));
	color += vec3(0.06, 0.18, 0.28) * exp(-abs(e - 0.02) / 0.12) * 0.35;
	color += starField(d, t, SKY_STARS) * smoothstep(-0.05, 0.12, e) * (1.0 - SKY_DAY * 0.85);
	color += nebula(d, t, SKY_NEBULA) * smoothstep(-0.12, 0.15, e);
	color += vec3(0.85, 0.9, 1.0) * streaks(d, t, SKY_METEORS) * 0.55 * (1.0 - SKY_DAY * 0.7);
	color = eclipse(d, color, SKY_GLOW);
	color += vec3(0.55, 0.3, 0.95) * horizonGlow(d, 0.05) * 0.16 * SKY_GLOW;
	color = mix(color, color * 1.4 + vec3(0.08, 0.06, 0.16), SKY_DAY * 0.5);
	color = mix(color, low * 0.35, smoothstep(0.0, -0.3, e));
	fragColor = vec4(finish(color, d), SKY_ALPHA);
}
