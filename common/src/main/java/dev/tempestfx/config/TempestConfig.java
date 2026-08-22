package dev.tempestfx.config;

import dev.tempestfx.compat.RenderCompatibilityMode;
import dev.tempestfx.math.FxMath;

/**
 * User-facing configuration.
 *
 * <p>Fields are plain public data so Gson can round-trip them, but nothing reads them before
 * {@link #validate()} has clamped every value into a range the renderer and simulation can handle.
 * Validation is idempotent and is also applied to hand-edited files on load.
 */
public final class TempestConfig {
    public General general = new General();
    public Lightning lightning = new Lightning();
    public Sky sky = new Sky();
    public Impact impact = new Impact();
    public Lighting lighting = new Lighting();
    public Camera camera = new Camera();
    public Audio audio = new Audio();
    public Performance performance = new Performance();
    public Compatibility compatibility = new Compatibility();

    public static final class General {
        public boolean enabled = true;
        public boolean debug = false;
        /** Accessibility switch: removes rapid brightness changes while keeping the bolt readable. */
        public boolean reducedFlashing = false;
    }

    public static final class Lightning {
        public int geometryQuality = 7;
        public int branchCount = 18;
        public float thickness = 1f;
        public float glowStrength = 1f;
        public boolean flicker = true;
        /** Strength of the cold blue-violet tint in the glow layers; the core stays near-white. */
        public float coldTint = 1f;
        /** Maximum visual return strokes per flash; 0 makes every flash a single stroke. */
        public int returnStrokes = 3;
        /**
         * The channel forms in discrete steps instead of appearing whole, and a bright return-stroke
         * front then climbs back up it. Turning this off restores the smooth reveal.
         */
        public boolean steppedLeader = true;
        /** Overall size of the flash: channel height, lean and the intracloud canopy above it. */
        public float scale = 1f;
        /** How far the near-horizontal cloud-base channels reach; 0 removes them entirely. */
        public float skySpread = 1f;
        /**
         * Fraction of ground strikes drawn as a positive superbolt: wider, far brighter, violet, and
         * nearly unbranched. Rare on purpose - the whole effect of one is that it is not the usual
         * flash.
         */
        public float superboltChance = 0.04f;
    }

    /**
     * The electrical life of the storm itself, above and between the clouds.
     *
     * <p>Everything here is ambient: it produces no impact, no damage and no block change, and it
     * exists so a storm reads as a system rather than as a series of unrelated ground strikes.
     */
    public static final class Sky {
        /** Master switch for every ambient discharge: cloud-to-cloud, intracloud and megaflashes. */
        public boolean skyActivity = true;
        /** Horizontal channels travelling between cloud regions. */
        public boolean cloudToCloud = true;
        /** Activity buried inside a cloud: little exposed channel, several pulses of internal light. */
        public boolean intracloud = true;
        /**
         * One in this many ambient discharges is a megaflash. The default puts one behind several
         * hundred ordinary events, which is roughly what makes it memorable.
         */
        public int megaflashRarity = 400;
        /** Overall pace of ambient activity; 0 switches it off as surely as {@link #skyActivity}. */
        public float activityRate = 1f;
        /** Localised cloud illumination: the cloud lighting up from the inside. */
        public boolean cloudIllumination = true;
        public float cloudIlluminationStrength = 1f;
        /** Hard ceiling on cloud light volumes alive at once. */
        public int maxCloudLightSources = 24;
        /** Hard ceiling on ambient channels alive at once. */
        public int maxAmbientDischarges = 12;

        /**
         * Red sprites: the enormous red structures that appear far above a storm after a powerful
         * positive discharge. Rare on purpose - the whole value of one is not having seen it before.
         */
        public boolean redSprites = true;
        /** Chance a qualifying discharge raises a sprite. Megaflashes get four times this. */
        public float spriteChance = 0.16f;
        /** Blue jets: cones of violet-blue light climbing out of a cloud top. */
        public boolean blueJets = true;
        /** Chance an ambient discharge in the cloud layer sends a jet out of the top. */
        public float blueJetChance = 0.03f;
        /** Hard ceiling on sprites and jets alive at once. */
        public int maxLuminousEvents = 3;
    }

    public static final class Impact {
        public boolean shockwave = true;
        public float shockwaveStrength = 1f;
        public boolean debris = true;
        public boolean sparks = true;
        public boolean smoke = true;
        public boolean ash = true;
        /** Screen-space refraction around the channel and impact, applied by the mod's composite pass. */
        public boolean airDistortion = true;
        public float airDistortionStrength = 1f;
        /** Expanding ripple decal that deforms the look of the struck surface. */
        public boolean surfaceRipple = true;
        /** Arcs crawling over nearby entities that are moving when the bolt lands. */
        public boolean entityDischarge = true;
        public float entityDischargeRadius = 10f;
        /** Blocks per tick a target must be moving before it starts arcing. */
        public float entityDischargeMinSpeed = 0.02f;
        /**
         * Upward streamers: tall and conductive things reach up as the leader closes, and the one
         * that connects decides where the bolt lands. This is what gives a lightning rod something
         * visible to do.
         */
        public boolean streamers = true;
        /**
         * Lightning rods crackle while the storm overhead is heavily charged. Nothing predicts a
         * strike - vanilla decides one on the tick it happens - so this follows the storm's own
         * accumulated charge, which is what makes a real rod hiss.
         */
        public boolean rodCorona = true;
        /** Scorched ash imprint left where a player took a direct hit. */
        public boolean ashImprint = true;
        public float ashImprintSeconds = 18f;
        /** Sparks and crackle around ball lightning. The sphere itself is a server-driven entity. */
        public boolean ballLightningEffects = true;
    }

    public static final class Lighting {
        public boolean dynamicLighting = true;
        public float illuminationRadius = 28f;
        public float illuminationStrength = 1f;
        /** Extends the vanilla client-side sky flash; never touches stored block light. */
        public boolean worldFlash = true;
        public int worldFlashTicks = 4;
        /** Distant cloud-to-ground channels across the horizon during a rolling thunder event. */
        public boolean distantBolts = true;
    }

    public static final class Camera {
        public boolean screenFlash = true;
        public float flashStrength = 1f;
        public boolean cameraImpulse = true;
        public float impulseStrength = 0.35f;
    }

    public static final class Audio {
        public boolean customThunder = true;
        /** Lets a player keep vanilla's lightning sounds when another mod relies on them. */
        public boolean suppressVanillaThunder = true;
        public float thunderVolume = 1f;
        public boolean realisticSoundDelay = true;
        /** Beyond this listener distance a strike produces no thunder at all. */
        public float maxThunderDistance = 512f;
        /** The five-to-ten second rolling thunder event, independent of the visual bolt. */
        public boolean giantRoll = true;
        /** Chance a strike inside {@link #giantRollDistance} opens a full roll. */
        public float giantRollChance = 0.28f;
        public float giantRollDistance = 160f;
    }

    public static final class Performance {
        public QualityPreset qualityPreset = QualityPreset.HIGH;
        public int maxParticles = 2048;
        public float renderDistance = 384f;
        public boolean lod = true;
        public int maxConcurrentEffects = 48;
    }

    public static final class Compatibility {
        public RenderCompatibilityMode shaderCompatibilityMode = RenderCompatibilityMode.AUTO;
        public BloomMode bloomMode = BloomMode.AUTO;
        /** Use the bundled core shaders; falls back to vanilla programs when disabled or broken. */
        public boolean customShaders = true;
        /**
         * Draw the effect into a framebuffer of the mod's own and composite it over the finished
         * frame. This is what makes the effect look the same with and without a shader pack. Turning
         * it off draws straight into the scene, the way the mod worked before, and is only worth doing
         * to diagnose a conflict.
         */
        public boolean effectCompositor = true;
    }

    public TempestConfig validate() {
        if (general == null) general = new General();
        if (lightning == null) lightning = new Lightning();
        if (sky == null) sky = new Sky();
        if (impact == null) impact = new Impact();
        if (lighting == null) lighting = new Lighting();
        if (camera == null) camera = new Camera();
        if (audio == null) audio = new Audio();
        if (performance == null) performance = new Performance();
        if (compatibility == null) compatibility = new Compatibility();
        if (performance.qualityPreset == null) performance.qualityPreset = QualityPreset.HIGH;
        if (compatibility.shaderCompatibilityMode == null) compatibility.shaderCompatibilityMode = RenderCompatibilityMode.AUTO;
        if (compatibility.bloomMode == null) compatibility.bloomMode = BloomMode.AUTO;

        lightning.geometryQuality = FxMath.clamp(lightning.geometryQuality, 3, 9);
        lightning.branchCount = FxMath.clamp(lightning.branchCount, 0, 64);
        lightning.thickness = FxMath.clamp(lightning.thickness, 0.25f, 4f);
        lightning.glowStrength = FxMath.clamp(lightning.glowStrength, 0f, 3f);
        lightning.coldTint = FxMath.clamp(lightning.coldTint, 0f, 2f);
        lightning.returnStrokes = FxMath.clamp(lightning.returnStrokes, 0, 4);
        lightning.scale = FxMath.clamp(lightning.scale, 0.4f, 2.5f);
        lightning.skySpread = FxMath.clamp(lightning.skySpread, 0f, 3f);
        lightning.superboltChance = FxMath.clamp(lightning.superboltChance, 0f, 1f);

        sky.megaflashRarity = FxMath.clamp(sky.megaflashRarity, 1, 100000);
        sky.activityRate = FxMath.clamp(sky.activityRate, 0f, 4f);
        sky.cloudIlluminationStrength = FxMath.clamp(sky.cloudIlluminationStrength, 0f, 3f);
        sky.maxCloudLightSources = FxMath.clamp(sky.maxCloudLightSources, 0, 128);
        sky.maxAmbientDischarges = FxMath.clamp(sky.maxAmbientDischarges, 0, 64);
        sky.spriteChance = FxMath.clamp(sky.spriteChance, 0f, 1f);
        sky.blueJetChance = FxMath.clamp(sky.blueJetChance, 0f, 1f);
        sky.maxLuminousEvents = FxMath.clamp(sky.maxLuminousEvents, 0, 16);

        impact.shockwaveStrength = FxMath.clamp(impact.shockwaveStrength, 0f, 3f);
        impact.airDistortionStrength = FxMath.clamp(impact.airDistortionStrength, 0f, 2f);
        impact.entityDischargeRadius = FxMath.clamp(impact.entityDischargeRadius, 0f, 48f);
        impact.entityDischargeMinSpeed = FxMath.clamp(impact.entityDischargeMinSpeed, 0f, 1f);
        impact.ashImprintSeconds = FxMath.clamp(impact.ashImprintSeconds, 1f, 120f);

        lighting.illuminationRadius = FxMath.clamp(lighting.illuminationRadius, 0f, 96f);
        lighting.illuminationStrength = FxMath.clamp(lighting.illuminationStrength, 0f, 3f);
        lighting.worldFlashTicks = FxMath.clamp(lighting.worldFlashTicks, 0, 12);

        camera.flashStrength = FxMath.clamp(camera.flashStrength, 0f, 1f);
        camera.impulseStrength = FxMath.clamp(camera.impulseStrength, 0f, 1f);

        audio.thunderVolume = FxMath.clamp(audio.thunderVolume, 0f, 2f);
        audio.maxThunderDistance = FxMath.clamp(audio.maxThunderDistance, 16f, 2048f);
        audio.giantRollChance = FxMath.clamp(audio.giantRollChance, 0f, 1f);
        audio.giantRollDistance = FxMath.clamp(audio.giantRollDistance, 0f, 1024f);

        performance.maxParticles = FxMath.clamp(performance.maxParticles, 128, 16384);
        performance.renderDistance = FxMath.clamp(performance.renderDistance, 64f, 1024f);
        performance.maxConcurrentEffects = FxMath.clamp(performance.maxConcurrentEffects, 1, 256);

        if (general.reducedFlashing) {
            camera.flashStrength = Math.min(camera.flashStrength, 0.25f);
            camera.impulseStrength = Math.min(camera.impulseStrength, 0.2f);
            lightning.flicker = false;
            lighting.worldFlashTicks = 0;
            lighting.distantBolts = false;
            // A multi-stroke flash is exactly the rapid brightness change this mode exists to remove.
            lightning.returnStrokes = 0;
            // So is a pulse train inside a cloud, and so is a superbolt three times the usual output.
            sky.intracloud = false;
            sky.cloudIllumination = false;
            sky.activityRate = Math.min(sky.activityRate, 0.3f);
            lightning.superboltChance = 0f;
            // A sprite is a large area of sky changing brightness in a fraction of a second, which is
            // precisely what this mode exists to remove - however rare and however far away it is.
            sky.redSprites = false;
            sky.blueJets = false;
        }
        return this;
    }

    /** Particle budget for a strike at {@code distance}, before the global cap is applied. */
    public int particleBudget(double distance) {
        int base = switch (performance.qualityPreset) {
            case LOW -> 90;
            case MEDIUM -> 160;
            case HIGH -> 280;
            case ULTRA -> 460;
        };
        if (!performance.lod) return base;
        if (distance < 32) return base;
        if (distance < 96) return base / 2;
        if (distance < 256) return base / 5;
        return 0;
    }
}
