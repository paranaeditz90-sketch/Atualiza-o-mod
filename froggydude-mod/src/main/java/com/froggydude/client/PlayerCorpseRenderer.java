package com.froggydude.client;

import com.froggydude.client.cutscene.Stumps;
import com.froggydude.client.gore.BloodStains;
import com.froggydude.config.FroggyClientConfig;
import com.froggydude.config.GoreLevel;
import com.froggydude.entity.Finisher;
import com.froggydude.entity.PlayerCorpse;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * O que sobrou do jogador no Extremo: o corpo com a skin dele, sem braços e
 * sem cabeça (tocos vermelhos), deitado de costas onde morreu. O FroggyDude
 * come (as pernas somem primeiro), joga pro alto e sacode na boca; o corpo
 * gira (flip no eixo do comprimento, roll de ponta-cabeça) e assenta de costas
 * ou de bruços (PlayerCorpse faz a física).
 *
 * Quem joga no Desligado ou no Leve não vê o corpo.
 */
public class PlayerCorpseRenderer extends EntityRenderer<PlayerCorpse> {

    private final PlayerModel<AbstractClientPlayer> wide;
    private final PlayerModel<AbstractClientPlayer> slim;

    public PlayerCorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0.6F;
    }

    private record Skin(ResourceLocation texture, boolean slim) {
    }

    private static Skin skin(PlayerCorpse corpse) {
        UUID owner = corpse.getOwner().orElse(null);
        if (owner == null) return new Skin(DefaultPlayerSkin.getDefaultSkin(), false);
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(owner);
        if (info != null) return new Skin(info.getSkinLocation(), "slim".equals(info.getModelName()));
        return new Skin(DefaultPlayerSkin.getDefaultSkin(owner), "slim".equals(DefaultPlayerSkin.getSkinModelName(owner)));
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerCorpse corpse) {
        return skin(corpse).texture();
    }

    @Override
    public void render(PlayerCorpse corpse, float entityYaw, float partial, PoseStack ps, MultiBufferSource buffers,
                       int light) {
        GoreLevel gore = FroggyClientConfig.gore();
        if (gore.ordinal() < GoreLevel.MEDIUM.ordinal()) return;
        Skin skin = skin(corpse);
        PlayerModel<AbstractClientPlayer> m = skin.slim() ? slim : wide;

        float headYaw = corpse.getHeadYaw();
        Vec3 h = Finisher.dir(headYaw);
        Vec3 l = Finisher.left(h);
        float flip = Mth.rotLerp(partial, corpse.flipO, corpse.getFlip());
        float roll = Mth.rotLerp(partial, corpse.rollO, corpse.getRoll());

        ps.pushPose();
        // a entidade fica no meio do corpo, encostada no chão
        ps.translate(0D, 0.09D, 0D);
        // ponta-cabeça (em volta do lado) e de bruços/de costas (em volta do comprimento)
        ps.mulPose(new Quaternionf().rotationAxis(roll * Mth.DEG_TO_RAD, (float) l.x, 0F, (float) l.z));
        ps.mulPose(new Quaternionf().rotationAxis(flip * Mth.DEG_TO_RAD, (float) h.x, 0F, (float) h.z));
        // em pé virado pra -H, tomba pra trás: cabeça pra H, cara pro céu (igual à vítima da cutscene)
        ps.mulPose(new Quaternionf().rotationTo(0F, 1F, 0F, (float) h.x, 0F, (float) h.z));
        ps.mulPose(Axis.YP.rotationDegrees(-headYaw));
        ps.translate(0D, -0.9D, 0D);
        // o mesmo que o LivingEntityRenderer/PlayerRenderer fazem com o modelo
        ps.scale(-1F, -1F, 1F);
        ps.scale(0.9375F, 0.9375F, 0.9375F);
        ps.translate(0D, -1.501D, 0D);

        pose(m, corpse);
        int ov = OverlayTexture.NO_OVERLAY;
        // a skin como é (o sangue vem da camada de manchas e dos tocos): tingida de vermelho, no
        // meio da poça, o corpo sumia de longe
        m.renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(skin.texture())), light, ov,
                1F, 1F, 1F, 1F);

        ResourceLocation stains = corpse.getOwner().map(BloodStains::textureFor).orElse(null);
        if (stains != null) {
            boolean[] base = {m.head.visible, m.body.visible, m.rightArm.visible, m.leftArm.visible, m.rightLeg.visible, m.leftLeg.visible};
            m.head.visible = m.body.visible = m.rightArm.visible = m.leftArm.visible = m.rightLeg.visible = m.leftLeg.visible = false;
            m.hat.visible = base[0];
            m.jacket.visible = base[1];
            m.rightSleeve.visible = base[2];
            m.leftSleeve.visible = base[3];
            m.rightPants.visible = base[4];
            m.leftPants.visible = base[5];
            m.renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(stains)), light, ov, 1F, 1F, 1F, 1F);
        }

        // os tocos (no espaço do tronco)
        int miss = corpse.getMissing();
        float eaten = corpse.getEaten();
        ps.pushPose();
        m.body.translateAndRotate(ps);
        if ((miss & 1) != 0) Stumps.leftShoulder(ps, buffers, light);
        if ((miss & 2) != 0) Stumps.rightShoulder(ps, buffers, light);
        if ((miss & 4) != 0) Stumps.neck(ps, buffers, light);
        if (eaten > 0.2F) Stumps.hip(ps, buffers, light, true);
        if (eaten > 0.45F) Stumps.hip(ps, buffers, light, false);
        if (eaten > 0.7F) {
            // comeu até a barriga: o fundo do tronco é carne
            Stumps.box(ps, buffers, light, -4.1F, 4.1F, 8.5F, 12.2F, -2.1F, 2.1F);
        }
        ps.popPose();
        ps.popPose();
        super.render(corpse, entityYaw, partial, ps, buffers, light);
    }

    /** Mole, braços e pernas meio abertos, cabeça (se tivesse) caída de lado. */
    private static void pose(PlayerModel<AbstractClientPlayer> m, PlayerCorpse corpse) {
        int miss = corpse.getMissing();
        float eaten = corpse.getEaten();
        m.setAllVisible(true);
        m.leftArm.visible = m.leftSleeve.visible = (miss & 1) == 0;
        m.rightArm.visible = m.rightSleeve.visible = (miss & 2) == 0;
        m.head.visible = m.hat.visible = (miss & 4) == 0;
        m.leftLeg.visible = m.leftPants.visible = eaten <= 0.2F;
        m.rightLeg.visible = m.rightPants.visible = eaten <= 0.45F;
        m.jacket.visible = eaten <= 0.7F;
        set(m.rightArm, m.rightSleeve, 0F, 0F, 0.32F);
        set(m.leftArm, m.leftSleeve, 0F, 0F, -0.32F);
        set(m.rightLeg, m.rightPants, 0F, 0F, 0.09F);
        set(m.leftLeg, m.leftPants, 0F, 0F, -0.09F);
        set(m.head, m.hat, 0F, 0.5F, 0F);
    }

    private static void set(ModelPart part, ModelPart over, float x, float y, float z) {
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
        over.copyFrom(part);
    }
}
