#version 150

// Flat colour, straight from the vertex. The fragments are solid debris, so there is no mask to apply
// and nothing to shape; the only thing worth doing is dropping the ones that have faded out.

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    if (vertexColor.a <= 0.002) discard;
    fragColor = vertexColor;
}
