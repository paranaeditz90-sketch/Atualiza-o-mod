package com.becomelm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;

/**
 * /becomelm gallop | stand | human - troca o próprio modo pelo chat (o mesmo da roda;
 * serve de reserva se a roda não abrir no celular). Com nome de jogador (só op):
 * /becomelm gallop Amigo.
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LmCommand {

    private LmCommand() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("becomelm");
        for (LmMode mode : LmMode.values()) {
            root.then(Commands.literal(mode.id)
                    .executes(ctx -> apply(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException()), mode))
                    .then(Commands.argument("players", EntityArgument.players())
                            .requires(src -> src.hasPermission(2))
                            .executes(ctx -> apply(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"), mode))));
        }
        d.register(root);
    }

    private static int apply(CommandSourceStack src, Collection<ServerPlayer> players, LmMode mode) {
        for (ServerPlayer p : players) LmModes.set(p, mode);
        src.sendSuccess(() -> Component.translatable("becomelm.command.done", players.size(),
                Component.translatable("becomelm.mode.toast." + mode.id)), false);
        return players.size();
    }
}
