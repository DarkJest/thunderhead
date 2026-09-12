package dev.tempestfx.storm;

import dev.tempestfx.math.Vec3d;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StormPacket(StormEvent event) implements CustomPacketPayload {
    public static final Type<StormPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("tempestfx", "storm_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StormPacket> CODEC = StreamCodec.of((buffer, packet) -> {
        var e = packet.event();
        buffer.writeVarInt(StormEvent.VERSION); buffer.writeLong(e.id()); buffer.writeLong(e.seed()); buffer.writeLong(e.startTick());
        buffer.writeUtf(e.dimension(), 128); buffer.writeByte(e.kind());
        vector(buffer, e.origin()); vector(buffer, e.target()); buffer.writeFloat(e.intensity()); buffer.writeByte(e.returns()); buffer.writeInt(e.nativeEntityId());
    }, buffer -> {
        if (buffer.readVarInt() != StormEvent.VERSION) throw new IllegalArgumentException("Unsupported storm protocol");
        return new StormPacket(new StormEvent(buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readUtf(128),
            buffer.readByte(), vector(buffer), vector(buffer), buffer.readFloat(), buffer.readUnsignedByte(), buffer.readInt()));
    });
    private static void vector(RegistryFriendlyByteBuf b, Vec3d p) { b.writeDouble(p.x()); b.writeDouble(p.y()); b.writeDouble(p.z()); }
    private static Vec3d vector(RegistryFriendlyByteBuf b) { return new Vec3d(b.readDouble(), b.readDouble(), b.readDouble()); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
