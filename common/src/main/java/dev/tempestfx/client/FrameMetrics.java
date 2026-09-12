package dev.tempestfx.client;
import java.util.Arrays;

/** Allocation-free frame-duration ring. Measures CPU submission plus driver waits, not isolated GPU time. */
final class FrameMetrics {
    private final long[] samples = new long[2048];
    private int cursor, count;
    void add(long nanos) { samples[cursor] = nanos; cursor = (cursor+1)%samples.length; count = Math.min(count+1,samples.length); }
    String report() {
        if(count==0)return "no samples";
        long[] ordered=Arrays.copyOf(samples,count); Arrays.sort(ordered);
        return String.format(java.util.Locale.ROOT,"samples=%d p50=%.3fms p95=%.3fms p99=%.3fms",count,
            ordered[count/2]/1e6,ordered[Math.min(count-1,(int)(count*.95))]/1e6,ordered[Math.min(count-1,(int)(count*.99))]/1e6);
    }
}
