package dev.tempestfx.client;
import dev.tempestfx.math.Vec3d;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One local, at-most-64-block ray per admitted sound voice. */
public final class AcousticEnvironment {
    private AcousticEnvironment() {}
    public static float transmission(Vec3d source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 1;
        Vec3 start = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 delta = new Vec3(source.x(), source.y(), source.z()).subtract(start);
        Vec3 end = start.add(delta.normalize().scale(Math.min(64, delta.length())));
        var hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS ? 1f : .45f;
    }
}
