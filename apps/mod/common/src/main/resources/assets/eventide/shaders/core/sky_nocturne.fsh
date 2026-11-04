#version 330

#moj_import <eventide:sky_lib.glsl>

in vec3 ray;

out vec4 fragColor;

void main() {
	vec3 d = normalize(ray);
	float t = SKY_TIME;
	vec3 base = mix(vec3(0.16, 0.1, 0.2), vec3(0.055, 0.035, 0.08), smoothstep(-0.05, 0.8, d.y));
	vec3 color = base;
	color += vec3(0.95, 0.9, 1.0) * starLayer(d, t) * SKY_STARS * 0.7 * smoothstep(0.0, 0.2, d.y) * (1.0 - SKY_DAY * 0.9);
	vec3 p = d * vec3(1.6, 2.6, 1.6) + vec3(t * 0.012, 0.0, t * 0.006);
	float warp = fbm3(p * 0.9 + vec3(4.0, 1.0, 2.0), 3);
	float f = fbm3(p + warp * 1.1, 5);
	float body = smoothstep(0.5, 0.74, f);
	float rim = smoothstep(0.44, 0.53, f) * (1.0 - smoothstep(0.53, 0.66, f));
	float fade = smoothstep(-0.02, 0.2, d.y);
	vec3 cloud = mix(vec3(0.19, 0.12, 0.26), vec3(0.3, 0.19, 0.4), fbm3(p * 2.0 + 5.0, 2));
	color = mix(color, cloud, body * 0.8 * fade);
	color += vec3(0.66, 0.46, 0.9) * rim * 0.3 * fade;
	color += vec3(0.42, 0.26, 0.6) * horizonGlow(d, 0.07) * 0.22;
	color = mix(color, color * 1.6 + vec3(0.08, 0.05, 0.12), SKY_DAY * 0.55);
	color = mix(color, base * 0.9, smoothstep(0.0, -0.3, d.y));
	color += vec3(0.85, 0.85, 1.0) * streaks(d, t, SKY_METEORS) * 0.35 * (1.0 - SKY_DAY * 0.8);
	fragColor = vec4(finish(color, d), SKY_ALPHA);
}
