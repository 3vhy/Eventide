#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <eventide:knife_lib.glsl>
#moj_import <eventide:knife_surface.glsl>

in vec3 worldPos;
in vec3 worldNormal;
in vec3 viewPos;
in vec3 viewNormal;
in vec2 pattern;
in float edge;
flat in ivec4 info;
flat in vec4 frameInfo;
in vec4 lightMapColor;
in vec3 orthoEye;
flat in int orthographic;

out vec4 fragColor;

void main() {
    int material = info.x;
    int finish = info.y;
    float saturation = float(info.z) / 3.0;
    int knife = info.w;
    float glint = frameInfo.x;
    float glow = frameInfo.y;
    float boost = frameInfo.z;
    vec3 glowColor = k_hsv(frameInfo.w, saturation, 1.0);
    vec2 p = pattern;

    vec3 nv3 = normalize(viewNormal);
    vec3 nw = normalize(worldNormal);
    // 3D skins (knife 62) are lit like a photo studio, in neutral greys, so their own colours read true;
    // drawn flat (the inventory's preview), they're seen straight on.
    bool studio = knife == 62;
    vec3 v = normalize(-viewPos);
    vec3 vw = normalize(-worldPos);
    if (studio && orthographic == 1) {
        v = vec3(0.0, 0.0, 1.0);
        vw = normalize(orthoEye);
    }
    bool blade = material == M_BLADE || material == M_EDGE;
    float t = blade ? k_bladeT(knife, p) : 0.0;
    float len = k_bladeLength(knife);
    float nv = clamp(dot(nv3, v), 0.0, 1.0);

    Surface s;
    bool finished = blade || material == M_GUARD || material == M_RING || material == M_HANDLE_PAINT;
    if (material == M_ENERGY) {
        s = k_surface(vec3(0.0), 0.0, 1.0);
        s.emissive = mix(glowColor * 1.2, vec3(1.0), 0.45 + 0.55 * pow(nv, 1.5));
    } else if (material == M_GEM_RIM) {
        s = k_surface(vec3(0.0), 0.0, 1.0);
        s.emissive = mix(glowColor, vec3(1.0), 0.18) * (1.0 + 0.35 * pow(1.0 - nv, 2.0));
    } else if (material == M_GEM) {
        vec2 c = k_crystal(p * 2.2);
        s = k_surface(mix(vec3(0.05, 0.02, 0.12), vec3(0.3, 0.13, 0.58), c.x), 0.45, 0.08);
        s.coat = 1.0;
        s.emissive = glowColor * (0.14 + 0.2 * c.x + 0.55 * pow(1.0 - nv, 2.0));
    } else if (finished) {
        s = k_finish(finish, p, t, edge, glowColor, blade, len, k_engravePlace(knife));
        if (material == M_EDGE) {
            s.rough *= 0.5;
            s.albedo = mix(s.albedo, vec3(0.85, 0.86, 0.9), 0.14);
            s.metal = max(s.metal, 0.8);
        }
        if (material == M_GUARD || material == M_RING || material == M_HANDLE_PAINT) {
            s.aniso = 0.0;
            s.emissive *= 0.4;
        }
        if (material == M_HANDLE_PAINT) s.emissive += glowColor * glow * 0.35;
    } else {
        s = k_knifePart(material, p, finish, glowColor);
    }

    vec3 dpdx = dFdx(viewPos);
    vec3 dpdy = dFdy(viewPos);
    vec2 dtdx = dFdx(p);
    vec2 dtdy = dFdy(p);
    float det = dtdx.y * dtdy.x - dtdy.y * dtdx.x;
    vec3 tangent = (dpdx * dtdy.x - dpdy * dtdx.x) / (abs(det) > 1e-9 ? det : 1e-9);
    tangent -= nv3 * dot(tangent, nv3);
    tangent = length(tangent) > 1e-6 ? normalize(tangent) : vec3(0.0, 1.0, 0.0);

    vec3 key = normalize(vec3(-0.5, 0.72, 0.48));
    vec3 back = normalize(vec3(0.75, 0.2, -0.62));
    float nl = max(dot(nv3, key), 0.0);
    float nb = max(dot(nv3, back), 0.0);
    vec3 h = normalize(key + v);
    float nh = max(dot(nv3, h), 0.0);
    float d = k_ggx(nh, s.rough);
    if (s.aniso > 0.0) {
        float th = dot(tangent, h);
        float streak = pow(sqrt(max(1.0 - th * th, 0.0)), mix(18.0, 150.0, 1.0 - s.rough)) * mix(1.2, 3.0, 1.0 - s.rough);
        d = mix(d, streak, s.aniso);
    }
    vec3 f0 = mix(vec3(0.04), s.albedo, s.metal);
    vec3 fresnel = f0 + (max(vec3(1.0 - s.rough), f0) - f0) * pow(1.0 - nv, 5.0);
    vec3 r = reflect(-vw, nw);
    vec3 env = studio ? k_studio(r, s.rough) : mix(k_environment(r, s.rough), vec3(0.22, 0.2, 0.3), s.rough * 0.55);
    vec3 backColor = studio ? vec3(0.5, 0.52, 0.55) : vec3(0.62, 0.42, 0.95);

    vec3 lightIn = vec3(0.26) + vec3(0.95, 0.95, 1.0) * nl * 0.8 + backColor * nb * 0.4 + vec3(0.2, 0.22, 0.3) * max(nw.y, 0.0) * 0.3;
    vec3 diffuse = s.albedo * (1.0 - s.metal) * lightIn;
    vec3 specular = fresnel * env + fresnel * d * nl * vec3(1.0, 0.98, 1.0) * 0.7;
    vec3 rimTint = studio ? vec3(0.62) : mix(vec3(0.7, 0.75, 0.95), glowColor, 0.5);
    vec3 rim = rimTint * pow(1.0 - nv, 4.0) * (0.25 + nb * 0.6) * (1.0 - s.rough * 0.6);
    float coatFresnel = 0.04 + 0.96 * pow(1.0 - nv, 5.0);
    vec3 coat = s.coat * (coatFresnel * (studio ? k_studio(r, 0.03) : k_environment(r, 0.03)) * 0.7 + k_ggx(nh, 0.05) * nl * 0.08);
    vec3 color = (diffuse + specular) * s.ao + rim + coat;

    // The luminous edge: a thin line of the glow colour right along the blade's outline, brightest seen edge-on.
    if (blade && glow > 0.0) {
        float line = smoothstep(0.72, 1.0, clamp(edge, 0.0, 1.0));
        line *= line;
        float seen = 0.55 + 0.45 * pow(1.0 - nv, 1.5);
        color += glowColor * glow * line * seen * 1.5;
        color += glowColor * glow * pow(1.0 - nv, 4.0) * 0.04;
    }

    if (glint > 0.0 && (blade || material == M_RING)) {
        float band = exp(-pow((t - glint) / 0.08, 2.0)) * (1.0 - s.rough * 0.7);
        color += mix(vec3(1.6), glowColor * 2.0, 0.4) * band;
    }

    vec3 light = mix(lightMapColor.rgb, vec3(1.0), boost);
    if (material == M_ENERGY || material == M_GEM_RIM) light = vec3(1.0);
    color = color * light + s.emissive;
    fragColor = vec4(k_softClip(color), 1.0);
}
