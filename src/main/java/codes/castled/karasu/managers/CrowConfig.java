package codes.castled.karasu.managers;

import java.io.File;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reads {@code karasu.yml}. Every fallback comes from {@link ConfigMigrator}, which is also what
 * rewrites the file when it is missing an option, so a default is only ever declared once.
 */
public class CrowConfig {
    private static final String CONFIG_FILE = "karasu.yml";
    private static CrowConfig instance;
    private FileConfiguration config;
    private final File configFile;
    private final JavaPlugin plugin;

    /**
     * The engine actually in use, resolved once at startup. Set before anything reads model names, so
     * that the model getters below can hand out the right names for whichever engine won.
     */
    private ModelEngineType modelEngineType = ModelEngineType.AUTO;

    private CrowConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE, false);
        }

        load();
    }

    public static void initialize(JavaPlugin plugin) {
        if (instance == null) {
            instance = new CrowConfig(plugin);
        }
    }

    public static CrowConfig getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CrowConfig not initialized. Call initialize() first.");
        }
        return instance;
    }

    /** Loads the file and brings it up to date, so options added since it was written show up in it. */
    private void load() {
        ConfigMigrator.Result result = migrate();
        if (result.changed()) {
            report(result);
        }
        // Read back from disk rather than trusting the in-memory merge, so what the getters see is
        // exactly what is in the file, and a failed write leaves the operator's values untouched.
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    private ConfigMigrator.Result migrate() {
        return ConfigMigrator.migrate(configFile, YamlConfiguration.loadConfiguration(configFile));
    }

    private void report(ConfigMigrator.Result result) {
        for (String path : result.added()) {
            plugin.getLogger().info("Added missing config option " + path + " with its default.");
        }
        for (String path : result.removed()) {
            plugin.getLogger().info("Removed the obsolete option " + path + "; see karasu.yml for its replacement.");
        }
    }

    /**
     * Reads {@code path}, falling back to the declared default when the file has no value for it. Going
     * through the file rather than its getter defaults keeps the declared default in one place, and
     * means an operator who wrote an unparseable value still gets the default instead of a wrong type.
     */
    private Object raw(String path) {
        Object value = config.get(path);
        return value != null ? value : ConfigMigrator.defaultValue(path, null);
    }

    private double dbl(String path) {
        return raw(path) instanceof Number number ? number.doubleValue() : 0;
    }

    private int num(String path) {
        return raw(path) instanceof Number number ? number.intValue() : 0;
    }

    private String str(String path) {
        return raw(path) instanceof String value ? value : "";
    }

    private boolean bool(String path) {
        return raw(path) instanceof Boolean value && value;
    }

    public double getBaseScale() {
        return dbl("karasu.transformation.base-scale");
    }

    public double getMorphFlightSpeed() {
        return dbl("karasu.transformation.morph-flight-speed");
    }

    public int getMaxCrowCount() {
        return num("karasu.transformation.max-crow-count");
    }

    public int getDepartureCrowCount() {
        return num("karasu.swarm.departure-crows");
    }

    public int getArrivalCrowCount() {
        return num("karasu.swarm.arrival-crows");
    }

    public double getSwarmCrowScale() {
        return dbl("karasu.swarm.crow-scale");
    }

    public double getSwarmSpread() {
        return dbl("karasu.swarm.spread");
    }

    public int getDepartureTicks() {
        return num("karasu.swarm.departure-ticks");
    }

    public int getArrivalTicks() {
        return num("karasu.swarm.arrival-ticks");
    }

    public int getMannequinTicks() {
        return num("karasu.swarm.mannequin-ticks");
    }

    public double getMinTeleportDistance() {
        return dbl("karasu.swarm.min-teleport-distance");
    }

    public int getBlindDurationTicks() {
        return num("karasu.transformation.blind-duration-ticks");
    }

    /** The engine the operator asked for, before availability is taken into account. */
    public ModelEngineType getModelEngineType() {
        return ModelEngineType.parse(config.getString("karasu.visual.model-engine"));
    }

    /** The engine actually in use. See {@link #setModelEngineType}. */
    public ModelEngineType getActiveModelEngineType() {
        return modelEngineType;
    }

    /** Records which engine was resolved, so the model getters resolve names against the right one. */
    public void setModelEngineType(ModelEngineType type) {
        this.modelEngineType = type;
    }

    public boolean isModelsEnabled() {
        return modelEngineType != ModelEngineType.NONE;
    }

    public boolean isSelfView() {
        return bool("karasu.visual.self-view");
    }

    public boolean isHideHeldItemFromSelf() {
        return bool("karasu.visual.hide-held-item-from-self");
    }

    public boolean isHideArmorFromSelf() {
        return bool("karasu.visual.hide-armor-from-self");
    }

    /** Whether the active engine tracks models under the BetterModel names or the ModelEngine ones. */
    private boolean usesBetterModelNames() {
        return modelEngineType == ModelEngineType.BETTERMODEL;
    }

    /** Model id for the airborne crow, resolved for the active engine. */
    public String getFlyingModelId() {
        return str(usesBetterModelNames()
                ? "karasu.visual.bettermodel.models.flying"
                : "karasu.visual.models.flying");
    }

    /** Model id for the perched crow, resolved for the active engine. */
    public String getPerchedModelId() {
        return str(usesBetterModelNames()
                ? "karasu.visual.bettermodel.models.perched"
                : "karasu.visual.models.perched");
    }

    /** Walking speed while morphed. Vanilla is 0.2; 0 roots the player. */
    public float getWalkSpeed() {
        return (float) dbl("karasu.transformation.walk-speed");
    }

    /** Whether the morph should attach as a BetterModel limb model rather than a general one. */
    public boolean isLimbModelsEnabled() {
        return bool("karasu.visual.bettermodel.use-limb-models");
    }

    /** Whether to also hide the morphed player's body via Bukkit, not just the model engine. */
    public boolean isSelfBodyHidden() {
        return bool("karasu.visual.hide-self-body");
    }

    /** Size of the crow model itself, decoupled from the player's scale attribute. */
    public double getModelScale() {
        return dbl("karasu.visual.model-scale");
    }

    /** Whether a morphed player is prevented from sneaking. */
    public boolean isSneakShiftDisabled() {
        return bool("karasu.visual.disable-sneak-shift");
    }

    /** Bone names BetterModel should not draw. See {@code karasu.visual.bettermodel.hidden-bones}. */
    public List<String> getHiddenBones() {
        Object value = raw("karasu.visual.bettermodel.hidden-bones");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    public String getEnterSound() {
        return str("karasu.sounds.enter-sound");
    }

    public String getExitSound() {
        return str("karasu.sounds.exit-sound");
    }

    public String getFlyingSound() {
        return str("karasu.sounds.flying-sound");
    }

    public String getSwarmSound() {
        return str("karasu.sounds.swarm-sound");
    }

    public boolean getUseResourcePack() {
        return bool("karasu.resource-pack.use-resourcepack");
    }

    public String getResourcePackUrl() {
        return str("karasu.resource-pack.url");
    }

    /**
     * Pack file this plugin manages, relative to the server root. Defaults to whichever pack the active
     * engine builds, since neither engine knows about the other's output.
     */
    public String getResourcePackFile() {
        String configured = str("karasu.resource-pack.pack-file");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return modelEngineType == ModelEngineType.MODELENGINE
                ? "plugins/ModelEngine/resource pack.zip"
                : "plugins/BetterModel/build.zip";
    }

    /** Re-reads the file, migrating it again so options added since the last boot show up. */
    public ConfigMigrator.Result reload() {
        ConfigMigrator.Result result = migrate();
        if (result.changed()) {
            report(result);
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
        return result;
    }
}