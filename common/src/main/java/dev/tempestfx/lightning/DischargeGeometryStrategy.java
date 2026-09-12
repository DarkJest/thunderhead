package dev.tempestfx.lightning;

import dev.tempestfx.api.LightningKind;
import dev.tempestfx.math.Bounds3d;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed canonical backbone and independently seeded attached forks. Quality only decimates the
 * generated tree; it never changes the backbone's random walk or moves the contact point.
 * This is a bounded stochastic approximation, not an electrostatic field solver.
 */
public final class DischargeGeometryStrategy {
    private static final int BACKBONE_GENERATIONS = 7;
    private final MidpointDisplacementStrategy paths = new MidpointDisplacementStrategy();

    public LightningGeometry generate(LightningBolt bolt, LightningKind kind, LightningLod lod) {
        double length = bolt.start().distanceTo(bolt.end());
        double wander = switch (kind) { case INTRACLOUD -> .12; case INTERCLOUD -> .045; default -> .07; };
        var main = path(bolt.start(), bolt.end(), bolt.seed(), BACKBONE_GENERATIONS, length * wander);
        List<LightningBranch> branches = new ArrayList<>();
        int step = switch (lod) { case FULL -> 1; case MEDIUM -> 2; case DISTANT -> 4; case ATMOSPHERIC -> 8; };
        step = Math.max(step, 1 << Math.max(0, BACKBONE_GENERATIONS - bolt.config().generations()));
        while ((main.size() + step - 1) / step > bolt.config().maxSegments()) step *= 2;
        branches.add(convert(main, 0, bolt.seed(), 0, 1, 1, step));
        int budget = bolt.config().maxSegments() - branches.getFirst().segments().size();
        double forkChance = bolt.config().branchProbability() * switch (kind) {
            case POSITIVE_GROUND -> .5; case INTRACLOUD -> 2.2; case INTERCLOUD -> 1.2; default -> 1.7;
        };
        if (lod != LightningLod.ATMOSPHERIC) {
            for (int i = 8; i < main.size() - 8 && budget >= 16; i += 8) {
                if (i % step != 0) continue; // A decimated tree may only fork at a retained vertex.
                long forkSeed = StrikeSeed.derive(bolt.seed(), 0x7400 + i);
                if (StrikeSeed.unit(forkSeed, 1) > forkChance) continue;
                double along = i / (double) main.size();
                Vec3d origin = main.get(i).start();
                Vec3d forward = main.get(i).end().subtract(origin).normalize();
                Vec3d axis = Math.abs(forward.dot(Vec3d.UP)) < .85 ? Vec3d.UP : new Vec3d(1, 0, 0);
                Vec3d side = forward.cross(axis).normalize();
                Vec3d other = forward.cross(side).normalize();
                double angle = StrikeSeed.unit(forkSeed, 2) * Math.PI * 2;
                Vec3d radial = side.scale(Math.cos(angle)).add(other.scale(Math.sin(angle)));
                double reach = length * (1 - along) * (.18 + .18 * StrikeSeed.unit(forkSeed, 3));
                Vec3d tip = origin.add(forward.scale(reach * .7)).add(radial.scale(reach * .7));
                if (kind.contactsGround()) tip = new Vec3d(tip.x(), Math.max(bolt.end().y() + 2, tip.y()), tip.z());
                if (tip.distanceTo(origin) < .1) continue;
                var fork = path(origin, tip, forkSeed, 4, Math.max(.01, reach * .1));
                var branch = convert(fork, 1, forkSeed, along, Math.min(.99, along + reach / length), .5, Math.min(4, step));
                branches.add(branch); budget -= branch.segments().size();
            }
        }
        Bounds3d bounds = Bounds3d.empty();
        for (var branch : branches) for (var segment : branch.segments()) {
            bounds = bounds.include(segment.start()).include(segment.end());
        }
        return new LightningGeometry(branches, bounds, bolt.seed());
    }

    private List<LightningSegment> path(Vec3d start, Vec3d end, long seed, int generations, double displacement) {
        var config = new LightningGenerationConfig(generations, Math.max(.01, displacement), .62, 0,
            .75, .5, .3, .6, .1, 0, 0, 0, 0, 256);
        return paths.generate(new LightningBolt(start, end, seed, 1, config)).branches().getFirst().segments();
    }

    private LightningBranch convert(List<LightningSegment> source, int depth, long seed, double alongStart,
                                    double alongEnd, double intensity, int step) {
        List<LightningSegment> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i += step) {
            int end = Math.min(source.size(), i + step);
            double a = i / (double) source.size(), b = end / (double) source.size();
            // Visible core radius is a rendering approximation; the halo remains a separate layer.
            double width = depth == 0 ? .055 : .027;
            result.add(new LightningSegment(source.get(i).start(), source.get(end - 1).end(), depth,
                width * (1 - depth * .8 * a), width * (1 - depth * .8 * b), intensity,
                alongStart + (alongEnd - alongStart) * a, alongStart + (alongEnd - alongStart) * b, -1L));
        }
        return new LightningBranch(depth, seed, result);
    }
}
