#version 150

// One axis of a gaussian blur, run twice.
//
// Nine taps folded into five bilinear fetches by sampling between texel centres, which is the usual
// trick and is what keeps a wide, soft bloom affordable at quarter resolution.
//
//   Sampler0  the source
//   TempestBlur.xy  step in texture coordinates: the axis, already scaled by texel size
//   TempestBlur.z   overall radius multiplier

uniform sampler2D Sampler0;
uniform vec4 TempestBlur;

in vec2 texCoord;

out vec4 fragColor;

// Offsets and weights for a 9-tap gaussian collapsed onto 5 samples.
const float OFFSETS[3] = float[](0.0, 1.3846153846, 3.2307692308);
const float WEIGHTS[3] = float[](0.2270270270, 0.3162162162, 0.0702702703);

void main() {
    vec2 stride = TempestBlur.xy * max(0.0, TempestBlur.z);
    vec3 sum = texture(Sampler0, texCoord).rgb * WEIGHTS[0];
    for (int index = 1; index < 3; index++) {
        vec2 offset = stride * OFFSETS[index];
        sum += texture(Sampler0, texCoord + offset).rgb * WEIGHTS[index];
        sum += texture(Sampler0, texCoord - offset).rgb * WEIGHTS[index];
    }
    fragColor = vec4(sum, 1.0);
}
