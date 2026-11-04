#version 330

// What each part of a knife looks like: Eventide's finishes, the filigree and lattice patterns, and the
// handle materials. Materials, finishes and knives are numbered the same way as in KnifeShading.java,
// Finish.java and KnifeModel.java.

#define M_BLADE 0
#define M_EDGE 1
#define M_GUARD 2
#define M_HARDWARE 3
#define M_RUBBER 4
#define M_POLYMER 5
#define M_WOOD 6
#define M_CORD 7
#define M_CARBON 8
#define M_RING 9
#define M_ENERGY 10
#define M_HANDLE_PAINT 11

#define F_OBSIDIAN 0
#define F_CRYSTAL 1
#define F_MIDNIGHT 2
#define F_NEBULA 3
#define F_AURORA 4
#define F_FROST 5
#define F_ROSE 6
#define F_VOID 7
#define F_CHROME 8

struct Surface {
    vec3 albedo;
    float metal;
    float rough;
    float aniso;
    float coat;
    vec3 emissive;
    float ao;
};

Surface k_surface(vec3 albedo, float metal, float rough) {
    Surface s;
    s.albedo = albedo;
    s.metal = metal;
    s.rough = rough;
    s.aniso = 0.0;
    s.coat = 0.0;
    s.emissive = vec3(0.0);
    s.ao = 1.0;
    return s;
}

// Each knife's blade, from where it leaves the guard to its tip, in pixels along the knife; and a hole
// through it (centre and radii, in pixels; zero radius for none).
const vec2 BLADE_RANGE[24] = vec2[24](
    vec2(-0.5, 5.6), vec2(1.3, 11.8), vec2(1.2, 11.0), vec2(0.2, 9.2),
    vec2(1.2, 9.6), vec2(0.2, 5.6), vec2(0.0, 9.2), vec2(1.0, 11.6),
    vec2(1.3, 13.7), vec2(-1.0, 4.0), vec2(0.6, 9.2), vec2(1.0, 9.4),
    vec2(0.0, 1.0), vec2(0.0, 1.0), vec2(0.0, 1.0), vec2(0.0, 1.0),
    vec2(0.0, 1.0), vec2(0.0, 1.0), vec2(0.0, 1.0), vec2(0.0, 1.0),
    vec2(0.0, 1.0), vec2(0.0, 1.0), vec2(0.0, 1.0), vec2(0.0, 1.0)
);
const vec4 HOLE[24] = vec4[24](
    vec4(-0.5, 2.61, 0.36, 0.36), vec4(0.0), vec4(0.0), vec4(0.0),
    vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0),
    vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0),
    vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0),
    vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0),
    vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0)
);

float k_bladeT(int knife, vec2 p) {
    vec2 range = BLADE_RANGE[clamp(knife, 0, 23)];
    return clamp((p.y - range.x) / max(range.y - range.x, 0.01), 0.0, 1.0);
}

vec3 k_hsv(float h, float s, float v) {
    vec3 k = clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return v * mix(vec3(1.0), k, s);
}

// Engraved filigree: a lattice of fine lines with a scroll at its heart, strongest near the guard and
// fading up the blade. Returns how much a point is on a line (0..1).
float k_filigree(vec2 p, float t) {
    vec2 q = vec2(abs(p.x), p.y);
    float lattice = min(abs(fract((q.x + q.y) * 2.4) - 0.5), abs(fract((q.x - q.y) * 2.4) - 0.5));
    float lines = (1.0 - smoothstep(0.04, 0.09, lattice)) * (1.0 - smoothstep(0.25, 0.55, q.x));
    vec2 c = vec2(q.x, fract(q.y * 0.6) - 0.5);
    float scroll = abs(sin(atan(c.x + 0.001, c.y) * 3.0 + length(c) * 9.0));
    float curls = (1.0 - smoothstep(0.08, 0.2, scroll)) * (1.0 - smoothstep(0.35, 0.75, q.x));
    float fade = 1.0 - smoothstep(0.08, 0.26, t);
    return clamp(max(lines * 0.6, curls), 0.0, 1.0) * fade;
}

// The crystal lattice: diamond facets, each a slightly different dark violet, edged by glowing lines.
// Returns {facet shade, line} in x and y.
vec2 k_crystal(vec2 p) {
    vec2 d = vec2(p.x + p.y * 0.55, p.x - p.y * 0.55) * 1.35;
    vec2 cell = floor(d);
    vec2 f = fract(d) - 0.5;
    float line = 1.0 - smoothstep(0.38, 0.47, max(abs(f.x), abs(f.y)));
    float shade = k_hash12(cell) * 0.6 + 0.4 * (f.x + f.y + 1.0) * 0.5;
    return vec2(shade, 1.0 - line);
}

// The finish on a blade (or a finished guard, ring or handle inlay). p is the point on the knife in pixels,
// t how far along the blade (0 at the guard), edge how near the outline. glowColor is the edge glow's colour.
Surface k_finish(int finish, vec2 p, float t, float edge, vec3 glowColor) {
    Surface s = k_surface(vec3(0.16, 0.16, 0.19), 1.0, 0.3);
    float fil = k_filigree(p, t);
    if (finish == F_CRYSTAL) {
        vec2 c = k_crystal(p);
        s.albedo = mix(vec3(0.07, 0.03, 0.14), vec3(0.24, 0.11, 0.42), c.x);
        s.metal = 0.75;
        s.rough = 0.16;
        s.coat = 0.8;
        s.emissive = glowColor * c.y * 0.45 * (0.6 + 0.4 * c.x);
    } else if (finish == F_MIDNIGHT) {
        s.albedo = mix(vec3(0.07, 0.09, 0.2), vec3(0.16, 0.2, 0.42), smoothstep(0.0, 1.0, t * 0.8 + edge * 0.3));
        s.metal = 0.95;
        s.rough = 0.12;
        s.coat = 0.7;
        s.albedo = mix(s.albedo, vec3(0.78, 0.82, 0.92), fil * 0.85);
        s.emissive = vec3(0.45, 0.55, 0.9) * fil * 0.18;
    } else if (finish == F_NEBULA) {
        float n = k_swirl(p * 0.2, 3.0);
        float m = k_fbm3(p * 0.3 + 6.0);
        vec3 col = mix(vec3(0.12, 0.04, 0.3), vec3(0.55, 0.18, 0.92), smoothstep(0.25, 0.6, n));
        col = mix(col, vec3(0.2, 0.75, 0.95), smoothstep(0.62, 0.8, m) * 0.7);
        col = mix(col, vec3(0.95, 0.42, 0.85), smoothstep(0.66, 0.85, n) * 0.6);
        s.albedo = col;
        s.metal = 0.85;
        s.rough = 0.1;
        s.coat = 1.0;
        s.emissive = vec3(1.0) * step(0.992, k_hash12(floor(p * 7.0))) * 0.9 + col * 0.06;
    } else if (finish == F_AURORA) {
        float f = t * 0.9 + (edge - 0.5) * 0.2 + (k_fbm3(p * 0.4) - 0.5) * 0.12;
        s.albedo = mix(vec3(0.1, 0.62, 0.82), vec3(0.52, 0.28, 0.98), smoothstep(0.1, 0.9, f));
        s.metal = 0.9;
        s.rough = 0.14;
        s.coat = 0.7;
    } else if (finish == F_FROST) {
        s.albedo = vec3(0.8, 0.86, 0.95);
        s.metal = 1.0;
        s.rough = 0.1;
        s.aniso = 0.5;
        s.albedo = mix(s.albedo, vec3(0.55, 0.82, 1.0), fil * 0.6);
    } else if (finish == F_ROSE) {
        s.albedo = mix(vec3(0.95, 0.55, 0.62), vec3(1.0, 0.78, 0.6), smoothstep(0.0, 1.0, t));
        s.metal = 1.0;
        s.rough = 0.16;
        s.coat = 0.4;
    } else if (finish == F_VOID) {
        s.albedo = vec3(0.025, 0.025, 0.032);
        s.metal = 0.4;
        s.rough = 0.42;
        float star = step(0.985, k_hash12(floor(p * 9.0)));
        float twinkle = k_hash12(floor(p * 9.0) + 3.0);
        s.emissive = mix(vec3(0.8, 0.85, 1.0), glowColor, twinkle) * star * 0.8;
    } else if (finish == F_CHROME) {
        s.albedo = vec3(0.92, 0.93, 0.95);
        s.metal = 1.0;
        s.rough = 0.04;
    } else {
        // Obsidian: dark brushed gunmetal with cyan filigree near the guard.
        s.albedo = vec3(0.09, 0.095, 0.12) * (0.92 + k_noise(p * vec2(0.6, 7.0)) * 0.12);
        s.metal = 0.8;
        s.rough = 0.34;
        s.aniso = 0.7;
        s.albedo = mix(s.albedo, vec3(0.2, 0.55, 0.62), fil * 0.6);
        s.emissive = vec3(0.12, 0.78, 0.9) * fil * 0.3;
    }
    return s;
}

// A non-finish knife part: rubber, polymer, wood, cord, carbon or hardware, all in Eventide's dark tones.
Surface k_knifePart(int material, vec2 p, int finish, vec3 glowColor) {
    if (material == M_RUBBER) {
        float grip = k_noise(p * 8.0) * 0.08;
        return k_surface(vec3(0.05, 0.05, 0.06) * (1.0 + grip), 0.0, 0.62);
    }
    if (material == M_POLYMER) {
        Surface s = k_surface(vec3(0.055, 0.05, 0.07), 0.0, 0.34);
        float hex = min(abs(fract(p.x * 1.4 + p.y * 0.8) - 0.5), abs(fract(p.y * 1.6) - 0.5));
        s.albedo *= 0.85 + smoothstep(0.0, 0.12, hex) * 0.3;
        s.coat = 0.55;
        return s;
    }
    if (material == M_WOOD) {
        float grain = sin((p.y * 1.8 + k_fbm3(p * vec2(0.6, 0.15)) * 6.0) * 3.0) * 0.5 + 0.5;
        Surface s = k_surface(mix(vec3(0.16, 0.09, 0.06), vec3(0.28, 0.16, 0.1), grain), 0.0, 0.45);
        s.coat = 0.4;
        return s;
    }
    if (material == M_CORD) {
        float twist = abs(fract(p.x * 1.6 + p.y * 1.2) - 0.5);
        vec3 cord = mix(vec3(0.06, 0.05, 0.09), glowColor * 0.25, 0.25);
        return k_surface(cord * (0.6 + twist * 1.0), 0.0, 0.85);
    }
    if (material == M_CARBON) {
        vec2 w = floor(p * 3.0);
        float weave = mod(w.x + w.y, 2.0);
        Surface s = k_surface(mix(vec3(0.04), vec3(0.11), weave), 0.2, 0.25);
        s.coat = 0.8;
        return s;
    }
    Surface s = k_surface(vec3(0.22, 0.22, 0.26), 1.0, 0.3);
    return s;
}
