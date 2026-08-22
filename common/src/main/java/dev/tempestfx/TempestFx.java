package dev.tempestfx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared identity and logging for the mod. */
public final class TempestFx {
    public static final String MOD_ID = "tempestfx";
    public static final String MOD_NAME = "Thunderhead";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private TempestFx() {}

    /** Diagnostics go to the game log rather than the console, so bug reports actually contain them. */
    public static Logger log() { return LOGGER; }
}
