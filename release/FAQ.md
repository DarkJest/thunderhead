# Thunderhead FAQ

### Does this require shaders?

No external shader pack is required. The baseline effect uses additive geometry and bundled effect shaders, with a vanilla rendering fallback.

### Does it work with Iris?

The code detects Iris and avoids framebuffer-dependent bloom/distortion under third-party pipelines. However, the release package does **not** claim Iris compatibility until the exact Iris and shader-pack versions are manually tested.

### Does it work with OptiFine?

OptiFine is detected defensively in code, but it has not been manually tested for this release. Treat it as **untested and unsupported**, not compatible.

### Does it work without a shader pack?

Yes. This is the primary rendering path and the one the no-shader gallery shot must demonstrate.

### Fabric?

Yes. Minecraft 1.21.1 with Fabric Loader 0.16.14+ and Fabric API.

### NeoForge?

Yes. Minecraft 1.21.1 with NeoForge 21.1+.

### Is it client-side?

The visual and audio overhaul is client-side and works when joining a server without Thunderhead. Optional near-miss damage and ball lightning are server-owned features and require the mod on the server as well.

### Does it change gameplay?

The client-only experience is visual/audio. When installed on the server, Thunderhead can add configurable near-miss damage and rare ball lightning. It does not replace vanilla lightning damage, fire, rods, copper weathering or mob conversions.

### How is performance handled?

Low, Medium, High and Ultra presets change the per-strike particle budget. Distance-based LOD reduces work for remote strikes, and particle count, render distance and concurrent effects have hard configurable caps. Performance depends on hardware, resolution, weather intensity and configuration; no “zero impact” claim is made.

### Can I reduce flashing or camera movement?

Yes. Reduced-flashing mode disables flicker and multi-stroke sequences, caps flash intensity and camera impulse, and removes the extended sky flash. Screen flash and camera impulse also have separate toggles. Minecraft's own **Hide Lightning Flashes** setting is respected.

### Is the thunder delay realistic?

When enabled, thunder is scheduled from strike distance using approximately 343 metres per second.

### Does it relight chunks?

No. Illumination is transient rendering plus a short client-side sky flash. Stored block light is never rewritten.

