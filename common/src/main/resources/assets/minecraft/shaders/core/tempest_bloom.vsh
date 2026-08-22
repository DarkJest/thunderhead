#version 150

// Shared fullscreen quad for the three post passes, emitted in clip space.
//
// No ModelViewMat and no ProjMat, for the same reason the composite declares none: the pass then
// needs nothing out of the global matrix state and has nothing to restore.

in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
}
