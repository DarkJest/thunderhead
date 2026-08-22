#version 150

// Tempest FX lightning channel.
// UV0.x runs along the channel, UV0.y runs across the ribbon width; no texture is sampled.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 channelCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    channelCoord = UV0;
    vertexColor = Color;
}
