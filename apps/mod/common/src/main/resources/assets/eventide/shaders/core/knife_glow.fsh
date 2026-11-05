#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec3 viewPos;
in vec3 viewNormal;
in vec4 vertexColor;

out vec4 fragColor;

// Bright where the shell is seen edge-on (the silhouette), fading to nothing where it faces you, added on
// top of what's behind it.
void main() {
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
#ifdef OUTLINE
    // An outline: only the far side of the bigger shell (where its outward normal points away from you), so all
    // that shows is the rim past the edges. The normal decides it, not the winding, which knife meshes don't keep.
    if (dot(n, v) > 0.0) {
        discard;
    }
    float rim = 1.0;
#else
    float rim = pow(1.0 - abs(dot(n, v)), 2.2);
#endif
    float a = vertexColor.a * rim * ColorModulator.a;
    if (a < 0.003) {
        discard;
    }
#ifdef LINE
    fragColor = vec4(vertexColor.rgb, a);
#else
    fragColor = vec4(vertexColor.rgb * a * 1.6, a);
#endif
}
