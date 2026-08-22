package dev.tempestfx.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Puts a settings button on the mod's entry in ModMenu.
 *
 * <p>Declared as an optional entrypoint, so ModMenu is a suggestion rather than a dependency: when
 * it is absent this class is never loaded and nothing here runs. Players without it reach the same
 * screen through {@code /tempestfx settings}.
 */
public final class TempestFxModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> TempestFxFabricClient.client().settingsScreen(parent);
    }
}
