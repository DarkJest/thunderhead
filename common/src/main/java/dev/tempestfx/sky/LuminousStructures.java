package dev.tempestfx.sky;

import dev.tempestfx.lightning.LightningSegment;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Builds the two structures procedurally, from a seed and nothing else.
 *
 * <p>Deliberately not the bolt generator. Midpoint displacement produces a wandering conducting
 * channel, which is right for lightning and wrong for these: a sprite is a <em>curtain</em> of
 * roughly parallel columns with tendrils combed downward and outward, and a jet is a <em>cone</em>
 * that splits as it climbs. Both are described by their morphology rather than discovered by
 * subdivision, so they are built directly - and the result is far cheaper than a bolt, a couple of
 * hundred segments against several thousand.
 *
 * <p>The reference for the sprite is the standard "carrot" morphology: a diffuse head, a short bright
 * body, and long tendrils that spread as they descend, repeated across several elements of differing
 * size so the cluster does not read as one stamped shape.
 */
public final class LuminousStructures {
    /** Half-width of a sprite body column, in blocks, before element scaling. */
    private static final double SPRITE_BODY_HALF_WIDTH = 1.7;
    /** Segments per tendril. Enough to curve, few enough to stay cheap. */
    private static final int TENDRIL_SEGMENTS = 9;
    private static final int JET_TRUNK_SEGMENTS = 7;
    private static final int JET_BRANCH_SEGMENTS = 6;

    private LuminousStructures() {}

    /** Builds the structure a profile describes, at {@code anchor}. */
    public static LuminousStructure create(LuminousProfile profile, Vec3d anchor, long seed, double scale) {
        return profile.type().climbs()
            ? jet(profile, anchor, seed, scale)
            : sprite(profile, anchor, seed, scale);
    }

    // ------------------------------------------------------------------ red sprite

    private static LuminousStructure sprite(LuminousProfile profile, Vec3d anchor, long seed, double scale) {
        SplittableRandom random = new SplittableRandom(seed);
        double height = profile.height() * scale * (0.82 + random.nextDouble() * 0.4);
        double width = height * profile.widthRatio() * (0.8 + random.nextDouble() * 0.45);

        List<LightningSegment> filaments = new ArrayList<>();
        List<LightningSegment> wisps = new ArrayList<>();
        List<LuminousStructure.LuminousGlow> glows = new ArrayList<>();

        int elements = 2 + random.nextInt(4);
        for (int index = 0; index < elements; index++) {
            // Spread wide across the curtain and shallow in depth: a sprite cluster is a sheet seen
            // edge-on more often than a ball, and spreading it evenly in all three axes loses that.
            double offsetX = signed(random) * width * 0.45;
            double offsetZ = signed(random) * width * 0.18;
            double elementScale = 0.6 + random.nextDouble() * 0.55;
            Vec3d top = anchor.add(offsetX, -random.nextDouble() * height * 0.14, offsetZ);

            spriteElement(filaments, wisps, glows, top, anchor.y(), height, width, elementScale, random);
        }

        // One wide halo behind the whole cluster, which is what carries at distance.
        glows.add(new LuminousStructure.LuminousGlow(
            anchor.add(0, -height * 0.28, 0), width * 0.85, 0.55f, 0.05));
        return new LuminousStructure(filaments, wisps, glows, anchor, height, width);
    }

    private static void spriteElement(List<LightningSegment> filaments, List<LightningSegment> wisps,
                                      List<LuminousStructure.LuminousGlow> glows, Vec3d top, double anchorY,
                                      double height, double width, double elementScale,
                                      SplittableRandom random) {
        double bodyHeight = height * 0.28 * elementScale;
        double bodyWidth = SPRITE_BODY_HALF_WIDTH * elementScale * (height / 190.0);

        // The head: the brightest, most diffuse part, and the reason the whole thing reads as one
        // object rather than as a bundle of threads.
        glows.add(new LuminousStructure.LuminousGlow(
            top.add(0, -bodyHeight * 0.35, 0), width * 0.2 * elementScale, 1f,
            along(top.y() - bodyHeight * 0.35, anchorY, height)));

        Vec3d bodyBottom = column(filaments, top, bodyHeight, bodyWidth, anchorY, height, random);

        int tendrils = 3 + random.nextInt(5);
        for (int index = 0; index < tendrils; index++) {
            tendril(filaments, bodyBottom, bodyWidth, height, width, elementScale, anchorY, random, 0);
        }

        // The faint cool fringe reaching above the head. Short, thin and barely there, but it is the
        // detail that makes a sprite look photographed rather than drawn.
        int wispCount = 1 + random.nextInt(3);
        for (int index = 0; index < wispCount; index++) {
            double length = height * (0.07 + random.nextDouble() * 0.13);
            Vec3d start = top.add(signed(random) * width * 0.1, 0, signed(random) * width * 0.06);
            Vec3d end = start.add(signed(random) * width * 0.12, length, signed(random) * width * 0.12);
            wisps.add(new LightningSegment(start, end, 0, bodyWidth * 0.45, bodyWidth * 0.05, 1,
                0, 0.02, random.nextLong()));
        }
    }

    /** The short bright column under a sprite head. Returns where it ends. */
    private static Vec3d column(List<LightningSegment> output, Vec3d top, double bodyHeight, double bodyWidth,
                                double anchorY, double height, SplittableRandom random) {
        int steps = 4;
        Vec3d current = top;
        for (int step = 0; step < steps; step++) {
            double t0 = step / (double) steps;
            double t1 = (step + 1) / (double) steps;
            Vec3d next = top.add(signed(random) * bodyWidth * 0.8, -bodyHeight * t1, signed(random) * bodyWidth * 0.8);
            if (next.distanceSquaredTo(current) < 1.0e-6) continue;
            output.add(new LightningSegment(current, next, 0,
                bodyWidth * (1.0 - 0.25 * t0), bodyWidth * (1.0 - 0.25 * t1), 1,
                along(current.y(), anchorY, height), along(next.y(), anchorY, height), random.nextLong()));
            current = next;
        }
        return current;
    }

    /**
     * One tendril, combed outward as it falls.
     *
     * <p>The outward drift is what makes a cluster read as a curtain: real tendrils splay away from
     * the axis of their element rather than hanging plumb, and a bundle of parallel verticals looks
     * like a barcode.
     */
    private static void tendril(List<LightningSegment> output, Vec3d origin, double bodyWidth, double height,
                                double width, double elementScale, double anchorY, SplittableRandom random,
                                int depth) {
        double length = height * (0.3 + random.nextDouble() * 0.45) * elementScale * (depth == 0 ? 1 : 0.55);
        double driftX = signed(random) * width * (0.12 + random.nextDouble() * 0.22);
        double driftZ = signed(random) * width * 0.1;
        double startWidth = bodyWidth * (depth == 0 ? 0.55 : 0.3);

        Vec3d current = origin;
        int branchAt = 3 + random.nextInt(3);
        for (int step = 0; step < TENDRIL_SEGMENTS; step++) {
            double t0 = step / (double) TENDRIL_SEGMENTS;
            double t1 = (step + 1) / (double) TENDRIL_SEGMENTS;
            // Drift accelerates downward, so the tendrils fan out rather than lean.
            Vec3d next = origin.add(
                driftX * t1 * t1 + signed(random) * width * 0.012,
                -length * t1,
                driftZ * t1 * t1 + signed(random) * width * 0.012);
            if (next.distanceSquaredTo(current) < 1.0e-6) continue;
            output.add(new LightningSegment(current, next, depth,
                Math.max(0.01, startWidth * (1 - t0 * 0.92)),
                Math.max(0.008, startWidth * (1 - t1 * 0.92)), 1,
                along(current.y(), anchorY, height), along(next.y(), anchorY, height), random.nextLong()));
            if (depth == 0 && step == branchAt && random.nextDouble() < 0.55) {
                tendril(output, next, bodyWidth, height, width, elementScale, anchorY, random, 1);
            }
            current = next;
        }
    }

    // ------------------------------------------------------------------ blue jet

    private static LuminousStructure jet(LuminousProfile profile, Vec3d anchor, long seed, double scale) {
        SplittableRandom random = new SplittableRandom(seed);
        double height = profile.height() * scale * (0.85 + random.nextDouble() * 0.4);
        double width = height * profile.widthRatio();

        List<LightningSegment> filaments = new ArrayList<>();
        List<LuminousStructure.LuminousGlow> glows = new ArrayList<>();

        // The trunk: narrow where it leaves the cloud, widening as the air thins.
        double trunkHeight = height * 0.55;
        double baseWidth = width * 0.05;
        Vec3d current = anchor;
        double lean = signed(random) * 0.12;
        for (int step = 0; step < JET_TRUNK_SEGMENTS; step++) {
            double t0 = step / (double) JET_TRUNK_SEGMENTS;
            double t1 = (step + 1) / (double) JET_TRUNK_SEGMENTS;
            Vec3d next = anchor.add(lean * height * t1 + signed(random) * width * 0.03,
                trunkHeight * t1, lean * height * t1 * 0.5 + signed(random) * width * 0.03);
            filaments.add(new LightningSegment(current, next, 0,
                baseWidth * (0.6 + 1.5 * t0), baseWidth * (0.6 + 1.5 * t1), 1,
                t0 * 0.55, t1 * 0.55, random.nextLong()));
            current = next;
        }
        glows.add(new LuminousStructure.LuminousGlow(anchor.add(0, height * 0.12, 0), width * 0.3, 0.8f, 0.05));
        glows.add(new LuminousStructure.LuminousGlow(current, width * 0.5, 1f, 0.5));

        // The crown: the cone splits near the top and the branches fade out well short of sprite
        // altitude, which is what keeps a jet a jet and not a very tall bolt.
        int branches = 3 + random.nextInt(3);
        for (int index = 0; index < branches; index++) {
            jetBranch(filaments, glows, current, anchor, height, width, baseWidth * 1.9, random, 0);
        }
        return new LuminousStructure(filaments, List.of(), glows, anchor, height, width);
    }

    private static void jetBranch(List<LightningSegment> output, List<LuminousStructure.LuminousGlow> glows,
                                  Vec3d origin, Vec3d anchor, double height, double width, double startWidth,
                                  SplittableRandom random, int depth) {
        double length = height * (0.2 + random.nextDouble() * 0.22) * (depth == 0 ? 1 : 0.55);
        double spreadX = signed(random) * width * (0.35 + random.nextDouble() * 0.4);
        double spreadZ = signed(random) * width * (0.35 + random.nextDouble() * 0.4);

        Vec3d current = origin;
        for (int step = 0; step < JET_BRANCH_SEGMENTS; step++) {
            double t0 = step / (double) JET_BRANCH_SEGMENTS;
            double t1 = (step + 1) / (double) JET_BRANCH_SEGMENTS;
            Vec3d next = origin.add(spreadX * t1 * t1, length * t1, spreadZ * t1 * t1);
            if (next.distanceSquaredTo(current) < 1.0e-6) continue;
            output.add(new LightningSegment(current, next, depth,
                Math.max(0.01, startWidth * (1 - t0 * 0.9)),
                Math.max(0.008, startWidth * (1 - t1 * 0.9)), 1,
                alongUp(current.y(), anchor.y(), height), alongUp(next.y(), anchor.y(), height),
                random.nextLong()));
            if (depth == 0 && step == 3 && random.nextDouble() < 0.45) {
                jetBranch(output, glows, next, anchor, height, width, startWidth * 0.6, random, 1);
            }
            current = next;
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Downward from the anchor, {@code 0..1}: a sprite develops from its head toward its tips. */
    private static double along(double y, double anchorY, double height) {
        return clampAlong((anchorY - y) / height);
    }

    /** Upward from the anchor, {@code 0..1}: a jet climbs. */
    private static double alongUp(double y, double anchorY, double height) {
        return clampAlong((y - anchorY) / height);
    }

    private static double clampAlong(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double signed(SplittableRandom random) {
        return random.nextDouble() * 2 - 1;
    }
}
