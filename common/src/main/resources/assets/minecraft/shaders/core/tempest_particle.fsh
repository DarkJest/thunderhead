#version 150

// Grayscale masks carry the shape in their alpha channel, the vertex colour carries the tint.
// Keeping colour out of the textures is what lets one 128x128 mask serve sparks, dust, smoke,
// steam, ash and ground decals, and it means Minecraft's stock position_tex_color program is a
// correct fallback if this shader ever fails to load.

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float mask = texture(Sampler0, texCoord).a;
    // Smoothstep density curve: denser cores and cleaner edges than a plain multiply.
    float shaped = mask * mask * (3.0 - 2.0 * mask);
    float alpha = vertexColor.a * shaped;
    if (alpha <= 0.003) discard;
    fragColor = vec4(vertexColor.rgb * (1.0 + shaped * 0.25), alpha);
}
