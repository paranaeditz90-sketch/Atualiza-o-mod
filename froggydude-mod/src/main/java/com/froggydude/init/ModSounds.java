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

    /** Parado/andando: ribbit, "wibbit", risada. */
    public static final RegistryObject<SoundEvent> AMBIENT = register("froggydude.ambient");
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
    /** Invasão (anti-trapaça): a ligação pro 911 (o áudio original vem no pacote de voz). */
    public static final RegistryObject<SoundEvent> INVASION_CALL = register("froggydude.invasion.call");
    /** Invasão: a janela quebrando. */
    public static final RegistryObject<SoundEvent> INVASION_GLASS = register("froggydude.invasion.glass");
    /** A voz a cada soco do esmagamento ("tai! tai! tai!"). */
    public static final RegistryObject<SoundEvent> SMASH_TAI = register("froggydude.smash_tai");
    /** O baque do pulo altíssimo no chão. */
    public static final RegistryObject<SoundEvent> SKY_LAND = register("froggydude.sky_land");
    /** Braço sendo arrancado. */
    public static final RegistryObject<SoundEvent> RIP = register("froggydude.rip");
    /** Ossos estalando na contorção da fase 2 (vs Grox, 5:16: "hughghuguuhuh *crack*"). */
    public static final RegistryObject<SoundEvent> CRACK = register("froggydude.crack");
    /**
     * A contorção inteira (2,4 s): ossos estalando + o gemido/risada torta, do
     * jeito que sai no original durante a dobra. Sem o pacote de voz: estalos.
     */
    public static final RegistryObject<SoundEvent> CONTORT = register("froggydude.contort");
    /** Mastigando o braço. */
    public static final RegistryObject<SoundEvent> CHEW = register("froggydude.chew");
    /** As patadas do galope de quatro (junto com o passo do bloco do chão). */
    public static final RegistryObject<SoundEvent> GALLOP = register("froggydude.gallop");

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
