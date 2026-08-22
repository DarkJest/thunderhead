# Showcase world setup

Create a separate Creative world named `Thunderhead Showcase`. Enable cheats. Use a seed with forest, plains, a mountain, water and a village within a practical travel radius, or copy those areas into one curated world.

## Lock the conditions

Run once:

```text
/time set midnight
/weather thunder 1000000
/gamerule doDaylightCycle false
/gamerule doWeatherCycle false
/gamerule doFireTick false
```

Set render distance as high as the machine can hold at a locked 60 FPS. Use High particles and the Thunderhead High preset first; use Ultra only if a one-minute capture remains stable.

Put marker blocks outside the camera crop and save each camera position/rotation in a notes file. For every comparison, reuse the same marker, FOV, seed and strike coordinate.

## Scene A — Forest

- Choose dense spruce/dark oak with a 25×25 block clearing or road.
- Camera: 1.7 blocks above ground, 15–30 blocks from impact.
- Keep one foreground trunk on an outer third for parallax; do not cover the bolt.
- Aim at the impact and run `/tempestfx strike-camera 20 --seed 12345`.
- Best for: hero, close impact, shockwave and main GIF.

## Scene B — Plains

- Find an open 150+ block horizon with few tall objects.
- Camera: slightly below a small ridge, tilted only enough to keep 70% sky.
- Strike: 60–100 blocks away with seed `8273641`.
- Best for: full branching, multi-stroke and delayed thunder.

## Scene C — Mountain

- Choose a peak at least 60 blocks above the camera's terrain.
- Camera: across a valley, 100–180 blocks away; use 40–55° FOV.
- Use exact coordinates: `/tempestfx strike <x> <y> <z> --seed 99017`.
- Best for: scale, final title shot and distant thunder.

## Scene D — Water

- Use a lake at least 40 blocks across or a calm ocean inlet.
- Camera: 2–4 blocks above water and 15–25 blocks from the target.
- Aim at the target water tile and run `/tempestfx strike water --seed 45009`.
- Best for: spray, steam, ripple and reflection-like additive light.

## Scene E — Village

- Use a village with a clear street and at least three visible façades.
- Turn off nearby torches only if the scene remains readable before the strike.
- Camera: 25–40 blocks from impact, slightly elevated.
- Use `/tempestfx strike-camera 30 --seed 71337`.
- Best for: whole-scene illumination and recognizable scale.

## Reset between takes

Wait 8–12 seconds for smoke, particles, rolling audio and return strokes to finish. Keep game simulation at normal speed. If rain noise masks thunder, lower only Minecraft weather volume slightly; do not mute it completely.

