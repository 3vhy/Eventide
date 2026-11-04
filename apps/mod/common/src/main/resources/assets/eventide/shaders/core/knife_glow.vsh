#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The soft shell of light round a knife or arm: already pushed out along its normals, in the glow's colour
// with its strength in alpha.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 viewPos;
out vec3 viewNormal;
out vec4 vertexColor;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    viewPos = view.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    vertexColor = Color;
}
