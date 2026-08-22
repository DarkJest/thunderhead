# Screenshot shot list and capture instructions

Capture at 2560×1440 minimum; use 3840×2160 if 60 FPS remains stable. Hide HUD with `F1` or `/tempestfx camera cinematic`. Use 45–60° FOV, no debug overlay, no chat and no crosshair over the channel.

For timing-critical frames, record a 5-second clip and export the sharpest frame. Minecraft `F2` is fine when you can trigger and capture reliably, but video frame export makes the exact peak easier to choose.

## 1 — HERO

Goal: the single image that sells the project.

1. Use Scene A (Forest), High/Ultra preset and no external shader pack for the first version.
2. Put the impact 18–22 blocks away near a rule-of-thirds point.
3. Frame roughly 65–75% sky and keep the land a dark silhouette.
4. Run `/tempestfx strike-camera 20 --seed 12345`.
5. Capture the first main stroke, before the flash clips the whole frame.
6. Reject any frame where the bolt uses less than one third of the image height or the branches merge into solid white.

## 2 — CLOSE IMPACT

Goal: core, glow, sparks, shockwave and smoke in one readable frame.

1. Stay in Scene A and move 10–16 blocks from the impact.
2. Lower the camera to 1.2–1.6 blocks so the pressure ring crosses visible ground.
3. Use `/tempestfx strike-camera 14 --seed 12345`.
4. Export a frame 2–5 frames after the brightest contact, when sparks and ring are open but smoke is still small.
5. Preserve highlights: lower capture exposure/grade if the white core loses its edge.

## 3 — BRANCHING

Goal: show the entire procedural channel.

1. Use Scene B (Plains), 55–65° FOV and 70–80% sky.
2. Put the strike 70–100 blocks away.
3. Run `/tempestfx strike-camera 80 --seed 8273641` repeatedly until the timing is comfortable; the seed keeps the channel stable.
4. Choose the frame with the thick trunk, major forks and finest visible branches all present.
5. Do not crop the cloud canopy or impact point.

## 4 — WATER

Goal: prove water-specific impact behavior.

1. Use Scene D, camera 2–4 blocks above water, 15–25 blocks away.
2. View the surface at a shallow angle so the ripple forms a readable ellipse.
3. Aim at open water and run `/tempestfx strike water --seed 45009`.
4. Export the first frame where spray and steam separate from the bolt core.
5. Confirm no dry dust/debris dominates the frame.

## 5 — NIGHT ILLUMINATION

Goal: a true before/impact pair.

1. Use Scene E and lock camera, FOV and all settings.
2. Capture one clean dark frame immediately before the strike.
3. Without moving, run `/tempestfx strike-camera 30 --seed 71337`.
4. Capture the frame where house façades and the road receive the cold flash.
5. Create a 50/50 vertical split labeled only `BEFORE` and `TEMPEST FX`; do not change exposure independently between halves.

## 6 — SHADERS

Goal: optional beauty shot, never the sole proof image.

1. Install the exact Iris and shader-pack versions you intend to name.
2. Complete the compatibility test in `release/compatibility.md` first.
3. Reuse Scene A, seed `12345`, camera marker and FOV.
4. Capture the impact without pushing shader bloom high enough to erase branches.
5. Caption with exact versions. If the test fails, omit this asset instead of implying support.

## 7 — VANILLA / NO SHADER PACK

Goal: prove the effect stands on its own.

1. Disable the external shader pack and restart/reload as required.
2. Reuse the exact camera marker, FOV and seed from screenshot 6.
3. Run `/tempestfx strike-camera 20 --seed 12345`.
4. Match only global color grading; do not add artificial bloom in post.
5. Caption: **No external shader pack required.**

## Composition rules

- The bolt is the subject, not weather ambience.
- Use darkness as negative space; keep the core sharp inside the glow.
- Put the impact on a third unless symmetry is intentional.
- Keep horizon level and avoid extreme FOV distortion.
- Never show debug UI, coordinates, chat, inventory, crosshair or menus.
- Never use an unrelated daytime scene or a shader preset that hides the channel.

