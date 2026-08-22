#version 150

// Procedural smoke on top of a torn mask.
//
// One mask shared by every puff in a cloud is what makes impact smoke look like a row of stamped
// copies. This warps the mask lookup by a curl map and modulates its density with a second octave,
// and it offsets the noise per particle using the tone the simulation randomised into the vertex
// colour — so no two puffs sample the same part of the noise even though they share one texture.
//
// Sampler0 is the puff mask, Sampler1 the curl map.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float variation = fract(vertexColor.r * 97.0 + vertexColor.b * 31.0);
    vec2 noiseCoord = texCoord * 2.3 + vec2(variation, fract(variation * 7.3));
    vec3 curl = texture(Sampler1, noiseCoord).rgb;

    vec2 warped = clamp(texCoord + (curl.rg - 0.5) * 0.11, 0.0, 1.0);
    float mask = texture(Sampler0, warped).a * (0.55 + curl.b * 0.8);

    // Smoothstep density curve: denser cores, cleaner edges than a plain multiply.
    float shaped = clamp(mask, 0.0, 1.0);
    shaped = shaped * shaped * (3.0 - 2.0 * shaped);
    float alpha = vertexColor.a * shaped;
    if (alpha <= 0.003) discard;
    fragColor = vec4(vertexColor.rgb, alpha);
}
