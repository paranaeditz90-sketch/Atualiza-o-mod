package com.froggydude.client.cutscene;

import com.froggydude.init.ModEntityTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * O aldeão na sequência da zebra (a comida favorita dele, "mesmas animações"
 * do jogador): deitado de costas no lugar onde caiu, se debatendo, e os braços
 * (uma peça só, cruzada) e a cabeça somem no tick em que ele arranca - com os
 * tocos no lugar. Mesma geometria da vítima jogadora (VictimRender), sem
 * câmera de cinema: quem está perto vê de fora.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VillagerVictimRender {

    private static final class Saved {
        float bodyRot, bodyRotO, headRot, headRotO, xRot, xRotO, walkPos, walkSpeed, walkSpeedO;
        int deathTime;
    }

    private static final Map<Integer, Saved> PUSHED = new HashMap<>();

    private VillagerVictimRender() {
    }

    /** Deitado no golpe final (até sumir: morre deitado, sem a queda de lado da morte). */
    public static boolean lying(LivingEntity v) {
        ClientFinisher.Entry e = ClientFinisher.forVictim(v.getId());
        if (e == null) return false;
        if (!e.ended() || v.isDeadOrDying()) return true;
        var level = Minecraft.getInstance().level;
        return level != null && level.getGameTime() - e.endedAt < 10;
    }

    static ModelPart arms(VillagerModel<?> m) {
        return m.root().getChild("arms");
    }

    private static void headScale(VillagerModel<?> m, float s) {
        ModelPart head = m.getHead();
        head.xScale = s;
        head.yScale = s;
        head.zScale = s;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractVillager v) || !lying(v)) return;
        if (!(event.getRenderer().getModel() instanceof VillagerModel<?> m)) return;
        ClientFinisher.Entry e = ClientFinisher.forVictim(v.getId());
        float partial = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        Vec3 pos = v.getPosition(partial);
        Vec3 h = e.h;
        float t = e.t(partial);
        float thr = (v.isDeadOrDying() || e.ended()) ? 0F : VictimRender.thrash(t, e.skipLeft);
        float time = v.level().getGameTime() + partial;
        ps.pushPose();
        double lift = thr * 0.05D * Math.abs(Mth.sin(time * 1.9F));
        // de costas: o robe é mais grosso que o corpo do jogador (3,5 px de cada lado)
        ps.translate(e.anchor.x - pos.x, e.anchor.y - pos.y + 0.22D + lift, e.anchor.z - pos.z);
        ps.mulPose(new Quaternionf().rotationAxis(thr * 7F * Mth.DEG_TO_RAD * VictimRender.jitter(time, 2.3F),
                (float) h.x, 0F, (float) h.z));
        ps.translate(-h.x * 0.95D, 0D, -h.z * 0.95D);
        ps.mulPose(new Quaternionf().rotationTo(0F, 1F, 0F, (float) h.x, 0F, (float) h.z));

        Saved s = new Saved();
        s.bodyRot = v.yBodyRot;
        s.bodyRotO = v.yBodyRotO;
        s.headRot = v.yHeadRot;
        s.headRotO = v.yHeadRotO;
        s.xRot = v.getXRot();
        s.xRotO = v.xRotO;
        s.deathTime = v.deathTime;
        s.walkPos = VictimRender.getF(VictimRender.WALK_POS, v.walkAnimation);
        s.walkSpeed = VictimRender.getF(VictimRender.WALK_SPEED, v.walkAnimation);
        s.walkSpeedO = VictimRender.getF(VictimRender.WALK_SPEED_O, v.walkAnimation);
        PUSHED.put(v.getId(), s);
        float face = e.yaw + 180F;
        v.yBodyRot = v.yBodyRotO = face;
        // a cabeça virando de um lado pro outro, as pernas chutando (igual à zebra)
        v.yHeadRot = v.yHeadRotO = face + thr * 55F * VictimRender.jitter(time, 1.3F);
        v.setXRot(thr * 14F * VictimRender.jitter(time + 7F, 1.7F));
        v.xRotO = v.getXRot();
        VictimRender.setF(VictimRender.WALK_POS, v.walkAnimation, time * 1.15F);
        VictimRender.setF(VictimRender.WALK_SPEED, v.walkAnimation, thr * 0.85F);
        VictimRender.setF(VictimRender.WALK_SPEED_O, v.walkAnimation, thr * 0.85F);
        v.deathTime = 0;

        int miss = ClientFinisher.missing(e, partial);
        // os braços cruzados saem juntos, no tempo do braço direito (a tabela já começa nele)
        arms(m).visible = (miss & 2) == 0;
        // a cabeça some pela escala: a camada da profissão chama hatVisible(), que liga de novo
        // a cabeça pra pintar o chapéu (a aba de palha do fazendeiro ficava no chão sem cabeça)
        headScale(m, (miss & 4) != 0 ? 0F : 1F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPost(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity v = event.getEntity();
        Saved s = PUSHED.remove(v.getId());
        if (s == null) return;
        event.getPoseStack().popPose();
        v.yBodyRot = s.bodyRot;
        v.yBodyRotO = s.bodyRotO;
        v.yHeadRot = s.headRot;
        v.yHeadRotO = s.headRotO;
        v.setXRot(s.xRot);
        v.xRotO = s.xRotO;
        v.deathTime = s.deathTime;
        VictimRender.setF(VictimRender.WALK_POS, v.walkAnimation, s.walkPos);
        VictimRender.setF(VictimRender.WALK_SPEED, v.walkAnimation, s.walkSpeed);
        VictimRender.setF(VictimRender.WALK_SPEED_O, v.walkAnimation, s.walkSpeedO);
        if (event.getRenderer().getModel() instanceof VillagerModel<?> m) {
            arms(m).visible = true;
            headScale(m, 1F);
        }
    }

    /** Os tocos: dos dois lados do peito (onde os braços cruzados prendiam) e no pescoço. */
    public static final class StumpLayer<T extends AbstractVillager, M extends VillagerModel<T>> extends RenderLayer<T, M> {

        public StumpLayer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buffers, int light, T v, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!lying(v)) return;
            ClientFinisher.Entry e = ClientFinisher.forVictim(v.getId());
            int miss = ClientFinisher.missing(e, partialTick);
            if ((miss & 2) != 0) {
                Stumps.box(ps, buffers, light, 4.2F, 5.6F, 0.6F, 4.2F, -2.6F, 1.6F);
                Stumps.box(ps, buffers, light, -5.6F, -4.2F, 0.6F, 4.2F, -2.6F, 1.6F);
            }
            if ((miss & 4) != 0) Stumps.box(ps, buffers, light, -3.6F, 3.6F, -0.9F, 0.5F, -2.9F, 2.9F);
        }
    }
}
