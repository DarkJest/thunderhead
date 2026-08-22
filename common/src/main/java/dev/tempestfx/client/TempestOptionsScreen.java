package dev.tempestfx.client;

import com.mojang.serialization.Codec;
import dev.tempestfx.config.QualityPreset;
import dev.tempestfx.config.TempestConfig;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * In-game settings, built on vanilla's own options widgets.
 *
 * <p>No Cloth Config, no ModMenu dependency, no config library: those would make a mod that
 * currently needs nothing but its loader demand a second download, and the vanilla widgets already
 * do sliders, toggles and cycles with tooltips. The same screen serves both loaders because it is
 * ordinary client code - Fabric reaches it through ModMenu's optional entrypoint, NeoForge through
 * its own config-screen hook, and {@code /tempestfx settings} opens it with neither.
 */
public final class TempestOptionsScreen extends OptionsSubScreen {
    private final TempestConfig config;
    private final Runnable onSave;

    public TempestOptionsScreen(Screen lastScreen, TempestConfig config, Runnable onSave) {
        super(lastScreen, Minecraft.getInstance().options, Component.translatable("screen.tempestfx.title"));
        this.config = config;
        this.onSave = onSave;
    }

    @Override
    protected void addOptions() {
        list.addBig(cycle("preset", QualityPreset.class, config.performance.qualityPreset,
            value -> config.performance.qualityPreset = value));

        list.addSmall(
            toggle("enabled", config.general.enabled, value -> config.general.enabled = value),
            // The one option that exists for people who need it rather than for people tuning looks.
            toggle("reduced_flashing", config.general.reducedFlashing,
                value -> config.general.reducedFlashing = value));

        list.addSmall(
            percent("thickness", 25, 400, config.lightning.thickness, value -> config.lightning.thickness = value),
            percent("glow", 0, 300, config.lightning.glowStrength, value -> config.lightning.glowStrength = value),
            count("branches", 0, 64, config.lightning.branchCount, value -> config.lightning.branchCount = value),
            percent("scale", 25, 300, config.lightning.scale, value -> config.lightning.scale = value),
            percent("cold_tint", 0, 200, config.lightning.coldTint, value -> config.lightning.coldTint = value),
            count("return_strokes", 0, 6, config.lightning.returnStrokes,
                value -> config.lightning.returnStrokes = value),
            toggle("flicker", config.lightning.flicker, value -> config.lightning.flicker = value),
            toggle("distant_bolts", config.lighting.distantBolts, value -> config.lighting.distantBolts = value));

        list.addSmall(
            toggle("shockwave", config.impact.shockwave, value -> config.impact.shockwave = value),
            toggle("sparks", config.impact.sparks, value -> config.impact.sparks = value),
            toggle("smoke", config.impact.smoke, value -> config.impact.smoke = value),
            toggle("debris", config.impact.debris, value -> config.impact.debris = value),
            toggle("ash", config.impact.ash, value -> config.impact.ash = value),
            toggle("air_distortion", config.impact.airDistortion, value -> config.impact.airDistortion = value),
            toggle("entity_discharge", config.impact.entityDischarge,
                value -> config.impact.entityDischarge = value),
            toggle("ash_imprint", config.impact.ashImprint, value -> config.impact.ashImprint = value));

        list.addSmall(
            toggle("screen_flash", config.camera.screenFlash, value -> config.camera.screenFlash = value),
            percent("flash_strength", 0, 200, config.camera.flashStrength,
                value -> config.camera.flashStrength = value),
            toggle("camera_impulse", config.camera.cameraImpulse, value -> config.camera.cameraImpulse = value),
            percent("impulse_strength", 0, 200, config.camera.impulseStrength,
                value -> config.camera.impulseStrength = value),
            toggle("dynamic_lighting", config.lighting.dynamicLighting,
                value -> config.lighting.dynamicLighting = value),
            toggle("world_flash", config.lighting.worldFlash, value -> config.lighting.worldFlash = value));

        list.addSmall(
            toggle("custom_thunder", config.audio.customThunder, value -> config.audio.customThunder = value),
            percent("thunder_volume", 0, 200, config.audio.thunderVolume,
                value -> config.audio.thunderVolume = value),
            toggle("sound_delay", config.audio.realisticSoundDelay,
                value -> config.audio.realisticSoundDelay = value),
            toggle("giant_roll", config.audio.giantRoll, value -> config.audio.giantRoll = value),
            toggle("suppress_vanilla_thunder", config.audio.suppressVanillaThunder,
                value -> config.audio.suppressVanillaThunder = value));

        list.addSmall(
            count("max_particles", 128, 8192, config.performance.maxParticles,
                value -> config.performance.maxParticles = value),
            count("render_distance", 32, 512, (int) config.performance.renderDistance,
                value -> config.performance.renderDistance = value),
            count("max_effects", 1, 128, config.performance.maxConcurrentEffects,
                value -> config.performance.maxConcurrentEffects = value),
            toggle("lod", config.performance.lod, value -> config.performance.lod = value));
    }

    /**
     * Validates and writes on the way out.
     */
    @Override
    public void onClose() {
        config.validate();
        onSave.run();
        super.onClose();
    }

    private OptionInstance<Boolean> toggle(String key, boolean initial, Consumer<Boolean> setter) {
        return OptionInstance.createBoolean(caption(key), tooltip(key), initial, setter::accept);
    }

    /** A fraction edited as a percentage, because {@code 140%} needs no explanation and {@code 1.4} does. */
    private OptionInstance<Integer> percent(String key, int min, int max, float initial, Consumer<Float> setter) {
        return new OptionInstance<>(caption(key), tooltip(key),
            (label, value) -> Component.translatable("screen.tempestfx.percent", label, value),
            new OptionInstance.IntRange(min, max), Math.round(initial * 100),
            value -> setter.accept(value / 100f));
    }

    private OptionInstance<Integer> count(String key, int min, int max, int initial, Consumer<Integer> setter) {
        return new OptionInstance<>(caption(key), tooltip(key),
            (label, value) -> Component.translatable("options.generic_value", label, value),
            new OptionInstance.IntRange(min, max), initial, setter::accept);
    }

    private <T extends Enum<T>> OptionInstance<T> cycle(String key, Class<T> type, T initial, Consumer<T> setter) {
        List<T> values = Arrays.asList(type.getEnumConstants());
        return new OptionInstance<>(caption(key), tooltip(key),
            (label, value) -> Component.translatable("options.generic_value", label,
                Component.translatable(caption(key) + "." + value.name().toLowerCase(java.util.Locale.ROOT))),
            new OptionInstance.Enum<>(values, Codec.INT.xmap(values::get, values::indexOf)),
            initial, setter::accept);
    }

    private static String caption(String key) { return "option.tempestfx." + key; }

    /**
     * Tooltips come from the language file, and only where one exists.
     */
    private <T> OptionInstance.TooltipSupplier<T> tooltip(String key) {
        String translation = caption(key) + ".tooltip";
        if (!hasTranslation(translation)) return value -> null;
        Supplier<net.minecraft.client.gui.components.Tooltip> tooltip =
            () -> net.minecraft.client.gui.components.Tooltip.create(Component.translatable(translation));
        return value -> tooltip.get();
    }

    private static boolean hasTranslation(String key) {
        return net.minecraft.locale.Language.getInstance().has(key);
    }

    /** Label for the button that opens this screen from elsewhere. */
    public static Component title() { return Component.translatable("screen.tempestfx.title"); }

}
