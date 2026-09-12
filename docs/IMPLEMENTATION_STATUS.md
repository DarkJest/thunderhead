# Implementation journal

## 2026-09-12 — 1.2 baseline / 1.2.1 corrections

- User authorized implementation through 2.0, one independent reviewer after each minor release,
  and local Minecraft testing. No publishing has been requested.
- Baseline buildAll succeeded using Microsoft JDK 21.0.12; both loader builds and cached common tests passed.
- Baseline NeoForge 21.1.248 launched a copy of the neighboring project's save, named Thunderhead QA,
  without Iris. Radeon RX 570 / OpenGL 4.6. Six FX programs compiled; smoke commands ran without mod errors.
  This is log evidence, not visual certification.
- Computer Use node runtime cannot initialize (kernel assets path error), including after reset.
  Added an opt-in finite development screenshot capture run as an independent integration-test path.
- Reviewer review_1_2_0 found: private FBO retained on failed prepare; texture-unit leak;
  stale render config after reload; origin-vs-AABB near-miss exclusion; destructive accessibility validation.
  Corrections implemented alongside actual viewport/clear-color/blend-state preservation and truthful render status.
- Corrections passed buildAll with fresh common tests. The same reviewer rechecked all five fixes
  and the state guard lifecycle without finding unresolved defects in that scope.
- Finite vanilla run completed, saved 36 screenshots, and exited normally with all world chunks saved.
  Examined before/strike frames 119/122 from run 1789238547404: channels visible, scene flash present;
  sampled log reports isolated compositor. This does not certify transparent-water occlusion or reflections.
- Next: shader capture, then 1.3.0. No later milestone is complete yet.

## 1.3.0 — review and integration

- Shared immutable pulse timeline and fixed geometry, two presentation profiles, preserved configuration/API options.
- Reviewer review_1_3_0 identified first-frame aging, omitted repeat callbacks and premature removal during
  accessibility fade. All fixed; FlashSimulation now owns birth ordering and frame sampling retains the first interval.
- buildAll passed 166 tests after the fixes, including birth→tick→first-frame regression, geometry identity,
  exposure energy at multiple frame rates, config migration and saved accessibility preferences.
- Initial Realistic capture with Complementary completed (run 1789239034193); final corrected captures pending.
- Final corrections rechecked by the same reviewer: no blocking defects in reviewed scope.
- Corrected Realistic capture completed with Complementary at 60 FPS cap (1789239279005) and ARTShade
  at 30 FPS cap (1789239346704). Examined captured channel frames, isolated compositor confirmed in logs;
  both runs exited with worlds saved. Capture overhead means these are not performance benchmarks.
- 1.3 implementation checkpoint complete; extended physical calibration, complete shader matrix and full
  roadmap acceptance remain ongoing. Next is 1.4 channel geometry and distinct discharge kinds.

## 1.4.0 — implementation, review pending

- Canonical channel/backbone generation, retained LOD vertices, attached forks and hard budgets.
- Four API categories with distinct envelopes and explicit visual commands on both loaders.
- Ground aftermath guarded by category; cloud source fallback documented as an approximation.
- buildAll passed 169 tests. Reviewer review_1_4_0 is checking the changes; ARTShade capture in progress.
- Reviewer found cloud categories only differed in debug placement, and cinematic fallback gave cloud
  kinds vertical origins. Moved kind-specific profiles into generation/timing and preserved horizontal
  cloud origins in both presentation modes. Added API factory regressions; buildAll passes 171 tests.
- Same reviewer rechecked: no remaining blocking findings. ARTShade run 1789239751700 and final
  Complementary run 1789239959670 completed with captures and normal save/exit; examined cloud channels.
- 1.4 implementation checkpoint complete. It remains an approximate geometric model, not a field solver.
- 1.5 investigation: bundled Complementary declares lightningBoltPosition but does not use it;
  ARTShade does not reference it. Exposing that uniform alone cannot provide their missing lighting.
