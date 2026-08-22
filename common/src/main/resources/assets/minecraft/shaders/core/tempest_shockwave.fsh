#version 150

// Procedural pressure ring.
//
// The CPU only grows a square quad; everything that makes this read as a shock front is computed
// here: a sharp gaussian leading edge, a long trailing wake behind it, and a curl-noise warp of the
// sampling position so the front is never a mathematically perfect circle. Computing the ring rather
// than scrolling a baked texture means the edge stays crisp at any radius.
//
// Sampler0 is the baked ring mask: it carries pre-generated angular noise that modulates the wake,
// and binding it also keeps Minecraft's stock position_tex_color a correct fallback if this program
// fails to load. Sampler1 is the curl map: rg is an offset vector, b is density.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 centred = texCoord * 2.0 - 1.0;
    vec3 curl = texture(Sampler1, texCoord * 1.7).rgb;

    float radius = length(centred + (curl.rg - 0.5) * 0.18);
    if (radius > 1.0) discard;

    // A second octave of irregularity, pre-baked at a different frequency to the curl map.
    float baked = texture(Sampler0, clamp(texCoord + (curl.rg - 0.5) * 0.06, 0.0, 1.0)).a;

    float front = exp(-pow((radius - 0.82) * 7.0, 2.0));
    float wake = smoothstep(0.18, 0.8, radius) * 0.3 * (0.55 + baked * 0.9);
    float density = 0.7 + curl.b * 0.6;
    float energy = (front * 1.5 + wake) * density * (1.0 - smoothstep(0.88, 1.0, radius));

    float alpha = vertexColor.a * energy;
    if (alpha <= 0.003) discard;
    // The leading edge is hotter than the wake dragging behind it.
    fragColor = vec4(vertexColor.rgb * (1.0 + front * 0.85), alpha);
}
