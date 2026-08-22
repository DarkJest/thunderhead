#version 150

// Light shafts, by radially blurring the mod's own bloom outward from the channel.
//
// The classic sun-shaft trick, applied to the effect rather than to the scene. That distinction is
// what makes it correct here: the effect attachment already had a real depth test applied to it while
// it was drawn, so terrain and leaves have carved the channel's silhouette out of it. Smearing that
// silhouette away from the bolt reproduces exactly the shafts light would throw between the things
// blocking it - and it never reads the scene, so it needs no depth buffer of its own and cannot be
// wrong about a pipeline it was not written for.
//
//   Sampler0  the blurred bloom
//   TempestShaft.xy  the channel's position on screen
//   TempestShaft.z   strength
//   TempestShaft.w   window aspect, so the smear is round rather than stretched

uniform sampler2D Sampler0;
uniform vec4 TempestShaft;

in vec2 texCoord;

out vec4 fragColor;

const int STEPS = 12;
// How far along the ray to the centre one full sweep travels.
const float REACH = 0.55;
// Per-step falloff. Shafts have to thin out or they read as a paper fan.
const float DECAY = 0.86;

void main() {
    vec3 source = texture(Sampler0, texCoord).rgb;
    float strength = TempestShaft.z;
    if (strength <= 0.0) {
        fragColor = vec4(source, 1.0);
        return;
    }

    vec2 aspect = vec2(TempestShaft.w, 1.0);
    vec2 delta = (texCoord - TempestShaft.xy);
    // Step toward the source of the light, gathering what is between here and there.
    vec2 stride = delta * (REACH / float(STEPS));

    vec2 cursor = texCoord;
    float weight = 1.0;
    float total = 0.0;
    vec3 shafts = vec3(0.0);
    for (int index = 0; index < STEPS; index++) {
        cursor -= stride;
        shafts += texture(Sampler0, cursor).rgb * weight;
        total += weight;
        weight *= DECAY;
    }
    shafts /= max(total, 1.0e-4);

    // Fade the smear out far from the channel, where a shaft would have dispersed anyway.
    float radial = length(delta * aspect);
    float reachFade = exp(-radial * 1.6);
    fragColor = vec4(source + shafts * strength * reachFade, 1.0);
}
