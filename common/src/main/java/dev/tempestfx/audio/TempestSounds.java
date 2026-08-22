package dev.tempestfx.audio;

import dev.tempestfx.TempestFx;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Resource ids of the original Thunderhead audio set, and the registered events behind them.
 *
 * <p>Registration itself is a loader concern, but the resolved {@link SoundEvent} is shared: the
 * client plays clips through the same table the loader filled, so there is only one place where a
 * profile maps to a sound.
 */
public final class TempestSounds {
    private static final Map<ThunderProfile, ResourceLocation> IDS = new EnumMap<>(ThunderProfile.class);
    private static final Map<ThunderProfile, SoundEvent> EVENTS = new EnumMap<>(ThunderProfile.class);

    static {
        for (ThunderProfile profile : ThunderProfile.values()) {
            IDS.put(profile, ResourceLocation.fromNamespaceAndPath(TempestFx.MOD_ID, profile.path()));
        }
    }

    private TempestSounds() {}

    public static ResourceLocation id(ThunderProfile profile) { return IDS.get(profile); }

    /** Called by the loader bootstrap once the event is in the registry. */
    public static void bind(ThunderProfile profile, SoundEvent event) { EVENTS.put(profile, event); }

    /** @return the registered event, or {@code null} before registration has run. */
    public static SoundEvent event(ThunderProfile profile) { return EVENTS.get(profile); }
}
