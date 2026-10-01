package codes.castled.karasu.managers;

import java.util.Locale;

/** The engine named by {@code karasu.visual.model-engine}. */
public enum ModelEngineType {

    /** Prefer BetterModel, fall back to ModelEngine. */
    AUTO,
    /** BetterModel only; never silently falls back. */
    BETTERMODEL,
    /** ModelEngine only. */
    MODELENGINE,
    /** Draw the crows as bats, using LibsDisguises if present. No resource pack needed. */
    VANILLA,
    /** Draw no models at all. Transforms still work, the crow is just invisible. */
    NONE;

    /** Parses a config value, falling back to {@link #AUTO} for anything unrecognised. */
    public static ModelEngineType parse(String raw) {
        if (raw == null || raw.isBlank()) return AUTO;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }
}