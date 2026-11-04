#version 330

#moj_import <eventide:sky_lib.glsl>

in vec3 ray;

out vec4 fragColor;

void main() {
	vec3 d = normalize(ray);
	float t = SKY_TIME;
	vec3 p = d * 1.5 + vec3(0.0, t * 0.008, 0.0);
	vec3 q = vec3(fbm3(p, 4), fbm3(p + vec3(5.2, 1.3, 7.1), 4), fbm3(p + vec3(2.7, 8.4, 3.3), 4));
	vec3 r = vec3(fbm3(p + 3.5 * q + vec3(1.7, 9.2, 0.4) + t * 0.012, 4), fbm3(p + 3.5 * q + vec3(8.3, 2.8, 5.5) - t * 0.01, 4), 0.0);
	float f = fbm3(p + 3.0 * r.xyy, 4);
	vec3 color = mix(vec3(0.05, 0.015, 0.1), vec3(0.3, 0.09, 0.52), clamp(f * f * 2.4, 0.0, 1.0));
	color = mix(color, vec3(0.46, 0.2, 0.74), clamp(length(q) * 0.55 - 0.42, 0.0, 1.0));
	color = mix(color, vec3(0.08, 0.02, 0.15), clamp(r.y * r.y * 1.6 - 0.2, 0.0, 0.7));
	float veins = pow(ridge(fract(f * 2.5 + r.x * 0.5)), 28.0);
	color += vec3(0.86, 0.72, 1.0) * veins * 0.75 * smoothstep(0.25, 0.65, r.x);
	color += vec3(0.95, 0.9, 1.0) * starLayer(d, t) * SKY_STARS * 0.35 * smoothstep(0.0, 0.3, d.y) * (1.0 - SKY_DAY);
	color += vec3(0.45, 0.2, 0.7) * horizonGlow(d, 0.06) * 0.2;
	color = mix(color, color * 1.4 + vec3(0.06, 0.02, 0.1), SKY_DAY * 0.5);
	color = mix(color, vec3(0.1, 0.03, 0.18), smoothstep(0.0, -0.35, d.y));
	color += vec3(0.9, 0.85, 1.0) * streaks(d, t, SKY_METEORS) * 0.3 * (1.0 - SKY_DAY * 0.8);
	fragColor = vec4(finish(color, d), SKY_ALPHA);
}
