#version 150

// (v0.7.36) O borrão de movimento da cena de morte, como no "Eating a Zebra" (LM 16/11: "o borrão do blur é só na
// carne", e o exemplo dele mergulhando na cabeça da vítima aos 7,85 s). Cada quadro mistura um pouco do anterior, mas
// o anterior é lido no lugar onde AQUELE ponto do mundo estava no quadro passado (a profundidade e as duas câmeras):
// o que está parado no mundo (chão, parede, céu) cai no mesmo ponto e fica nítido mesmo com a câmera tremendo ou
// virando; só o que se mexe de verdade (ele, a carne, o sangue voando) deixa rastro.
// Os valores vêm do CinematicCamera a cada quadro; sem eles, BlurMix fica 0 e a tela sai igual.

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D PrevSampler;

uniform mat4 InvViewProj;   // tela de agora -> mundo, em volta da câmera de agora
uniform mat4 PrevViewProj;  // mundo, em volta da câmera de antes -> tela do quadro anterior
uniform vec3 CamDelta;      // câmera de agora - câmera de antes (blocos)
uniform float BlurMix;      // quanto do quadro anterior entra (0 = nada)

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec4 cur = texture(DiffuseSampler, texCoord);
    float k = BlurMix;
    vec2 uv = texCoord;
    if (k > 0.0) {
        float depth = texture(DiffuseDepthSampler, texCoord).r;
        vec4 world = InvViewProj * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        if (abs(world.w) > 0.000001) {
            vec4 clip = PrevViewProj * vec4(world.xyz / world.w + CamDelta, 1.0);
            if (clip.w > 0.000001) uv = clip.xy / clip.w * 0.5 + 0.5;
            else k = 0.0;
        } else {
            k = 0.0;
        }
        // estava fora da tela no quadro passado: não tem o que misturar
        if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) k = 0.0;
    }
    vec3 prev = texture(PrevSampler, uv).rgb;
    fragColor = vec4(mix(cur.rgb, prev, k), 1.0);
}
