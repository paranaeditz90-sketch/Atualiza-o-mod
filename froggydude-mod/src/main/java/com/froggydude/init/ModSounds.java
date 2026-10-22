package com.froggydude.init;

import com.froggydude.entity.voice.VoiceLine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * Sons do Froggy. Os arquivos .ogg ficam em assets/froggydude/sounds/ e a
 * lista de quais arquivos cada som sorteia está em assets/froggydude/sounds.json.
 */
public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ModEntityTypes.MOD_ID);

    /** Parado/andando, à toa: o resmungo e a risada (o ribbit é de raiva, ver RIBBIT). */
    public static final RegistryObject<SoundEvent> AMBIENT = register("froggydude.ambient");
    /**
     * Ribbit = RAIVA (LM: "Ribbits = Raiva, na lore original"): apanhando, a presa
     * fugindo ou fora do alcance, na fase 2.
     */
    public static final RegistryObject<SoundEvent> RIBBIT = register("froggydude.ribbit");
    /** Quando ele te acha ("I'm hungry", "you can't run, you can't hide"). */
    public static final RegistryObject<SoundEvent> HUNT = register("froggydude.hunt");
    /** Farejando o rastro (FroggyTrackGoal). */
    public static final RegistryObject<SoundEvent> SNIFF = register("froggydude.sniff");
    /** Apanhou: grunhido curto. */
    public static final RegistryObject<SoundEvent> HURT = register("froggydude.hurt");
    /** De vez em quando, no lugar do grunhido: "I love pain". */
    public static final RegistryObject<SoundEvent> LOVE_PAIN = register("froggydude.love_pain");
    public static final RegistryObject<SoundEvent> DEATH = register("froggydude.death");
    /** O grito da fase 2. */
    public static final RegistryObject<SoundEvent> SCREAM = register("froggydude.scream");
    public static final RegistryObject<SoundEvent> BITE = register("froggydude.bite");
    /** Comendo / "you taste so good". */
    public static final RegistryObject<SoundEvent> EAT = register("froggydude.eat");
    /** O estalo da língua saindo. */
    public static final RegistryObject<SoundEvent> TONGUE = register("froggydude.tongue");
    /** Provou com a língua. */
    public static final RegistryObject<SoundEvent> TASTE = register("froggydude.taste");
    /** Esforço do salto de sapo. */
    public static final RegistryObject<SoundEvent> LEAP = register("froggydude.leap");
    /** Montado em cima da vítima. */
    public static final RegistryObject<SoundEvent> PIN = register("froggydude.pin");
    /** Fase 2: gemendo e reclamando de dor enquanto corre ("freaking spicy"). */
    public static final RegistryObject<SoundEvent> PAIN = register("froggydude.pain");
    /** Cada soco do esmagamento. */
    public static final RegistryObject<SoundEvent> SMASH = register("froggydude.smash");
    /** A voz a cada soco do esmagamento ("tai! tai! tai!"). */
    public static final RegistryObject<SoundEvent> SMASH_TAI = register("froggydude.smash_tai");
    /** O baque do pulo altíssimo no chão. */
    public static final RegistryObject<SoundEvent> SKY_LAND = register("froggydude.sky_land");
    /** Braço sendo arrancado. */
    public static final RegistryObject<SoundEvent> RIP = register("froggydude.rip");
    /** Ossos estalando na contorção da fase 2 (vs Grox, 5:16: "hughghuguuhuh *crack*"). */
    public static final RegistryObject<SoundEvent> CRACK = register("froggydude.crack");
    /**
     * O começo da contorção. No mod é o primeiro estalo (os outros a FroggyFrenzyGoal toca
     * nos trancos da animação); o pacote de voz põe o som original inteiro (3,4 s de gemido
     * com os ossos estalando, o mesmo no short da fase 2, na versão longa e no "horror mod").
     */
    public static final RegistryObject<SoundEvent> CONTORT = register("froggydude.contort");
    /**
     * Os estalos e os gemidos que a contorção toca por conta própria (no mod: o CRACK e o
     * HURT). O pacote de voz cala os dois, porque o som original já traz tudo; senão
     * embolavam com ele (o "bloco de osso sendo posto no chão" que o LM ouviu).
     */
    public static final RegistryObject<SoundEvent> CONTORT_CRACK = register("froggydude.contort_crack");
    public static final RegistryObject<SoundEvent> CONTORT_GRUNT = register("froggydude.contort_grunt");
    /**
     * A cutscene inteira com o áudio dele comendo a zebra (o vídeo "Eating a Zebra"):
     * só o pacote de voz tem o arquivo; no mod é vazio e ficam os sons de cada golpe.
     * FINISHER_R é a mesma coisa a partir do braço direito (quem já não tinha o esquerdo).
     */
    public static final RegistryObject<SoundEvent> FINISHER = register("froggydude.finisher");
    public static final RegistryObject<SoundEvent> FINISHER_R = register("froggydude.finisher_r");
    /** Mastigando o braço. */
    public static final RegistryObject<SoundEvent> CHEW = register("froggydude.chew");
    /** As patadas do galope de quatro (junto com o passo do bloco do chão). */
    public static final RegistryObject<SoundEvent> GALLOP = register("froggydude.gallop");
    /**
     * O banquete em cima dos pedaços (v0.7): o "nhum nhum NHOM" com o sangue, em loop
     * perfeito no cliente (client.pieces.FeastSounds) no ritmo da raiva e da fome. Sem o
     * pacote de voz é mudo - aí quem soa são as mordidas (FEAST_BITE, FEAST_SQUISH).
     */
    public static final RegistryObject<SoundEvent> FEAST = register("froggydude.feast");
    /** Cada mordida do banquete sem o pacote: dente e mastigada (o pacote cala). */
    public static final RegistryObject<SoundEvent> FEAST_BITE = register("froggydude.feast_bite");
    /** O molhado do sangue a cada mordida sem o pacote (o pacote cala). */
    public static final RegistryObject<SoundEvent> FEAST_SQUISH = register("froggydude.feast_squish");

    /**
     * Uma fala por som (froggydude.voice.&lt;clipe&gt;), pra tocar na ordem dos vídeos
     * (entity/voice). Sem o pacote de voz, cada uma cai no som de reserva da categoria.
     */
    public static final Map<VoiceLine, RegistryObject<SoundEvent>> VOICE = new EnumMap<>(VoiceLine.class);

    static {
        for (VoiceLine line : VoiceLine.values()) {
            VOICE.put(line, register(line.soundName()));
        }
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ModEntityTypes.MOD_ID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
