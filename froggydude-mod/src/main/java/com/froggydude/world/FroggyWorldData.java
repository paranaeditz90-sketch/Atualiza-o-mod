package com.froggydude.world;

import com.froggydude.brain.DangerMemory;
import com.froggydude.brain.FroggyBrain;
import com.froggydude.entity.voice.FroggyVoice;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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

    // ----- (v0.7.34) o diretor de tensão (TensionDirector), só no "aparece no teu mundo"
    /** A fase (TensionDirector.Phase): 0 ainda não começou. */
    public int dirPhase;
    /** Até quando vai a fase (hora do jogo). */
    public long dirUntil;
    /** Quando a fase começou (hora do jogo; 0 = save antigo): o quanto os sinais já andaram. */
    public long dirSince;
    /** O próximo sinal (hora do jogo). */
    public long dirNextSign;
    /** A presa do ciclo. */
    @javax.annotation.Nullable
    public java.util.UUID dirTarget;
    /** O último sinal (nunca o mesmo duas vezes seguidas). */
    public String dirLastSign = "";
    /** Falso alívio: se ela for dormir agora, ele volta. */
    public boolean dirTrap;
    /** (v0.7.35) Quantas vezes ele já renasceu neste mundo (a 1ª é a longa; depois, mais rápida). */
    public int revives;
    /** Ele fora do mundo (calmaria, sinais): o FroggyDude inteiro guardado, pra voltar o mesmo. */
    @javax.annotation.Nullable
    public CompoundTag parkedFroggy;

    // ----- (v0.7.42) o anti-trapaça (AntiCheat)
    /** "Trapaças?" do mundo: -1 mundo antigo (desligado), 0 Não (ele pune), 1 Sim. */
    public byte cheats = AntiCheat.UNSET;
    /** Quem entrou no criativo/espectador com "Trapaças? Não": vai ver o vídeo e morrer. */
    public final Set<UUID> condemned = new HashSet<>();
    /** Desses, quem já viu o vídeo e morreu: não renasce nunca mais. */
    public final Set<UUID> executed = new HashSet<>();

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
        d.revives = tag.getInt("Revives");
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
        CompoundTag dir = inv.getCompound("Director");
        d.dirPhase = dir.getInt("Phase");
        d.dirUntil = dir.getLong("Until");
        d.dirSince = dir.getLong("Since");
        d.dirNextSign = dir.getLong("NextSign");
        if (dir.hasUUID("Target")) d.dirTarget = dir.getUUID("Target");
        d.dirLastSign = dir.getString("LastSign");
        d.dirTrap = dir.getBoolean("Trap");
        d.parkedFroggy = dir.contains("Parked") ? dir.getCompound("Parked") : null;
        CompoundTag ac = tag.getCompound("AntiCheat");
        d.cheats = ac.contains("Cheats") ? ac.getByte("Cheats") : AntiCheat.UNSET;
        readUuids(ac.getList("Condemned", Tag.TAG_INT_ARRAY), d.condemned);
        readUuids(ac.getList("Executed", Tag.TAG_INT_ARRAY), d.executed);
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
        tag.putInt("Revives", revives);
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
        CompoundTag dir = new CompoundTag();
        dir.putInt("Phase", dirPhase);
        dir.putLong("Until", dirUntil);
        dir.putLong("Since", dirSince);
        dir.putLong("NextSign", dirNextSign);
        if (dirTarget != null) dir.putUUID("Target", dirTarget);
        dir.putString("LastSign", dirLastSign);
        dir.putBoolean("Trap", dirTrap);
        if (parkedFroggy != null) dir.put("Parked", parkedFroggy);
        inv.put("Director", dir);
        tag.put("Invasion", inv);
        CompoundTag ac = new CompoundTag();
        if (cheats != AntiCheat.UNSET) ac.putByte("Cheats", cheats);
        ac.put("Condemned", writeUuids(condemned));
        ac.put("Executed", writeUuids(executed));
        tag.put("AntiCheat", ac);
        return tag;
    }

    private static void readUuids(ListTag list, Set<UUID> into) {
        for (Tag t : list) into.add(NbtUtils.loadUUID(t));
    }

    private static ListTag writeUuids(Set<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID u : uuids) list.add(NbtUtils.createUUID(u));
        return list;
    }
}
