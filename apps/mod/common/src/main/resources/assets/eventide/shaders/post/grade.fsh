#version 330

// Colour Grading: exposure, saturation and contrast, a tint over the whole picture with a colour lifted into
// its shadows, and a soft vignette.

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Tint: the tint (rgb) and saturation (a). Lift: the colour lifted into the shadows (rgb) and contrast (a).
// Look: exposure (x) and vignette (y).
layout(std140) uniform GradeConfig {
    vec4 Tint;
    vec4 Lift;
    vec4 Look;
};

out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb * Look.x;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luma), c, Tint.a);
    c = (c - 0.5) * Lift.a + 0.5;
    c = c * Tint.rgb + Lift.rgb * (1.0 - clamp(luma * 1.8, 0.0, 1.0));
    vec2 d = texCoord - 0.5;
    c *= 1.0 - Look.y * smoothstep(0.12, 0.62, dot(d, d) * 2.0);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
