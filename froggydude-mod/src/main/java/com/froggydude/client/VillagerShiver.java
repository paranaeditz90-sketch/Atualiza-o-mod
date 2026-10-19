package com.froggydude.client;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Aldeão (e mercador) com o FroggyDude a 16 blocos ou menos: treme de medo,
 * mais forte quanto mais perto ele está (o par do entity.VillagerFear, que faz
 * eles fugirem).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VillagerShiver {

    private static final double RANGE = 16.0D;
    /** O quanto o corpo sacode (blocos) com ele colado; some até os 16 blocos. */
    private static final float AMPLITUDE = 0.05F;
    private static final Map<Integer, float[]> PUSHED = new HashMap<>();

    private VillagerShiver() {
    }

    @SubscribeEvent
    public static void onPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        if (!(e instanceof AbstractVillager) || e.isDeadOrDying() || e.isSleeping()) return;
        double near = RANGE;
        for (FroggydudeEntity f : e.level().getEntitiesOfClass(FroggydudeEntity.class, e.getBoundingBox().inflate(RANGE))) {
            near = Math.min(near, f.distanceTo(e));
        }
        if (near >= RANGE) return;
        float k = AMPLITUDE * (float) (1.0D - near / RANGE * 0.7D);
        float t = e.tickCount + event.getPartialTick();
        int id = e.getId();
        // tremedeira: rápido e sem ritmo (duas senoides fora de fase)
        float dx = (Mth.sin(t * 5.3F + id) + 0.5F * Mth.sin(t * 11.7F + id * 0.37F)) * k / 1.5F;
        float dz = (Mth.cos(t * 6.1F + id * 1.3F) + 0.5F * Mth.cos(t * 13.1F + id)) * k / 1.5F;
        event.getPoseStack().translate(dx, 0F, dz);
        PUSHED.put(id, new float[]{dx, dz});
    }

    @SubscribeEvent
    public static void onPost(RenderLivingEvent.Post<?, ?> event) {
        float[] d = PUSHED.remove(event.getEntity().getId());
        if (d != null) event.getPoseStack().translate(-d[0], 0F, -d[1]);
    }
}
