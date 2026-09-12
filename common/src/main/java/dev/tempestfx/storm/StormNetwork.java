package dev.tempestfx.storm;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

/** Loader adapters install optional transport; this class never loads client implementation classes. */
public final class StormNetwork {
    private static BiConsumer<ServerPlayer, StormPacket> sender = (player, packet) -> {};
    private static Consumer<StormEvent> receiver = event -> {};
    private StormNetwork() {}
    public static void installServer(BiConsumer<ServerPlayer, StormPacket> value) { sender = value; }
    public static void installClient(Consumer<StormEvent> value) { receiver = value; }
    public static void receive(StormPacket packet) { receiver.accept(packet.event()); }
    public static void send(ServerPlayer player, StormEvent event) {
        if (player.connection.isAcceptingMessages()) sender.accept(player, new StormPacket(event));
    }
}
