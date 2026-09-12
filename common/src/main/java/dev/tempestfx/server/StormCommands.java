package dev.tempestfx.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

public final class StormCommands {
    private StormCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("thunderstorm").requires(source -> source.hasPermission(2))
            .then(Commands.literal("status").executes(context -> {
                var s = TempestFxServer.config();
                context.getSource().sendSuccess(() -> Component.literal("Thunderhead: cells=" + s.storm.enabled + ", extra ground strikes=" + s.storm.groundStrikes
                    + ", conduction=" + s.nearMiss.physicalConduction + ", experimental ball lightning=" + s.ballLightning.enabled), false); return 1;
            }))
            .then(Commands.literal("strike").then(Commands.argument("position", BlockPosArgument.blockPos()).executes(context -> {
                var at = BlockPosArgument.getLoadedBlockPos(context, "position");
                var level = context.getSource().getLevel();
                var bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                bolt.moveTo(at.getX()+.5, at.getY(), at.getZ()+.5);
                return level.addFreshEntity(bolt) ? 1 : 0;
            }))));
    }
}
