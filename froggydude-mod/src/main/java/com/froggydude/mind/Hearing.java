package com.froggydude.mind;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * (v0.7.68, a Mente) A audição do FroggyDude: os mesmos "barulhos" que o Warden ouve (os eventos de vibração do jogo).
 * Passos (agachado bem menos), porta e alçapão, baú, bloco quebrando e posto, comendo e bebendo, flecha, splash, cair.
 * Cada barulho tem até onde se ouve; ele vira a cabeça e vai ver (Mind.hear).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Hearing {

    private Hearing() {
    }

    /** Até onde se ouve cada barulho (blocos); 0 = ele não liga. */
    static double radius(GameEvent e) {
        if (e == GameEvent.STEP) return 9.0D;
        if (e == GameEvent.BLOCK_OPEN || e == GameEvent.BLOCK_CLOSE) return 16.0D;
        if (e == GameEvent.CONTAINER_OPEN || e == GameEvent.CONTAINER_CLOSE) return 14.0D;
        if (e == GameEvent.BLOCK_DESTROY) return 18.0D;
        if (e == GameEvent.BLOCK_PLACE) return 12.0D;
        if (e == GameEvent.EAT || e == GameEvent.DRINK) return 8.0D;
        if (e == GameEvent.PROJECTILE_SHOOT) return 20.0D;
        if (e == GameEvent.SPLASH || e == GameEvent.SWIM) return 12.0D;
        if (e == GameEvent.HIT_GROUND) return 10.0D;
        if (e == GameEvent.ENTITY_DAMAGE) return 14.0D;
        if (e == GameEvent.EXPLODE) return 64.0D;
        return 0.0D;
    }

    static String name(GameEvent e) {
        if (e == GameEvent.STEP) return "passos";
        if (e == GameEvent.BLOCK_OPEN || e == GameEvent.BLOCK_CLOSE) return "uma porta";
        if (e == GameEvent.CONTAINER_OPEN || e == GameEvent.CONTAINER_CLOSE) return "um baú";
        if (e == GameEvent.BLOCK_DESTROY) return "um bloco quebrando";
        if (e == GameEvent.BLOCK_PLACE) return "um bloco sendo posto";
        if (e == GameEvent.EAT || e == GameEvent.DRINK) return "alguém comendo";
        if (e == GameEvent.PROJECTILE_SHOOT) return "uma flecha";
        if (e == GameEvent.SPLASH || e == GameEvent.SWIM) return "água mexendo";
        if (e == GameEvent.HIT_GROUND) return "uma queda";
        if (e == GameEvent.ENTITY_DAMAGE) return "alguém se machucando";
        if (e == GameEvent.EXPLODE) return "uma explosão";
        return "um barulho";
    }

    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        GameEvent ge = event.getVanillaEvent();
        if (radius(ge) <= 0.0D) return;
        // quem fez o barulho; se o jogo não diz (ex.: alguém se machucou de queda/fogo), é quem está ali (o gemido sai dela)
        ServerPlayer p = event.getCause() instanceof ServerPlayer sp ? sp : null;
        if (p == null) {
            Vec3 at = event.getEventPosition();
            for (ServerPlayer o : sl.players()) {
                if (o.position().distanceToSqr(at) <= 2.25D) {
                    p = o;
                    break;
                }
            }
        }
        if (p == null) return;
        if (p.isCreative() || p.isSpectator()) return;
        GameEvent e = event.getVanillaEvent();
        double r = radius(e);
        if (r <= 0.0D) return;
        // agachado e devagar: quase nada (como pro Warden)
        if (p.isCrouching() && (e == GameEvent.STEP || e == GameEvent.HIT_GROUND)) r *= 0.3D;
        if (e == GameEvent.STEP && p.isSprinting()) r *= 1.5D;
        Vec3 pos = event.getEventPosition();
        for (FroggydudeEntity f : sl.getEntitiesOfClass(FroggydudeEntity.class, new AABB(pos, pos).inflate(r))) {
            if (f.isAlive() && f.position().distanceTo(pos) <= r) f.mind().hear(p, pos, r, name(e));
        }
    }
}
