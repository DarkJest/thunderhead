# Final release checklist

## Brand and storefront

- [ ] Final mod icon exported at 512×512 and checked at 64×64
- [ ] Real in-game hero image selected
- [ ] Modrinth description placeholders replaced
- [ ] CurseForge description placeholders replaced
- [ ] GitHub README media placeholders replaced
- [ ] Short descriptions checked against current platform limits
- [ ] FAQ reviewed against the final build
- [ ] Changelog and v1.0.0 release notes attached

## Screenshots and clips

- [ ] Hero screenshot
- [ ] Close lightning screenshot
- [ ] Branching screenshot
- [ ] Water impact screenshot
- [ ] Night illumination before/after
- [ ] Shader screenshot with exact tested versions
- [ ] No-shader screenshot
- [ ] Main 3–6 second GIF
- [ ] Before/after clip
- [ ] 30–60 second showcase
- [ ] 10–15 second vertical short
- [ ] Clean 1440p60 or 4K60 master archived
- [ ] All captures have no HUD, chat, coordinates or debug overlay

## Build and compatibility

- [ ] `clean buildAll` passes
- [ ] Fabric JAR launches and completes ten seeded strikes
- [ ] NeoForge JAR launches and completes ten seeded strikes
- [ ] Fabric JAR tested in a fresh profile
- [ ] NeoForge JAR tested in a fresh profile
- [ ] No-shader/vanilla renderer path tested
- [ ] Iris + exact shader-pack versions tested and recorded, or all Iris claims removed
- [ ] OptiFine remains marked untested unless manually validated
- [ ] Client-only connection to a vanilla server tested
- [ ] Client + dedicated server tested with two clients
- [ ] Near-miss damage tested without double damage
- [ ] Ball lightning spawn/replication tested
- [ ] Low, Medium, High and Ultra presets smoke-tested
- [ ] Performance preset tested during a dense storm
- [ ] Reduced-flashing mode tested
- [ ] Minecraft Hide Lightning Flashes option tested
- [ ] `/tempestfx strike <x> <y> <z> --seed 12345` tested
- [ ] `/tempestfx strike-camera 20 --seed 12345` tested
- [ ] `/tempestfx camera cinematic`, `speed` and `off` tested

## Packaging

- [ ] Fabric and NeoForge filenames include version and loader
- [ ] Correct Minecraft/loader dependencies selected on each platform
- [ ] MIT license included
- [ ] No development config, world save or capture files included in the JAR
- [ ] SHA-256 checksums recorded for final JARs
- [ ] GitHub release includes both JARs, release notes and checksums
- [ ] Modrinth and CurseForge uploads use the same final JARs
- [ ] No commit or tag created until the owner approves the final diff

