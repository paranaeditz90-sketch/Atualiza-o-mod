package com.becomelm;

import net.minecraftforge.common.ForgeConfigSpec;

/** becomelm-common.toml (vale no servidor: quem decide a finalização é ele). */
public final class LmConfig {

    public static final ForgeConfigSpec SPEC;
    /** A chance de cada golpe virar a finalização (0,01 = 1 em 100). */
    public static final ForgeConfigSpec.DoubleValue FINISHER_CHANCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("finisher");
        FINISHER_CHANCE = b.comment("Chance of each hit turning into the finisher cutscene (0.01 = 1 in 100, 0 = never).",
                        "Chance de cada golpe virar a cena de finalização (0.01 = 1 em 100, 0 = nunca).")
                .defineInRange("chance", 0.01D, 0D, 1D);
        b.pop();
        SPEC = b.build();
    }

    private LmConfig() {
    }
}
