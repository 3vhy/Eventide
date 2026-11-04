#version 330

// Noise, the reflections and the light for Eventide's knives.

float k_hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 k_hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float k_noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(k_hash12(i), k_hash12(i + vec2(1.0, 0.0)), u.x), mix(k_hash12(i + vec2(0.0, 1.0)), k_hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float k_fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * k_noise(p);
        p = mat2(1.6, 1.2, -1.2, 1.6) * p + vec2(17.1, 9.2);
        amp *= 0.5;
    }
    return sum;
}

float k_fbm3(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 3; i++) {
        sum += amp * k_noise(p);
        p = mat2(1.6, 1.2, -1.2, 1.6) * p + vec2(17.1, 9.2);
        amp *= 0.5;
    }
    return sum / 0.875;
}

// Swirls: noise looked up through noise, the marbled flow of Doppler-style finishes.
float k_swirl(vec2 p, float strength) {
    vec2 q = vec2(k_fbm(p), k_fbm(p + vec2(5.2, 1.3)));
    vec2 r = vec2(k_fbm(p + strength * q + vec2(1.7, 9.2)), k_fbm(p + strength * q + vec2(8.3, 2.8)));
    return k_fbm(p + strength * r);
}

// Distance to the nearest of a scatter of points (x), the second nearest (y), and the nearest one's id (z).
vec3 k_cells(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float d1 = 8.0;
    float d2 = 8.0;
    float id = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            vec2 o = k_hash22(i + g);
            vec2 d = g + o - f;
            float dist = dot(d, d);
            if (dist < d1) {
                d2 = d1;
                d1 = dist;
                id = k_hash12(i + g + 7.7);
            } else if (dist < d2) {
                d2 = dist;
            }
        }
    }
    return vec3(sqrt(d1), sqrt(d2), id);
}

// The world around the knife, as its metal reflects it: a midnight sky with a violet nebula band and a
// cyan glow low on one side, bright points of starlight, and dark ground. It's fixed to the world, so the
// glints move as you look around.
vec3 k_environment(vec3 r, float rough) {
    float y = r.y;
    float blur = 0.03 + rough * 0.55;
    vec3 zenith = vec3(0.06, 0.05, 0.14);
    vec3 horizon = vec3(0.2, 0.17, 0.32);
    vec3 ground = vec3(0.05, 0.05, 0.07);
    vec3 sky = mix(horizon, zenith, smoothstep(0.02, 0.75, y));
    float angle = atan(r.z, r.x);
    float detail = 1.0 - smoothstep(0.05, 0.6, rough);
    float band = exp(-pow((y - 0.35 - 0.25 * sin(angle * 1.5)) / (0.18 + rough * 0.3), 2.0));
    float clouds = k_fbm3(vec2(angle * 2.2, y * 5.0) + 3.0);
    sky += vec3(0.42, 0.2, 0.75) * band * (0.35 + 0.4 * clouds * detail);
    sky += vec3(0.25, 0.8, 1.0) * exp(-pow((angle - 1.2) / (0.5 + rough), 2.0)) * smoothstep(0.5, 0.0, abs(y - 0.12)) * 0.3;
    vec3 key = normalize(vec3(-0.35, 0.62, 0.7));
    float keySpot = pow(max(dot(r, key), 0.0), mix(700.0, 10.0, rough));
    sky += vec3(2.2, 2.1, 2.4) * keySpot * (1.0 - rough * 0.6);
    vec3 rim = normalize(vec3(0.7, 0.3, -0.6));
    sky += vec3(0.8, 0.45, 1.3) * pow(max(dot(r, rim), 0.0), mix(400.0, 8.0, rough)) * (1.0 - rough * 0.5);
    float skyline = k_fbm3(vec2(angle * 6.0, 1.0)) * 0.1 * detail;
    vec3 under = mix(ground * 1.6, ground * 0.55, smoothstep(0.0, -0.7, y));
    float t = smoothstep(-blur + skyline, blur + skyline, y);
    vec3 color = mix(under, sky, t);
    color += vec3(0.7, 0.55, 1.1) * exp(-pow((y - skyline) / (0.025 + rough * 0.25), 2.0)) * (1.0 - rough * 0.7) * 0.6;
    return color;
}

float k_ggx(float nh, float rough) {
    float a = max(rough * rough, 0.0025);
    float a2 = a * a;
    float d = nh * nh * (a2 - 1.0) + 1.0;
    return a2 / (3.14159 * d * d);
}

vec3 k_softClip(vec3 c) {
    vec3 over = max(c - 0.82, 0.0);
    return min(c, vec3(0.82)) + over / (1.0 + over * 2.2);
}
