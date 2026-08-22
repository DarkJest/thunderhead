#version 150

// Analytic cross-section of a lightning channel.
//
// The CPU only emits flat quads; the shape of the glow is computed here. That keeps the vertex count
// low while giving each layer a soft, energetic falloff instead of a visible rectangle edge. Because
// the profile depends only on the across-width coordinate, consecutive segments of one channel join
// seamlessly.

in vec2 channelCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float across = channelCoord.y * 2.0 - 1.0;
    float edge = 1.0 - across * across;
    if (edge <= 0.0) discard;

    // Squared parabola: a tight hot centre with a long, soft shoulder.
    float profile = edge * edge;
    float alpha = vertexColor.a * profile;
    if (alpha <= 0.002) discard;

    // Overdrive the core so additive blending reads as emissive even without post bloom.
    vec3 rgb = vertexColor.rgb * (1.0 + profile * 0.55);
    fragColor = vec4(rgb, alpha);
}
