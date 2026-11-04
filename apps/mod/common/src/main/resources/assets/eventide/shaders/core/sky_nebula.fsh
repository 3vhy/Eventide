#version 330

#moj_import <eventide:sky_lib.glsl>

in vec3 ray;

out vec4 fragColor;

// Nebula: deep blue night filled with billowing clouds of cyan and violet gas lit from within, a hot white
// heart low in the sky, dense stars and slow streaks of light.

void main() {
	vec3 d = normalize(ray);
	float t = SKY_TIME;
	float e = d.y;
	vec3 color = mix(vec3(0.03, 0.05, 0.14), vec3(0.008, 0.01, 0.04), smoothstep(0.0, 0.8, e));
	color += starField(d, t, SKY_STARS * 1.2) * smoothstep(-0.05, 0.15, e) * (1.0 - SKY_DAY * 0.85);
	vec3 q = d * vec3(1.7, 2.4, 1.7) + vec3(t * 0.006, 0.0, -t * 0.003);
	float warp = fbm3(q * 0.9 + 2.0, 3);
	float gas = fbm3(q + warp * 1.6, 5);
	float gas2 = fbm3(q * 1.8 - warp + 7.0, 4);
	float lit = smoothstep(0.42, 0.78, gas);
	vec3 cyan = vec3(0.12, 0.62, 0.9);
	vec3 violet = vec3(0.55, 0.2, 0.95);
	vec3 rose = vec3(0.95, 0.35, 0.8);
	vec3 gasColor = mix(violet, cyan, smoothstep(0.35, 0.7, gas2));
	gasColor = mix(gasColor, rose, smoothstep(0.7, 0.9, gas) * 0.45);
	float fade = smoothstep(-0.1, 0.25, e);
	color += gasColor * lit * 0.75 * SKY_NEBULA * fade;
	color *= 1.0 - 0.55 * smoothstep(0.58, 0.8, fbm3(q * 2.4 + 13.0, 3)) * fade * SKY_NEBULA;
	vec3 heart = normalize(vec3(0.6, 0.18, 0.78));
	float h = max(dot(d, heart), 0.0);
	color += vec3(0.9, 0.97, 1.0) * pow(h, 260.0) * 1.3 * SKY_GLOW + vec3(0.3, 0.6, 1.0) * pow(h, 26.0) * 0.28 * SKY_GLOW;
	color += vec3(0.85, 0.95, 1.0) * streaks(d, t * 0.7, SKY_METEORS) * 0.45 * (1.0 - SKY_DAY * 0.7);
	color += vec3(0.2, 0.5, 0.85) * horizonGlow(d, 0.06) * 0.2 * SKY_GLOW;
	color = mix(color, color * 1.5 + vec3(0.06, 0.1, 0.18), SKY_DAY * 0.5);
	color = mix(color, vec3(0.02, 0.03, 0.08), smoothstep(0.0, -0.3, e));
	fragColor = vec4(finish(color, d), SKY_ALPHA);
}
