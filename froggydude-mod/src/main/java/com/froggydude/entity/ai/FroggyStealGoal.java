package com.froggydude.entity.ai;

import com.froggydude.brain.ModWeapons;
import com.froggydude.brain.PlayerMemory;
import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.world.FroggyWorldData;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * (v0.7.29) Roubo de arma de mod com a língua (ideia do LM, 09/11). Referência: FroggyDude VS Grox MANHUNT
 * 1:17,7-1:24,5 - a pistola do Grox no chão, a língua chata vai, gruda e volta com ela na ponta em ~0,35 s; ele olha
 * de boca escancarada ("This looks tasty!") e come a arma atravessada na boca. Só na IA Extra, Máx e Real.
 *
 * Alvo: a arma de mod de quem ele caça (a da mão primeiro, senão a da mochila: a língua puxa de onde estiver) ou uma
 * arma de mod largada no chão (inclusive a que cai quando a pessoa morre). A língua vai (10 ticks), gruda e volta
 * com a arma na ponta (6 ticks); ele mastiga com ela atravessada na boca (as mordidas do arm_eat, farelo do item) e
 * engole: vai pra barriga sem a munição de dentro e cai quando ele morre. Apanhou mastigando: cospe ela inteira.
 */
public class FroggyStealGoal extends Goal {

    private static final double RANGE = 9.5D;
    /** Colado nela a língua arranca da mão mesmo (antes, abaixo de 2,2 blocos não tentava e, na briga de perto, nunca roubava). */
    private static final double MIN_DIST = 1.2D;
    private static final int AIM_TICKS = 10;
    private static final int BACK_TICKS = 6;
    private static final int EAT_TICKS = FroggyState.ARM_EAT.durationTicks;
    /** As mordidas do arm_eat (0,30 s, 1,45 s e 2,60 s) e o engolir. */
    private static final int[] BITES = {6, 29, 52};
    private static final int SWALLOW = 80;
    /** Depois de um roubo (ou de errar), quanto tempo até a língua tentar de novo. */
    private static final int COOLDOWN = 160;
    /** Marca no item que ele cuspiu/largou: 30 s pra pessoa pegar de volta antes dele querer de novo. */
    public static final String SPAT_TAG = "FroggySpatAt";
    private static final long SPAT_GRACE = 600L;

    private enum Phase { AIM, BACK, EAT }

    /** O estudo dos mods sai uma vez no log (FROGGYDEBUG). */
    private static boolean studied;

    private final FroggydudeEntity froggy;
    @Nullable
    private Phase phase;
    private int t;
    @Nullable
    private ServerPlayer victim;
    @Nullable
    private ItemEntity loot;
    private ItemStack taken = ItemStack.EMPTY;
    private int checkAt;
    private int readyAt;

    public FroggyStealGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        // relógio próprio (nada de tickCount % N aqui: o jogo só roda o canUse em tick sim, tick não)
        if (froggy.tickCount < checkAt || froggy.tickCount < readyAt) return false;
        // a cada vez que o jogo pergunta (tick sim, tick não): na hora que ele te vê, a língua vem antes do bote
        checkAt = froggy.tickCount + 2;
        if (!(froggy.level() instanceof ServerLevel level) || !FroggyConfig.ai().stealsWeapons() || !free()) return false;
        if (!studied) {
            studied = true;
            // o estudo inteiro (todos os itens do jogo) só pro log: só quando alguém vai ler (no celular pesa)
            if (Boolean.getBoolean("froggydude.debug") || FroggyWorldData.isDev(level.getServer())) {
                froggy.brainLog("estudei os mods: " + ModWeapons.study());
            }
        }
        FroggyWorldData data = FroggyWorldData.get(level);
        victim = null;
        loot = null;
        if (froggy.getTarget() instanceof ServerPlayer p && p.isAlive() && !p.isCreative() && !p.isSpectator()
                && !froggy.underTruce(p)) {
            double d = froggy.distanceTo(p);
            if (d >= MIN_DIST && d <= RANGE && froggy.hasLineOfSight(p) && ModWeapons.pickSlot(p, data) >= 0) {
                victim = p;
                return true;
            }
        }
        ItemEntity best = null;
        double bestD = Double.MAX_VALUE;
        long now = level.getGameTime();
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, froggy.getBoundingBox().inflate(RANGE),
                e -> e.isAlive() && !justSpat(e, now) && ModWeapons.stealable(e.getItem(), data))) {
            double d = froggy.distanceTo(e);
            if (d <= RANGE && d < bestD && froggy.hasLineOfSight(e)) {
                best = e;
                bestD = d;
            }
        }
        loot = best;
        return loot != null;
    }

    /** Ele mesmo cuspiu/largou isso faz pouco: a pessoa tem 30 s pra pegar de volta. */
    private static boolean justSpat(ItemEntity e, long now) {
        return e.getPersistentData().contains(SPAT_TAG) && now - e.getPersistentData().getLong(SPAT_TAG) < SPAT_GRACE;
    }

    /** Livre pra isso: em pé, fora de golpe, sem comer, sem se esconder, sem estar transformado ou zombando. */
    private boolean free() {
        FroggyState st = froggy.getFroggyState();
        LivingEntity target = froggy.getTarget();
        return (st == FroggyState.IDLE || st == FroggyState.CHASE) && !froggy.isFrenzyActive() && !froggy.isHolding()
                && froggy.getFeedTarget() == null && !froggy.isHiding() && !froggy.isClimbing()
                && froggy.getHeldArmOwner().isEmpty() && froggy.getMouthItem().isEmpty()
                && (target == null || !froggy.isMocking(target));
    }

    @Override
    public boolean canContinueToUse() {
        return phase != null && froggy.isAlive();
    }

    @Override
    public void start() {
        phase = Phase.AIM;
        t = 0;
        taken = ItemStack.EMPTY;
        froggy.getNavigation().stop();
        froggy.setFroggyState(FroggyState.TONGUE_GRAB);
        froggy.openMouth(AIM_TICKS + BACK_TICKS + 4);
        if (victim != null) {
            taunt(victim);
            froggy.brainLog(victim.getScoreboardName() + " tem arma de mod: a língua vai buscar");
        } else if (loot != null) {
            froggy.brainLog("arma de mod no chão (" + loot.getItem().getHoverName().getString() + "): a língua vai buscar");
        }
    }

    /** A fala rara: só na 1ª vez com cada pessoa (LM: "Não vejo a hora de testar seus brinquedinhos"). */
    private void taunt(ServerPlayer p) {
        FroggyWorldData data = FroggyWorldData.get(p.serverLevel());
        PlayerMemory m = data.brain.memory(p.getUUID(), p.getScoreboardName());
        if (m.toysTaunted) return;
        m.toysTaunted = true;
        data.setDirty();
        froggy.chat(p, "froggydude.chat.toys");
    }

    @Override
    public void tick() {
        if (phase == null) return;
        t++;
        switch (phase) {
            case AIM -> {
                Vec3 at = aimPoint();
                if (at == null) {
                    finish();
                    return;
                }
                face(at);
                Vec3 mouth = froggy.position().add(0, froggy.getBbHeight() * 0.88D, 0);
                froggy.setTongueLength((float) Math.min(mouth.distanceTo(at), RANGE));
                if (t >= AIM_TICKS) grab();
            }
            case BACK -> {
                // a língua volta com a arma na ponta (o renderer desenha ela lá); chegou na boca: mastiga
                if (t >= BACK_TICKS) {
                    froggy.setTongueItem(ItemStack.EMPTY);
                    froggy.setTongueLength(0F);
                    froggy.setMouthItem(taken);
                    froggy.setFroggyState(FroggyState.ARM_EAT);
                    froggy.openMouth(EAT_TICKS);
                    phase = Phase.EAT;
                    t = 0;
                }
            }
            case EAT -> {
                // apanhou mastigando e cuspiu (FroggydudeEntity.hurt): acabou
                if (t < SWALLOW && froggy.getMouthItem().isEmpty()) {
                    finish();
                    return;
                }
                LivingEntity look = victim != null && victim.isAlive() ? victim : froggy.getTarget();
                if (look != null) froggy.getLookControl().setLookAt(look, 30.0F, 30.0F);
                for (int b : BITES) {
                    if (t == b) crunch();
                }
                if (t == SWALLOW) {
                    ItemStack eaten = froggy.getMouthItem();
                    froggy.swallow(eaten);
                    froggy.setMouthItem(ItemStack.EMPTY);
                    froggy.playSound(SoundEvents.PLAYER_BURP, 1.2F, 0.6F);
                    froggy.brainLog("comi " + eaten.getHoverName().getString()
                            + (victim != null ? " de " + victim.getScoreboardName() : "") + " (fica na barriga até eu morrer)");
                }
                if (t >= EAT_TICKS) finish();
            }
        }
    }

    /** Onde a língua mira: a mão/o meio do corpo da pessoa, ou a arma no chão. */
    @Nullable
    private Vec3 aimPoint() {
        if (victim != null) {
            if (!victim.isAlive() || victim.isSpectator() || froggy.distanceTo(victim) > RANGE + 1.5D) return null;
            return victim.position().add(0, victim.getBbHeight() * 0.55D, 0);
        }
        if (loot != null && loot.isAlive()) return loot.position().add(0, 0.15D, 0);
        return null;
    }

    private void face(Vec3 at) {
        froggy.getLookControl().setLookAt(at.x, at.y, at.z, 90.0F, 90.0F);
        double dx = at.x - froggy.getX(), dz = at.z - froggy.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) return;
        float yaw = (float) (Mth.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
        froggy.setYRot(yaw);
        froggy.yBodyRot = yaw;
        froggy.yHeadRot = yaw;
    }

    /** A língua chegou: arranca a arma (da mão, da mochila ou do chão) ou errou. */
    private void grab() {
        froggy.onTongueOut();
        ItemStack got = ItemStack.EMPTY;
        String from = "";
        if (victim != null) {
            // no meio da mira ela pode ter virado criativo/espectador ou entrado em trégua: aí não
            if (!victim.isCreative() && !victim.isSpectator() && !froggy.underTruce(victim)
                    && froggy.hasLineOfSight(victim) && froggy.distanceTo(victim) <= RANGE + 0.5D) {
                FroggyWorldData data = FroggyWorldData.get(victim.serverLevel());
                Inventory inv = victim.getInventory();
                // pode ter trocado de slot no meio: pega a melhor de agora
                int s = ModWeapons.pickSlot(victim, data);
                if (s >= 0) {
                    got = inv.getItem(s).copy();
                    inv.setItem(s, ItemStack.EMPTY);
                    from = " de " + victim.getScoreboardName() + (s == inv.selected ? " (da mão)"
                            : s == Inventory.SLOT_OFFHAND ? " (da outra mão)" : " (da mochila)");
                }
            }
        } else if (loot != null && loot.isAlive() && froggy.distanceTo(loot) <= RANGE + 0.5D) {
            got = loot.getItem().copy();
            loot.discard();
            from = " do chão";
        }
        if (got.isEmpty()) {
            froggy.onTongueMiss();
            froggy.brainLog("a língua não pegou a arma");
            finish();
            return;
        }
        taken = got;
        froggy.setTongueItem(got);
        froggy.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 0.5F);
        froggy.brainLog("a língua arrancou " + got.getHoverName().getString() + from);
        phase = Phase.BACK;
        t = 0;
    }

    /** Uma mordida na arma: farelo do próprio item e o barulho de mastigar metal. */
    private void crunch() {
        ItemStack s = froggy.getMouthItem();
        if (s.isEmpty() || !(froggy.level() instanceof ServerLevel level)) return;
        Vec3 look = froggy.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-4D ? Vec3.ZERO : look.normalize();
        Vec3 mouth = froggy.position().add(look.scale(0.45D)).add(0, froggy.getBbHeight() * 0.85D, 0);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, s), mouth.x, mouth.y, mouth.z, 12,
                0.15D, 0.1D, 0.15D, 0.08D);
        froggy.playSound(SoundEvents.GENERIC_EAT, 1.3F, 0.55F + froggy.getRandom().nextFloat() * 0.2F);
        froggy.playSound(SoundEvents.ITEM_BREAK, 0.9F, 0.5F + froggy.getRandom().nextFloat() * 0.2F);
    }

    private void finish() {
        phase = null;
    }

    @Override
    public void stop() {
        // interrompido no meio (outra meta, ela fugiu): o que estava na língua/boca cai inteiro, nada some
        if (!froggy.level().isClientSide && froggy.isAlive()) {
            for (ItemStack s : new ItemStack[]{froggy.getTongueItem(), froggy.getMouthItem()}) {
                if (s.isEmpty()) continue;
                ItemEntity dropped = froggy.spawnAtLocation(s.copy(), 1.0F);
                if (dropped != null) dropped.getPersistentData().putLong(SPAT_TAG, froggy.level().getGameTime());
            }
        }
        froggy.setTongueItem(ItemStack.EMPTY);
        froggy.setMouthItem(ItemStack.EMPTY);
        froggy.setTongueLength(0F);
        FroggyState st = froggy.getFroggyState();
        if (st == FroggyState.TONGUE_GRAB || st == FroggyState.ARM_EAT) {
            froggy.setFroggyState(froggy.getTarget() != null ? FroggyState.CHASE : FroggyState.IDLE);
        }
        readyAt = froggy.tickCount + COOLDOWN + froggy.getRandom().nextInt(80);
        phase = null;
        victim = null;
        loot = null;
        taken = ItemStack.EMPTY;
    }
}
