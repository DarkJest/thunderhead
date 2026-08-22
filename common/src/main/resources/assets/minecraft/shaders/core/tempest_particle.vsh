#version 150

// Tempest FX particle and decal geometry. Position/UV/Color only: all shaping comes from the
// grayscale mask bound to Sampler0 and from the per-vertex colour the simulation computed.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
}
