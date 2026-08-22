package dev.tempestfx.lightning;

import java.util.List;

public record LightningBranch(int depth, long seed, List<LightningSegment> segments) {
    public LightningBranch { segments = List.copyOf(segments); }
}
