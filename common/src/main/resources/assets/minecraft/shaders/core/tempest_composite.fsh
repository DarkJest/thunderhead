#version 150

// Applies the effect to a finished scene image.
//
// The pass knows three things and nothing else: the colour already in the framebuffer, the mod's own
// effect attachment, and its own uniforms. It never samples a depth buffer and never guesses at one:
// visibility was resolved by a real depth test while the effect was drawn, and survives here as the
// coverage channel.
//
//   Sampler1  effect: rgb = premultiplied emissive colour, a = coverage after the depth test
//   Sampler0  a copy of the scene, read only while a shockwave is refracting it
//   Sampler2  the glow: bloom and light shafts, produced from Sampler1 at quarter resolution
//
// The glow is added as light and contributes no coverage, exactly like an additive layer in the
// world pass would have. It cannot darken the scene and cannot hide anything behind it.
//
// Output is the source term of a premultiplied "over" blend, so the framebuffer ends up holding
//   scene x (1 - coverage) + colour
// which is the same image the layers would have produced blended straight into the scene.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

// glow strength; zero means the chain did not run and Sampler2 must not be read
uniform vec4 TempestGlow;
// centre.xy in screen space, radius, strength; strength of zero means no scene read at all
uniform vec4 TempestRipple;
// window aspect, wavefront phase
uniform vec4 TempestRippleShape;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 effect = texture(Sampler1, texCoord);
    vec3 refracted = vec3(0.0);

    float strength = TempestRipple.w;
    float radius = TempestRipple.z;
    if (strength > 0.0 && radius > 0.0) {
        vec2 delta = (texCoord - TempestRipple.xy) * vec2(TempestRippleShape.x, 1.0);
        float dist = length(delta);
        float ring = dist / radius;
        float phase = TempestRippleShape.y;

        // Gaussian band centred on the wavefront, so the rest of the screen is untouched.
        float band = exp(-pow((ring - 1.0) * 3.4, 2.0));
        // Secondary shimmer just inside the front, where the hottest air is.
        float shimmer = exp(-pow(ring * 2.6, 2.0)) * 0.6;
        float wave = sin((ring - 1.0) * 22.0 - phase * 5.0);

        float amount = (band * wave + shimmer * sin(phase * 9.0 + ring * 30.0)) * strength * 0.014;
        vec2 direction = dist > 1.0e-5 ? delta / dist : vec2(0.0);
        vec2 warped = clamp(texCoord + direction * amount, vec2(0.0), vec2(1.0));

        // Only the difference is emitted: the untouched scene is already in the destination, and the
        // blend adds to it. That keeps this a single pass with no copy back.
        refracted = (texture(Sampler0, warped).rgb - texture(Sampler0, texCoord).rgb) * (1.0 - effect.a);
    }

    vec3 glow = TempestGlow.x > 0.0 ? texture(Sampler2, texCoord).rgb * TempestGlow.x : vec3(0.0);
    fragColor = vec4(effect.rgb + refracted + glow, effect.a);
}
