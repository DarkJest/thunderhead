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
//
// Output is the source term of a premultiplied "over" blend, so the framebuffer ends up holding
//   scene x (1 - coverage) + colour
// which is the same image the layers would have produced blended straight into the scene.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D SceneDepth;
uniform mat4 LightProjection;
uniform mat4 LightInverseProjection;
uniform vec4 LightControl;
uniform vec4 ChannelLight[4];

// centre.xy in screen space, radius, strength; strength of zero means no scene read at all
uniform vec4 TempestRipple;
// window aspect, wavefront phase
uniform vec4 TempestRippleShape;

in vec2 texCoord;

out vec4 fragColor;

vec3 viewPosition(vec2 uv, float depth) {
    vec4 point = LightInverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return point.xyz / point.w;
}

// Limited to visible depth. Off-screen occluders and pack-specific transparent/cloud volumes
// cannot be inferred from this buffer; this is explicitly a surface-lighting approximation.
float visibility(vec3 surface, vec3 source) {
    for (int i = 1; i <= 8; ++i) {
        vec3 samplePoint = mix(surface, source, float(i) / 9.0);
        vec4 clip = LightProjection * vec4(samplePoint, 1.0);
        if (clip.w <= 0.0) break;
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) break;
        float depth = texture(SceneDepth, uv).r;
        if (depth < 0.99999) {
            vec3 obstacle = viewPosition(uv, depth);
            if (samplePoint.z < obstacle.z - max(0.35, abs(samplePoint.z) * 0.003)) return 0.0;
        }
    }
    return 1.0;
}

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

    vec3 illumination = vec3(0.0);
    if (LightControl.x > 0.0) {
        float depth = texture(SceneDepth, texCoord).r;
        vec3 surface = viewPosition(texCoord, depth);
        // Evaluate derivatives before the depth branch so neighboring fragments stay coherent.
        vec3 normal = cross(dFdx(surface), dFdy(surface));
        float normalLength = length(normal);
        if (depth > 0.0 && depth < 0.99999 && normalLength > 0.000001) {
            normal /= normalLength;
            if (dot(normal, -surface) < 0.0) normal = -normal;
            float amount = 0.0;
            for (int i = 0; i < 4; ++i) {
                if (float(i) >= LightControl.x) break;
                vec3 delta = ChannelLight[i].xyz - surface;
                float distance = length(delta);
                float radius = LightControl.y;
                if (radius <= 0.0 || distance >= radius || distance < 0.001) continue;
                float diffuse = max(dot(normal, delta / distance), 0.0);
                float falloff = pow(max(0.0, 1.0 - distance / radius), 2.0)
                    / (1.0 + distance * distance / max(1.0, radius * radius * 0.0625));
                if (diffuse > 0.01) amount += ChannelLight[i].w * diffuse * falloff
                    * visibility(surface + normal * 0.08, ChannelLight[i].xyz);
            }
            vec3 scene = texture(Sampler0, texCoord).rgb;
            // Scene is already tone mapped: never claim this estimates material albedo or HDR radiance.
            illumination = (scene + vec3(0.025, 0.028, 0.035)) * min(3.0, amount * 12.0);
        }
    }
    fragColor = vec4(effect.rgb + refracted + illumination * (1.0 - effect.a), effect.a);
}
