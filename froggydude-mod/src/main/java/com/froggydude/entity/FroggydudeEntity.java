package com.froggydude.entity;

import com.froggydude.entity.ai.FroggyCombatGoal;
import com.froggydude.entity.ai.FroggyFeedGoal;
import com.froggydude.entity.ai.FroggyFrenzyGoal;
import com.froggydude.init.ModSounds;
import com.froggydude.player.ArmLoss;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Optional;
import java.util.UUID;

/**
 * O chefe Froggydude: escolhe entre vários ataques de língua e de impacto,
 * entra na fase 2 quando está machucado, e caça mobs pra devorar quando a
 * vida está muito baixa.
 *
 * A "IA de qual ataque usar" mora nas Goals (pacote entity.ai). Esta classe
 * guarda o estado sincronizado (pra animação e textura) e executa as ações
 * que as Goals disparam: sangue, cura ao comer, etc.
 */
public class FroggydudeEntity extends Monster implements GeoEntity {

    /** Tamanho do modelo: um jogador (1,8 bloco) e um tiquinho maior. Igual à hitbox. */
    public static final float MODEL_SCALE = 1.05F;

    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_GORE_STAGE =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_SKIN_VARIANT =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_FRENZY =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_TIRED =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_TONGUE_LEN =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.FLOAT);
    /** Correndo de quatro (decidido no servidor, com folga, pra animação não ficar piscando). */
    private static final EntityDataAccessor<Boolean> DATA_RUNNING =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BOOLEAN);
    /** Andando (mesma ideia, pra não alternar parado/andando a cada tick). */
    private static final EntityDataAccessor<Boolean> DATA_MOVING =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BOOLEAN);
    /** Teste: força uma animação em loop (NBT "DebugAnim"). Vazio = normal. */
    private static final EntityDataAccessor<String> DATA_DEBUG_ANIM =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.STRING);
    /** De quem é o braço que ele está segurando/comendo (pra desenhar com a skin certa). */
    private static final EntityDataAccessor<Optional<UUID>> DATA_HELD_ARM =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final DustParticleOptions BLOOD_DUST =
            new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.4F);
    private static final BlockParticleOption BLOOD_CHUNK =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());

    /** Fase 2: mais rápido que um jogador correndo (0,28 x 1,57 = 0,44, uns 7,8 m/s). */
    private static final double FRENZY_SPEED_BONUS = 0.57D;
    public static final int FRENZY_TICKS = 600; // 30 segundos

    private static final UUID FRENZY_SPEED_ID = UUID.fromString("6f3a2e6a-6b9e-4e9a-8f9a-6b6a1a2f9e11");
    private static final UUID FRENZY_TIRED_ID = UUID.fromString("1c1a9b0e-2d3f-4a5b-9c8d-7e6f5a4b3c2d");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // --- só no servidor (as Goals rodam só lá) ---
    private int stateTicks = 0;
    @Nullable
    private FroggyState lastAttack = null;
    private int attackCooldown = 0;
    private final EnumMap<FroggyState, Integer> cooldowns = new EnumMap<>(FroggyState.class);
    private int frenzyTicksLeft = 0;
    private int frenzyTiredTicksLeft = 0;
    private int frenzyCooldown = 0;
    private boolean phase2Started = false;
    private int noFallTicks = 0;
    @Nullable
    private LivingEntity feedTarget;
    private int feedSearchCooldown = 0;
    private int movingHold = 0;
    private int pinnedVictimId = -1;
    private int vocalCooldown = 0;
    /** Teste: NBT "DebugHunt" faz ele caçar o porco mais perto (pra filmar de lado). */
    private boolean debugHunt = false;
    /** Teste: NBT "DebugAttack" (ex.: "HIGH_JUMP") faz ele só usar esse ataque. */
    @Nullable
    private FroggyState debugAttack = null;
    private int runningHold = 0;
    /** Teste: NBT "DebugHeldArm" faz ele segurar o braço do jogador mais perto (pra ver a animação de comer). */
    private boolean debugHeldArm = false;
    /** Teste: NBT "DebugArmless" arranca o braço do jogador mais perto assim que o mundo abre. */
    private boolean debugArmless = false;
    /** Teste: NBT "DebugStalk" faz ele sempre começar só olhando (modo apavorar). */
    private boolean debugStalk = false;
    /** "Apavorar" em vez de "matar": segue de longe, encara, não ataca (ticks restantes). */
    private int stalkTicks = 0;
    /** Comeu blaze: entra na fase 2 assim que der. */
    private boolean phase2Requested = false;
    private int painCooldown = 0;

    // --- só no cliente ---
    private double clientGroundSpeed = 0D;
    private double hopClock = 0D;
    private int clientStateAge = 0;
    private int lastClientState = -1;

    public FroggydudeEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 25;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, (byte) FroggyState.IDLE.ordinal());
        this.entityData.define(DATA_GORE_STAGE, (byte) 0);
        this.entityData.define(DATA_SKIN_VARIANT, (byte) SkinVariant.NORMAL.ordinal());
        this.entityData.define(DATA_FRENZY, false);
        this.entityData.define(DATA_TIRED, false);
        this.entityData.define(DATA_TONGUE_LEN, 0.0F);
        this.entityData.define(DATA_RUNNING, false);
        this.entityData.define(DATA_MOVING, false);
        this.entityData.define(DATA_DEBUG_ANIM, "");
        this.entityData.define(DATA_HELD_ARM, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        // 0: comer tem prioridade sobre tudo, inclusive lutar
        this.goalSelector.addGoal(0, new FroggyFeedGoal(this));
        // 1: fase 2 (contorção + grito + 30 s correndo mais que um jogador)
        this.goalSelector.addGoal(1, new FroggyFrenzyGoal(this));
        // 2: cérebro de combate - só ataca quando tem certeza que acerta
        this.goalSelector.addGoal(2, new FroggyCombatGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // faro: quem está sem braço sangra e ele sente o cheiro de longe, mesmo sem ver
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                e -> e instanceof Player p && ArmLoss.isArmless(p)) {
            {
                this.targetConditions = this.targetConditions.ignoreLineOfSight();
            }

            @Override
            protected double getFollowDistance() {
                return 64.0D;
            }
        });
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HURT.get();
    }

    /** Na maioria das vezes grunhe; às vezes, se não está falando, solta "I love pain". */
    @Override
    protected void playHurtSound(DamageSource source) {
        if (vocalCooldown <= 0 && this.getRandom().nextFloat() < 0.35F) {
            vocalize(ModSounds.LOVE_PAIN.get(), 1.6F);
        } else {
            super.playHurtSound(source);
        }
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160; // um ribbit a cada ~8 s em média
    }

    /** Quando acha uma vítima nova: som de caça (froggydude.hunt). */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity old = this.getTarget();
        super.setTarget(target);
        if (target instanceof Player && old != target && !this.level().isClientSide) {
            vocalize(ModSounds.HUNT.get(), 2.0F);
            // metade das vezes ele não ataca de cara: segue de longe e encara
            if (debugStalk || (debugAttack == null && this.getLastHurtByMob() != target && this.getRandom().nextBoolean())) {
                startStalking(160 + this.getRandom().nextInt(140));
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // é um chefe, não some sozinho
    }

    // ---------------- estado sincronizado (animação e textura) ----------------

    public FroggyState getFroggyState() {
        return FroggyState.values()[this.entityData.get(DATA_STATE)];
    }

    public void setFroggyState(FroggyState state) {
        this.entityData.set(DATA_STATE, (byte) state.ordinal());
        this.stateTicks = 0;
    }

    /** Ticks desde que o estado atual começou (só é confiável no servidor). */
    public int getStateTicks() {
        return stateTicks;
    }

    public boolean isVulnerableState() {
        FroggyState s = getFroggyState();
        return s == FroggyState.FEEDING || s == FroggyState.CONTORTING;
    }

    public boolean isRunning() {
        return this.entityData.get(DATA_RUNNING);
    }

    public boolean isMovingSynced() {
        return this.entityData.get(DATA_MOVING);
    }

    @Nullable
    public FroggyState getDebugAttack() {
        return debugAttack;
    }

    public String getDebugAnim() {
        return this.entityData.get(DATA_DEBUG_ANIM);
    }

    /** 0 a 3 - quanto maior, mais sangue na skin (pela vida perdida). */
    public int getGoreStage() {
        return this.entityData.get(DATA_GORE_STAGE);
    }

    public SkinVariant getSkinVariant() {
        return SkinVariant.byOrdinal(this.entityData.get(DATA_SKIN_VARIANT));
    }

    public void setSkinVariant(SkinVariant variant) {
        this.entityData.set(DATA_SKIN_VARIANT, (byte) variant.ordinal());
    }

    public boolean isFrenzyActive() {
        return this.entityData.get(DATA_FRENZY);
    }

    public boolean isFrenzyTired() {
        return this.entityData.get(DATA_TIRED);
    }

    /** Comprimento da língua em blocos (o modelo estica a língua até aqui). */
    public float getTongueLength() {
        return this.entityData.get(DATA_TONGUE_LEN);
    }

    public void setTongueLength(float blocks) {
        this.entityData.set(DATA_TONGUE_LEN, blocks);
    }

    /**
     * Onde está o rosto dele de verdade (no modelo), pra câmera de quem está
     * preso mirar ali. Montado, ele fica agachado: a cara fica baixa e à frente.
     */
    public Vec3 getFacePosition(float partialTick) {
        Vec3 pos = this.getPosition(partialTick);
        float yaw = Mth.rotLerp(partialTick, this.yBodyRotO, this.yBodyRot) * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        FroggyState state = getFroggyState();
        if (state == FroggyState.PIN_HOLD || state == FroggyState.ARM_RIP) {
            // agachado em cima: a cara fica baixa e à frente
            return pos.add(fx * 0.87D, 0.78D, fz * 0.87D);
        }
        if (state == FroggyState.SMASH) {
            // em pé por cima: um ponto fixo no peito/rosto, pra câmera não pular a cada soco
            return pos.add(fx * 0.15D, 1.45D, fz * 0.15D);
        }
        return this.getEyePosition(partialTick);
    }

    /** A vítima derrubada embaixo dele (ou null). */
    @Nullable
    public LivingEntity getPinnedVictim() {
        if (pinnedVictimId < 0) return null;
        Entity e = this.level().getEntity(pinnedVictimId);
        return e instanceof LivingEntity living ? living : null;
    }

    public void setPinnedVictim(@Nullable LivingEntity victim) {
        this.pinnedVictimId = victim == null ? -1 : victim.getId();
    }

    /** De quem é o braço na mão dele (vazio = não está segurando nada). */
    public Optional<UUID> getHeldArmOwner() {
        return this.entityData.get(DATA_HELD_ARM);
    }

    /** O cliente desenha o braço com a skin (e o tipo de braço, fino ou normal) desse jogador. */
    public void setHeldArm(@Nullable Player owner) {
        this.entityData.set(DATA_HELD_ARM, owner == null ? Optional.empty() : Optional.of(owner.getUUID()));
    }

    public boolean isStalking() {
        return stalkTicks > 0;
    }

    public void startStalking(int ticks) {
        this.stalkTicks = ticks;
    }

    public void stopStalking() {
        this.stalkTicks = 0;
    }

    /** Só no cliente: quantos ticks desde que o estado mudou (pra animar coisas fora do GeckoLib). */
    public int getClientStateAge() {
        return clientStateAge;
    }

    // ---------------- sons dos golpes (chamados pelas Goals) ----------------

    /** Fala/grita, mas não por cima de outra fala (evita encavalar a voz). */
    public void vocalize(SoundEvent sound, float volume) {
        if (vocalCooldown > 0) return;
        this.playSound(sound, volume, 0.95F + this.getRandom().nextFloat() * 0.1F);
        vocalCooldown = 30;
    }

    public void onBiteHit() {
        this.playSound(ModSounds.BITE.get(), 1.0F, 0.9F + this.getRandom().nextFloat() * 0.2F);
    }

    /** O estalo da língua saindo (nos vídeos parece um tiro). */
    public void onTongueOut() {
        this.playSound(ModSounds.TONGUE.get(), 1.3F, 0.9F + this.getRandom().nextFloat() * 0.2F);
    }

    public void onTongueTaste() {
        if (this.getRandom().nextInt(3) == 0) vocalize(ModSounds.TASTE.get(), 1.2F);
    }

    public void onLeapLaunch() {
        if (this.getRandom().nextInt(3) == 0) vocalize(ModSounds.LEAP.get(), 1.0F);
    }

    public void onPinStart() {
        vocalize(ModSounds.PIN.get(), 1.4F);
    }

    /** Cada soco do esmagamento. */
    public void onSmashHit() {
        this.playSound(ModSounds.SMASH.get(), 1.3F, 0.8F + this.getRandom().nextFloat() * 0.25F);
    }

    /** O baque do pulo altíssimo no chão. */
    public void onSkyLand() {
        this.playSound(ModSounds.SKY_LAND.get(), 2.0F, 0.85F + this.getRandom().nextFloat() * 0.1F);
    }

    /** Mastigando o braço. */
    public void onChew() {
        this.playSound(ModSounds.CHEW.get(), 1.0F, 0.85F + this.getRandom().nextFloat() * 0.3F);
    }

    // ---------------- usado só pelas Goals ----------------

    @Nullable
    public FroggyState getLastAttack() {
        return lastAttack;
    }

    public void setLastAttack(FroggyState state) {
        this.lastAttack = state;
    }

    public int getAttackCooldown() {
        return attackCooldown;
    }

    public void setAttackCooldown(int ticks) {
        this.attackCooldown = ticks;
    }

    /** Cada ataque tem seu próprio tempo de recarga. */
    public boolean isReady(FroggyState attack) {
        return cooldowns.getOrDefault(attack, 0) <= 0;
    }

    public void setCooldown(FroggyState attack, int ticks) {
        cooldowns.put(attack, ticks);
    }

    @Nullable
    public LivingEntity getFeedTarget() {
        return feedTarget;
    }

    public void setFeedTarget(@Nullable LivingEntity target) {
        this.feedTarget = target;
    }

    public int getFeedSearchCooldown() {
        return feedSearchCooldown;
    }

    public void setFeedSearchCooldown(int cooldown) {
        this.feedSearchCooldown = cooldown;
    }

    public boolean isLowHealth() {
        return this.getHealth() < this.getMaxHealth() * 0.3F;
    }

    // ----- fase 2 -----

    /**
     * A fase 2 começa quando ele come um blaze (como no manhunt) ou, uma vez,
     * quando a vida cai abaixo de 55%.
     */
    public boolean shouldStartPhase2() {
        return phase2Requested || (!phase2Started && this.getHealth() < this.getMaxHealth() * 0.55F);
    }

    /** Comeu blaze: "freaking spicy". Entra na fase 2 na primeira chance. */
    public void requestPhase2() {
        this.phase2Requested = true;
        setSkinVariant(SkinVariant.BURNED);
    }

    public void markPhase2Started() {
        this.phase2Started = true;
        this.phase2Requested = false;
    }

    public boolean isInPhase2() {
        return phase2Started;
    }

    public int getFrenzyCooldown() {
        return frenzyCooldown;
    }

    public void setFrenzyCooldown(int ticks) {
        this.frenzyCooldown = ticks;
    }

    /** Chamado pela FroggyFrenzyGoal assim que a contorção termina. */
    public void startFrenzy(int durationTicks) {
        this.frenzyTicksLeft = durationTicks;
        this.entityData.set(DATA_FRENZY, true);
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(FRENZY_SPEED_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(
                    FRENZY_SPEED_ID, "froggy_frenzy_speed", FRENZY_SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        stopStalking();
        painCooldown = 40;
    }

    /** O grito da fase 2: volume 8 é ouvido de bem longe. */
    public void playScream() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.SCREAM.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
        vocalCooldown = 50; // não fala por cima do grito
    }

    // ----- pulos -----

    /** Durante um pulo de ataque ele não toma dano de queda. */
    public void setNoFallTicks(int ticks) {
        this.noFallTicks = ticks;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (noFallTicks > 0) return false;
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    /** Vira o corpo, a cabeça e o modelo de uma vez pra direção do alvo. */
    public void faceTarget(LivingEntity target) {
        faceDirection(target.getX() - this.getX(), target.getZ() - this.getZ());
    }

    public void faceDirection(double dx, double dz) {
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
        this.yHeadRot = yaw;
        this.yHeadRotO = yaw;
    }

    // ---------------- tick ----------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientTick();
            return;
        }

        stateTicks++;
        if (stalkTicks > 0) stalkTicks--;
        if (attackCooldown > 0) attackCooldown--;
        if (feedSearchCooldown > 0) feedSearchCooldown--;
        if (frenzyCooldown > 0) frenzyCooldown--;
        if (noFallTicks > 0) noFallTicks--;
        if (vocalCooldown > 0) vocalCooldown--;
        if (debugHunt && this.getTarget() == null && this.tickCount > 80 && this.tickCount % 10 == 0) {
            this.level().getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class,
                    this.getBoundingBox().inflate(48.0D)).stream().findFirst().ifPresent(this::setTarget);
        }
        cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));
        if ((debugHeldArm || debugArmless) && this.tickCount > 40 && this.tickCount % 10 == 0) {
            Player near = this.level().getNearestPlayer(this, 32.0D);
            if (near instanceof ServerPlayer sp) {
                if (debugHeldArm && getHeldArmOwner().isEmpty()) setHeldArm(sp);
                if (debugArmless && ArmLoss.canLoseArm(sp)) ArmLoss.removeArm(sp);
            }
        }

        // a vida também muda sem dano (cura, /summon, mundo recarregado)
        if (this.tickCount % 20 == 0) {
            updateGoreStage();
        }
        updateLocomotion();

        if (frenzyTicksLeft > 0) {
            frenzyTicksLeft--;
            // fase 2 dói: grita e reclama de dor enquanto corre
            if (--painCooldown <= 0) {
                vocalize(ModSounds.PAIN.get(), 2.5F);
                painCooldown = 50 + this.getRandom().nextInt(50);
            }
            if (frenzyTicksLeft == 0) {
                endFrenzy();
            }
        } else if (frenzyTiredTicksLeft > 0) {
            frenzyTiredTicksLeft--;
            if (frenzyTiredTicksLeft == 0) {
                AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) speed.removeModifier(FRENZY_TIRED_ID);
                this.entityData.set(DATA_TIRED, false);
            }
        }
    }

    /** Só no cliente: velocidade real (pra animação acompanhar) e poeira das patadas. */
    private void clientTick() {
        int st = this.entityData.get(DATA_STATE);
        if (st != lastClientState) {
            lastClientState = st;
            clientStateAge = 0;
        } else {
            clientStateAge++;
        }

        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        double v = Math.sqrt(dx * dx + dz * dz);
        clientGroundSpeed += (v - clientGroundSpeed) * 0.35D;
        if (Boolean.getBoolean("froggydude.debug") && this.tickCount % 10 == 0) {
            System.out.println("[FROGGYDEBUG] cliente: estado=" + getFroggyState() + " idade=" + clientStateAge
                    + " vel=" + String.format("%.3f", clientGroundSpeed) + " anim=" + String.format("%.2f", mainAnimSpeed())
                    + " correndo=" + isRunning() + " pos=" + String.format("%.1f %.1f %.1f", getX(), getY(), getZ()));
        }

        // cada pulinho do galope levanta terra (como no vídeo contra o AJ)
        if (isRunning() && this.onGround() && getFroggyState() == FroggyState.CHASE) {
            hopClock += mainAnimSpeed();
            if (hopClock >= 10.0D) {
                hopClock -= 10.0D;
                BlockState below = this.getBlockStateOn();
                if (!below.isAir()) {
                    BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, below);
                    for (int i = 0; i < 4; i++) {
                        this.level().addParticle(dust,
                                this.getX() + (this.getRandom().nextDouble() - 0.5D) * 0.6D, this.getY() + 0.1D,
                                this.getZ() + (this.getRandom().nextDouble() - 0.5D) * 0.6D,
                                (this.getRandom().nextDouble() - 0.5D) * 0.2D, 0.2D,
                                (this.getRandom().nextDouble() - 0.5D) * 0.2D);
                    }
                }
            }
        }
    }

    /**
     * Velocidade da animação principal. Correndo e andando, ela acompanha a
     * velocidade de verdade: caçando pra matar ele galopa rápido; seguindo de
     * longe pra apavorar, devagar; na fase 2, mais rápido que tudo.
     */
    public double mainAnimSpeed() {
        FroggyState s = getFroggyState();
        if ((s != FroggyState.CHASE && s != FroggyState.IDLE) || !isMovingSynced()
                || isFrenzyTired() || !getDebugAnim().isEmpty()) {
            return 1.0D;
        }
        if (isRunning()) {
            return Mth.clamp(clientGroundSpeed * 6.25D, 0.6D, 2.6D);
        }
        return Mth.clamp(clientGroundSpeed * 12.5D, 0.5D, 1.6D);
    }

    /**
     * Decide andando/correndo pela velocidade real, com folga (liga rápido,
     * desliga devagar). Assim a animação não fica trocando no meio da corrida.
     */
    private void updateLocomotion() {
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        boolean chasing = this.getTarget() != null || isFrenzyActive();

        if (speed > 0.02D) movingHold = 6;
        else if (movingHold > 0) movingHold--;
        if (chasing && speed > 0.10D) runningHold = 10;
        else if (runningHold > 0) runningHold--;

        boolean moving = movingHold > 0;
        boolean running = runningHold > 0 && moving;
        if (moving != this.entityData.get(DATA_MOVING)) this.entityData.set(DATA_MOVING, moving);
        if (running != this.entityData.get(DATA_RUNNING)) this.entityData.set(DATA_RUNNING, running);
    }

    private void endFrenzy() {
        this.entityData.set(DATA_FRENZY, false);
        this.frenzyCooldown = 1200 + this.getRandom().nextInt(600); // 60 a 90 s até a próxima
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(FRENZY_SPEED_ID);
        this.frenzyTiredTicksLeft = 60;
        this.entityData.set(DATA_TIRED, true);
        speed.addTransientModifier(new AttributeModifier(
                FRENZY_TIRED_ID, "froggy_frenzy_tired", -0.3D, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    // ---------------- dano / estágio de sangue / tipo de estrago ----------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        FroggyState state = getFroggyState();
        boolean wasFeeding = state == FroggyState.FEEDING;
        if (wasFeeding || state == FroggyState.ARM_EAT) {
            amount *= 1.5F; // mais vulnerável enquanto come
        }
        if (isFrenzyActive() && source.is(DamageTypeTags.IS_PROJECTILE)) {
            amount *= 0.15F; // quase à prova de flecha durante o frenesi
        }

        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof Player) {
            stopStalking(); // mexeu com ele: acabou o "só olhando"
        }
        boolean beforeRip = state != FroggyState.ARM_RIP || stateTicks < 22;
        if (hurt && state.isOnVictim() && beforeRip && amount >= 5.0F
                && source.getEntity() != null && source.getEntity() == getPinnedVictim()) {
            // pancada forte de quem está embaixo: ele é jogado pra trás e solta
            if (getPinnedVictim() instanceof ServerPlayer player) PinTracker.release(player);
            setPinnedVictim(null);
            setHeldArm(null);
            setFroggyState(FroggyState.CHASE);
            setAttackCooldown(20);
        }
        if (hurt) {
            updateGoreStage();
            if (source.is(DamageTypeTags.IS_FIRE)) {
                setSkinVariant(SkinVariant.BURNED);
            } else if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_EXPLOSION)) {
                setSkinVariant(SkinVariant.SMASHED);
            }
            if (wasFeeding) {
                // interrompido comendo: larga a vítima e revida na hora
                setFeedTarget(null);
                setFroggyState(FroggyState.CHASE);
                if (source.getEntity() instanceof LivingEntity attacker) {
                    setTarget(attacker);
                }
            }
        }
        return hurt;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinVariant", getSkinVariant().id);
        if (!getDebugAnim().isEmpty()) tag.putString("DebugAnim", getDebugAnim());
        if (debugHunt) tag.putBoolean("DebugHunt", true);
        if (debugAttack != null) tag.putString("DebugAttack", debugAttack.name());
        if (debugHeldArm) tag.putBoolean("DebugHeldArm", true);
        if (debugArmless) tag.putBoolean("DebugArmless", true);
        if (debugStalk) tag.putBoolean("DebugStalk", true);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String id = tag.getString("SkinVariant");
        for (SkinVariant v : SkinVariant.values()) {
            if (v.id.equals(id)) setSkinVariant(v);
        }
        updateGoreStage();
        this.entityData.set(DATA_DEBUG_ANIM, tag.getString("DebugAnim"));
        this.debugHunt = tag.getBoolean("DebugHunt");
        this.debugHeldArm = tag.getBoolean("DebugHeldArm");
        this.debugArmless = tag.getBoolean("DebugArmless");
        this.debugStalk = tag.getBoolean("DebugStalk");
        this.debugAttack = null;
        for (FroggyState st : FroggyState.values()) {
            if (st.name().equals(tag.getString("DebugAttack"))) this.debugAttack = st;
        }
    }

    private void updateGoreStage() {
        float pct = this.getHealth() / this.getMaxHealth();
        byte stage;
        if (pct > 0.75F) stage = 0;
        else if (pct > 0.5F) stage = 1;
        else if (pct > 0.25F) stage = 2;
        else stage = 3;
        if (stage != this.entityData.get(DATA_GORE_STAGE)) {
            this.entityData.set(DATA_GORE_STAGE, stage);
        }
    }

    // ---------------- GeckoLib: animações ----------------
    // Os nomes abaixo precisam bater com as animações do arquivo
    // assets/froggydude/animations/froggydude.animation.json

    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ANIM_RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ANIM_RUN_FRENZY = RawAnimation.begin().thenLoop("run_frenzy");
    private static final RawAnimation ANIM_TIRED = RawAnimation.begin().thenLoop("tired");
    private static final RawAnimation ANIM_FEED = RawAnimation.begin().thenLoop("feed");
    private static final RawAnimation ANIM_BITE =
            RawAnimation.begin().then("bite", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_WHIP =
            RawAnimation.begin().then("tongue_whip", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_GRAB =
            RawAnimation.begin().then("tongue_grab", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_CAPTURE =
            RawAnimation.begin().then("tongue_capture", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_JUMP_PIN =
            RawAnimation.begin().then("jump_pin", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_HIGH_JUMP =
            RawAnimation.begin().then("high_jump", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_LEAP =
            RawAnimation.begin().then("leap", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_PIN_HOLD = RawAnimation.begin().thenLoop("pin_hold");
    private static final RawAnimation ANIM_CONTORT =
            RawAnimation.begin().then("contort", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_SMASH = RawAnimation.begin().thenLoop("smash");
    private static final RawAnimation ANIM_SKY_DROP =
            RawAnimation.begin().then("sky_drop", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_ARM_RIP =
            RawAnimation.begin().then("arm_rip", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_ARM_EAT =
            RawAnimation.begin().then("arm_eat", Animation.LoopType.HOLD_ON_LAST_FRAME);

    // controlador separado só pro osso da língua (some quando não está atacando com ela)
    private static final RawAnimation TONGUE_HIDDEN = RawAnimation.begin().thenLoop("tongue_hidden");
    private static final RawAnimation TONGUE_WHIP =
            RawAnimation.begin().then("tongue_whip_ext", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation TONGUE_GRAB =
            RawAnimation.begin().then("tongue_grab_ext", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation TONGUE_CAPTURE =
            RawAnimation.begin().then("tongue_capture_ext", Animation.LoopType.HOLD_ON_LAST_FRAME);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new PacedAnimationController<>(this, "main", 3, this::mainPredicate)
                .setAnimationSpeedHandler(e -> e.mainAnimSpeed()));
        controllers.add(new AnimationController<>(this, "tongue", 0, this::tonguePredicate));
    }

    private PlayState mainPredicate(AnimationState<FroggydudeEntity> state) {
        String debug = getDebugAnim();
        if (!debug.isEmpty()) {
            return state.setAndContinue(RawAnimation.begin().thenLoop(debug));
        }
        switch (getFroggyState()) {
            case BITE:
                return state.setAndContinue(ANIM_BITE);
            case TONGUE_WHIP:
                return state.setAndContinue(ANIM_WHIP);
            case TONGUE_GRAB:
                return state.setAndContinue(ANIM_GRAB);
            case TONGUE_CAPTURE:
                return state.setAndContinue(ANIM_CAPTURE);
            case JUMP_PIN:
                return state.setAndContinue(ANIM_JUMP_PIN);
            case HIGH_JUMP:
                return state.setAndContinue(ANIM_HIGH_JUMP);
            case FEEDING:
                return state.setAndContinue(ANIM_FEED);
            case CONTORTING:
                return state.setAndContinue(ANIM_CONTORT);
            case LEAP:
                return state.setAndContinue(ANIM_LEAP);
            case PIN_HOLD:
                return state.setAndContinue(ANIM_PIN_HOLD);
            case SMASH:
                return state.setAndContinue(ANIM_SMASH);
            case SKY_DROP:
                return state.setAndContinue(ANIM_SKY_DROP);
            case ARM_RIP:
                return state.setAndContinue(ANIM_ARM_RIP);
            case ARM_EAT:
                return state.setAndContinue(ANIM_ARM_EAT);
            default:
                break;
        }
        if (isFrenzyTired()) {
            return state.setAndContinue(ANIM_TIRED);
        }
        if (isMovingSynced()) {
            // perseguindo rápido = de quatro (run); fora disso = walk
            if (!isRunning()) return state.setAndContinue(ANIM_WALK);
            return state.setAndContinue(isFrenzyActive() ? ANIM_RUN_FRENZY : ANIM_RUN);
        }
        return state.setAndContinue(ANIM_IDLE);
    }

    private PlayState tonguePredicate(AnimationState<FroggydudeEntity> state) {
        String debug = getDebugAnim();
        if (debug.startsWith("tongue_")) {
            // teste: DebugAnim "tongue_whip" também estica a língua, em loop
            return state.setAndContinue(RawAnimation.begin().thenLoop(debug + "_ext"));
        }
        switch (getFroggyState()) {
            case TONGUE_WHIP:
                return state.setAndContinue(TONGUE_WHIP);
            case TONGUE_GRAB:
                return state.setAndContinue(TONGUE_GRAB);
            case TONGUE_CAPTURE:
                return state.setAndContinue(TONGUE_CAPTURE);
            default:
                return state.setAndContinue(TONGUE_HIDDEN);
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ---------------- ações usadas pelas Goals ----------------

    /**
     * Sangue de verdade: respingos vermelhos e pedacinhos, no ponto da mordida
     * (entre a boca dele e a vítima). Nada de partícula em cima da câmera de
     * quem apanhou, senão tampa a visão.
     */
    public void spawnBlood(LivingEntity target, int amount) {
        if (this.level() instanceof ServerLevel server) {
            Vec3 from = target.position().add(0, target.getBbHeight() * 0.5D, 0);
            Vec3 to = this.position().add(0, this.getBbHeight() * 0.45D, 0);
            Vec3 at = from.add(to.subtract(from).scale(0.55D));
            server.sendParticles(BLOOD_DUST, at.x, at.y, at.z, amount, 0.2D, 0.2D, 0.2D, 0.0D);
            server.sendParticles(BLOOD_CHUNK, at.x, at.y, at.z, Math.max(1, amount / 2), 0.15D, 0.15D, 0.15D, 0.12D);
        }
    }

    /** Terminou de comer o braço: cura e fica com a boca suja. */
    public void finishEatingArm() {
        this.heal((float) (this.getMaxHealth() * 0.15D));
        setSkinVariant(SkinVariant.FED);
        setHeldArm(null);
        updateGoreStage();
    }

    /** Sangue saindo da boca enquanto mastiga. */
    public void spawnMouthBlood(int amount) {
        if (this.level() instanceof ServerLevel server) {
            float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 at = this.position().add(-Mth.sin(yaw) * 0.35D, this.getBbHeight() * 0.8D, Mth.cos(yaw) * 0.35D);
            server.sendParticles(BLOOD_DUST, at.x, at.y, at.z, amount, 0.12D, 0.1D, 0.12D, 0.0D);
            server.sendParticles(BLOOD_CHUNK, at.x, at.y, at.z, Math.max(1, amount / 3), 0.08D, 0.05D, 0.08D, 0.08D);
        }
    }

    public void eatVictim(LivingEntity victim) {
        spawnBlood(victim, 25);
        this.playSound(ModSounds.EAT.get(), 1.4F, 1.0F);
        victim.hurt(this.damageSources().mobAttack(this), Float.MAX_VALUE / 2F);
        this.heal((float) (this.getMaxHealth() * 0.20D));
        if (victim instanceof net.minecraft.world.entity.monster.Blaze) {
            requestPhase2(); // "freaking spicy"
        } else {
            setSkinVariant(SkinVariant.FED);
        }
    }
}
