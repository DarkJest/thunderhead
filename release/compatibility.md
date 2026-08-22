# Release compatibility matrix

This file is the source of truth for storefront claims. Update it after each manual run.

| Combination | Status | Evidence / action |
| --- | --- | --- |
| Minecraft 1.21.1 + Fabric Loader 0.16.14 + Fabric API 0.116.15 | **Build verified** | `:fabric:build` must pass; complete one manual storm and command pass before publish. |
| Minecraft 1.21.1 + NeoForge 21.1.248 | **Build verified** | `:neoforge:build` must pass; complete one manual storm and command pass before publish. |
| Vanilla rendering, no external shader pack | **Implemented** | Record screenshot 7 and verify bolt, particles, shockwave, flash and thunder in-game. |
| Iris 1.8.12 (NeoForge) + Complementary Unbound r5.8.1 | **Tested** | Channel, forks, impact particles, shockwave and air distortion render with the pack active and match the same seed rendered without it. Debug overlay reads `programs own \| compositor isolated`; no exception from the mod in the log. |
| Iris, other packs and versions | **Pack-agnostic by construction, not individually tested** | The mod compiles its own programs and draws into its own framebuffer, applying the result after the pack has finished the scene, so no pack-specific behaviour is involved. Record versions before claiming any specific one. |
| OptiFine / Oculus | **Not tested** | Nothing in the mod targets either, and neither has to cooperate for the effect to work. Do not claim compatibility without a run. |
| Dedicated server with Thunderhead | **Implemented, manual validation required** | Verify near-miss damage and ball-lightning replication with two clients. |
| Vanilla server, Thunderhead client | **Implemented, manual validation required** | Verify visual/audio features and absence of server gameplay additions. |

## Minimum manual shader check

Run it once with no shader pack and once with a pack; the point of the check is that the two look the
same.

1. Launch with the exact Iris version and pack named in the release notes.
2. Enable `general.debug` and confirm the overlay reads `programs own | compositor isolated`. Anything
   else means the mod fell back, and the log says why on the line it degraded.
3. Run `/tempestfx strike-camera 20 --seed 12345` ten times. The same seed with and without the pack
   must produce the same bolt: same width ladder, same wide glow, same flash.
4. Confirm the channel is occluded by terrain, by water and by a block placed in front of it, and that
   smoke and dust are occluded the same way. That is the borrowed depth buffer working.
5. Confirm air distortion is visible along the shock front with the pack on.
6. `/tempestfx ball` and confirm the sphere shell, core and ground pool are present, not arcs alone.
7. Toggle the shader pack on and off mid-storm, resize the window, go fullscreen and back, press F1,
   open a GUI, switch to third person, change dimensions and reload resources — then repeat one strike.
8. Record the exact versions here; only then change storefront wording to "Tested".
