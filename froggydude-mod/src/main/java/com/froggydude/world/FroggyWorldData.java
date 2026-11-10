package com.froggydude.world;

import com.froggydude.brain.DangerMemory;
import com.froggydude.brain.FroggyBrain;
import com.froggydude.entity.voice.FroggyVoice;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

/**
 * O que o mod guarda no mundo (data/froggydude.dat), fora do FroggyDude: assim
 * ele morre, volta e continua de onde parou - a fila das falas, o que o cérebro
 * aprendeu de cada jogador, o manhunt em andamento, advertências e banimentos.
 */
public class FroggyWorldData extends SavedData {

    private static final String NAME = "froggydude";

    /** Em que fala de cada situação ele está. */
    public final FroggyVoice voice = new FroggyVoice();
    /** O que ele aprendeu de cada jogador. */
    public final FroggyBrain brain = new FroggyBrain();
    /** Onde ele já se machucou (o caminho desvia). */
    public final DangerMemory dangers = new DangerMemory();
    /** (v0.7.29) Item de mod -> quanto já doeu nele (vida somada): o que mais doeu ele quer tirar da pessoa. */
    public final java.util.Map<String, Float> pain = new java.util.HashMap<>();

    // ----- o FroggyDude do mundo (só existe um)
    @Nullable
    public UUID froggyId;
    public boolean froggyAlive;
    public float froggyHealth;
    /** Hora do jogo em que ele volta (manhunt, depois de morrer); -1 = não vai voltar. */
    public long respawnAt = -1;

    // ----- a caçada fora do manhunt (v0.7.25, Hunt): quem ele está caçando e até quando lembra
    @Nullable
    public UUID huntPlayer;
    public long huntUntil;
    /** (v0.7.26) Zombou e sumiu: quando a trégua acabar, ele volta escondido pelas costas dela. */
    public boolean huntAmbush;

    // ----- manhunt
    public boolean manhuntActive;
    public ManhuntMode manhuntMode = ManhuntMode.SPEEDRUN;
    public int manhuntDays = 5;
    public long manhuntStart;
    /** dayTime do overworld no começo (pra contar os dias). */
    public long manhuntStartDay;
    /** Hora do jogo em que ele é solto (depois da vantagem). */
    public long releaseAt;
    public boolean released;
    public int lastDayShown;

    // ----- Froggy's in your world (Invasion)
    /** Dia do mundo em que ele chega (sorteado, escondido); 0 = ainda não sorteou. */
    public int invasionDay;
    /** 0 esperando, 1 a LAN falsa saiu, 2 "entrou no jogo" (vindo), 3 aqui caçando, 4 foi embora (dragão). */
    public int invasionStage;
    /** Hora do jogo do próximo passo da chegada (0 = ainda não marcou). */
    public long invasionAt;

    public static FroggyWorldData get(ServerLevel level) {
        return get(level.getServer());
    }

    public static FroggyWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(FroggyWorldData::load, FroggyWorldData::new, NAME);
    }

    /**
     * Mundo de desenvolvimento: "-dev" no nome do mundo (ou da pasta). Ali o
     * cérebro conta no chat o que aprendeu.
     */
    public static boolean isDev(MinecraftServer server) {
        String name = server.getWorldData().getLevelName().toLowerCase(Locale.ROOT);
        String folder = String.valueOf(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName())
                .toLowerCase(Locale.ROOT);
        return name.contains("-dev") || folder.contains("-dev");
    }

    private static FroggyWorldData load(CompoundTag tag) {
        FroggyWorldData d = new FroggyWorldData();
        d.voice.load(tag.getCompound("Voice"));
        d.brain.load(tag.getCompound("Brain"));
        d.dangers.load(tag.getCompound("Dangers"));
        if (tag.hasUUID("FroggyId")) d.froggyId = tag.getUUID("FroggyId");
        d.froggyAlive = tag.getBoolean("FroggyAlive");
        d.froggyHealth = tag.getFloat("FroggyHealth");
        d.respawnAt = tag.contains("RespawnAt") ? tag.getLong("RespawnAt") : -1;
        if (tag.hasUUID("HuntPlayer")) d.huntPlayer = tag.getUUID("HuntPlayer");
        d.huntUntil = tag.getLong("HuntUntil");
        d.huntAmbush = tag.getBoolean("HuntAmbush");
        CompoundTag painTag = tag.getCompound("Pain");
        for (String k : painTag.getAllKeys()) d.pain.put(k, painTag.getFloat(k));
        CompoundTag mh = tag.getCompound("Manhunt");
        d.manhuntActive = mh.getBoolean("Active");
        try {
            d.manhuntMode = ManhuntMode.valueOf(mh.getString("Mode"));
        } catch (IllegalArgumentException e) {
            d.manhuntMode = ManhuntMode.SPEEDRUN;
        }
        d.manhuntDays = Math.max(1, mh.getInt("Days"));
        d.manhuntStart = mh.getLong("Start");
        d.manhuntStartDay = mh.getLong("StartDay");
        d.releaseAt = mh.getLong("ReleaseAt");
        d.released = mh.getBoolean("Released");
        d.lastDayShown = mh.getInt("LastDay");
        CompoundTag inv = tag.getCompound("Invasion");
        d.invasionDay = inv.getInt("Day");
        d.invasionStage = inv.getInt("Stage");
        d.invasionAt = inv.getLong("At");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("Voice", voice.save());
        tag.put("Brain", brain.save());
        tag.put("Dangers", dangers.save());
        if (froggyId != null) tag.putUUID("FroggyId", froggyId);
        tag.putBoolean("FroggyAlive", froggyAlive);
        tag.putFloat("FroggyHealth", froggyHealth);
        tag.putLong("RespawnAt", respawnAt);
        if (huntPlayer != null) tag.putUUID("HuntPlayer", huntPlayer);
        tag.putLong("HuntUntil", huntUntil);
        tag.putBoolean("HuntAmbush", huntAmbush);
        CompoundTag painTag = new CompoundTag();
        pain.forEach(painTag::putFloat);
        tag.put("Pain", painTag);
        CompoundTag mh = new CompoundTag();
        mh.putBoolean("Active", manhuntActive);
        mh.putString("Mode", manhuntMode.name());
        mh.putInt("Days", manhuntDays);
        mh.putLong("Start", manhuntStart);
        mh.putLong("StartDay", manhuntStartDay);
        mh.putLong("ReleaseAt", releaseAt);
        mh.putBoolean("Released", released);
        mh.putInt("LastDay", lastDayShown);
        tag.put("Manhunt", mh);
        CompoundTag inv = new CompoundTag();
        inv.putInt("Day", invasionDay);
        inv.putInt("Stage", invasionStage);
        inv.putLong("At", invasionAt);
        tag.put("Invasion", inv);
        return tag;
    }
}
