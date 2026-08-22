#version 150

// Fullscreen quad, emitted in clip space.
//
// No ModelViewMat and no ProjMat on purpose: the pass then needs nothing out of the global matrix
// state, so it cannot be disturbed by whatever the frame left there and has nothing to restore.

in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
}
