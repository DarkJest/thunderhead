#version 150

// Bright pass, and the first halving of resolution.
//
// The effect attachment is half-float and the world pass accumulates into it without clamping, so a
// lightning core genuinely carries values above one. That headroom is the whole point: this keeps
// what is over the threshold and throws the rest away, and the blur that follows turns it into the
// bleed a camera would have produced. An eight-bit target would have thrown the headroom away long
// before this pass could see it.
//
//   Sampler0  the effect attachment
//   TempestBloom.x  threshold
//   TempestBloom.y  soft knee width
//   TempestBloom.zw texel size of the source

uniform sampler2D Sampler0;
uniform vec4 TempestBloom;

in vec2 texCoord;

out vec4 fragColor;

vec3 sampleAt(vec2 uv) {
    vec4 texel = texture(Sampler0, uv);
    // Additive layers wrote colour and no coverage, translucent ones premultiplied colour; either
    // way rgb is already the light this pixel emits.
    return max(texel.rgb, vec3(0.0));
}

void main() {
    vec2 texel = TempestBloom.zw;
    // Four taps on the source texel centres: a box downsample that costs one bilinear fetch each.
    vec3 sum = sampleAt(texCoord + texel * vec2(-0.5, -0.5))
             + sampleAt(texCoord + texel * vec2( 0.5, -0.5))
             + sampleAt(texCoord + texel * vec2(-0.5,  0.5))
             + sampleAt(texCoord + texel * vec2( 0.5,  0.5));
    vec3 color = sum * 0.25;

    float threshold = TempestBloom.x;
    float knee = max(1.0e-4, TempestBloom.y);
    float brightness = max(color.r, max(color.g, color.b));
    // Soft knee: a hard cut makes the bloom pop in and out as a bolt decays past the threshold.
    float contribution = clamp((brightness - threshold + knee) / (2.0 * knee), 0.0, 1.0);
    contribution = contribution * contribution * max(brightness - threshold, 0.0);
    float scale = brightness > 1.0e-5 ? contribution / brightness : 0.0;

    fragColor = vec4(color * scale, 1.0);
}
