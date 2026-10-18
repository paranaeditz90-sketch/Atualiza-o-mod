package com.froggydude.client;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * O bicho que ele está comendo cai de lado ("Taste testing Minecraft mobs": o
 * warden, o golem, o aldeão no chão com ele de bruços por cima), atravessado na
 * frente dele, o meio do corpo onde o bicho estava em pé - bem embaixo da boca
 * da flexão. Tomba em 5 ticks (acelerando, como quem desaba) e fica deitado até
 * sumir (morre comido ainda deitado, sem levantar pra fazer a queda da morte).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FeedVictimRender {

    private static final int FALL_TICKS = 5;

    private static final class Down {
        final int froggyId;
        final long since;
        /** Pra onde tombou (fica fixo mesmo se o Froggy se mexer). */
        Vec3 side;

        Down(int froggyId, long since) {
            this.froggyId = froggyId;
            this.since = since;
        }
    }

    private static final Map<Integer, Down> DOWN = new HashMap<>();
    /** Quem teve a pose empurrada neste quadro (o Post só desempilha esses). */
    private static final Map<Integer, Integer> PUSHED = new HashMap<>();

    private FeedVictimRender() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            DOWN.clear();
            return;
        }
        long now = mc.level.getGameTime();
        Set<Integer> keep = new HashSet<>();
        for (FroggydudeEntity f : mc.level.getEntitiesOfClass(FroggydudeEntity.class,
                mc.player.getBoundingBox().inflate(128D))) {
            int id = f.getFeedVictimId();
            if (id < 0 || f.getFroggyState() != FroggyState.FEEDING) continue;
            keep.add(id);
            DOWN.computeIfAbsent(id, k -> new Down(f.getId(), now));
        }
        // comido: morre deitado (a entidade some sozinha em 1 s)
        for (Map.Entry<Integer, Down> e : DOWN.entrySet()) {
            Entity v = mc.level.getEntity(e.getKey());
            if (v instanceof LivingEntity l && l.isDeadOrDying()) keep.add(e.getKey());
        }
        DOWN.keySet().retainAll(keep);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity v = event.getEntity();
        Down d = DOWN.get(v.getId());
        if (d == null) return;
        float partial = event.getPartialTick();
        if (d.side == null) {
            Entity f = v.level().getEntity(d.froggyId);
            Vec3 a = f == null ? Vec3.ZERO : v.position().subtract(f.position()).multiply(1, 0, 1);
            a = a.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : a.normalize();
            d.side = new Vec3(a.z, 0, -a.x);
        }
        float k = Mth.clamp((v.level().getGameTime() - d.since + partial) / FALL_TICKS, 0F, 1F);
        k *= k;
        double half = v.getBbHeight() * 0.5D * k;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-d.side.x * half, 0D, -d.side.z * half);
        Quaternionf full = new Quaternionf().rotationTo(0F, 1F, 0F, (float) d.side.x, 0F, (float) d.side.z);
        ps.mulPose(new Quaternionf().slerp(full, k));
        // a queda da morte (vanilla) por cima do tombo deixava ele de ponta-cabeça
        PUSHED.put(v.getId(), v.deathTime);
        v.deathTime = 0;
    }

    @SubscribeEvent
    public static void onPost(RenderLivingEvent.Post<?, ?> event) {
        Integer death = PUSHED.remove(event.getEntity().getId());
        if (death == null) return;
        event.getEntity().deathTime = death;
        event.getPoseStack().popPose();
    }
}
