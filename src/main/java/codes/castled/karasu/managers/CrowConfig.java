package codes.castled.karasu.managers;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class CrowConfig {
    private static final String CONFIG_FILE = "karasu.yml";
    private static CrowConfig instance;
    private FileConfiguration config;
    private File configFile;
    
    private CrowConfig(JavaPlugin plugin) {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE, false);
        }
        
        this.config = YamlConfiguration.loadConfiguration(configFile);
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
    
    public double getBaseScale() {
        return config.getDouble("karasu.transformation.base-scale", 0.6);
    }
    
    public double getMorphFlightSpeed() {
        return config.getDouble("karasu.transformation.morph-flight-speed", 0.4);
    }

    public int getMaxCrowCount() {
        return config.getInt("karasu.transformation.max-crow-count", 5);
    }
    
    public int getDepartureCrowCount() {
        return config.getInt("karasu.swarm.departure-crows", 12);
    }

    public int getArrivalCrowCount() {
        return config.getInt("karasu.swarm.arrival-crows", 10);
    }

    public double getSwarmCrowScale() {
        return config.getDouble("karasu.swarm.crow-scale", 0.5);
    }

    public double getSwarmSpread() {
        return config.getDouble("karasu.swarm.spread", 8.0);
    }

    public int getDepartureTicks() {
        return config.getInt("karasu.swarm.departure-ticks", 30);
    }

    public int getArrivalTicks() {
        return config.getInt("karasu.swarm.arrival-ticks", 16);
    }

    public int getMannequinTicks() {
        return config.getInt("karasu.swarm.mannequin-ticks", 5);
    }

    public double getMinTeleportDistance() {
        return config.getDouble("karasu.swarm.min-teleport-distance", 4.0);
    }
    
    public int getBlindDurationTicks() {
        return config.getInt("karasu.transformation.blind-duration-ticks", 30);
    }
    
    public boolean isUseModelEngine() {
        return config.getBoolean("karasu.visual.use-model-engine", true);
    }
    
    public String getFlyingModelId() {
        return config.getString("karasu.visual.models.flying", "crow_fly");
    }
    
    public String getPerchedModelId() {
        return config.getString("karasu.visual.models.perched", "crow_stand");
    }
    
    public String getEnterSound() {
        return config.getString("karasu.sounds.enter-sound", "entity.bat.takeoff");
    }
    
    public String getExitSound() {
        return config.getString("karasu.sounds.exit-sound", "entity.bat.hurt");
    }
    
    public String getFlyingSound() {
        return config.getString("karasu.sounds.flying-sound", "entity.ender_dragon.flap");
    }
    
    public String getSwarmSound() {
        return config.getString("karasu.sounds.swarm-sound", "entity.witch.ambient");
    }
    
    public boolean getUseResourcePack() {
        return config.getBoolean("karasu.resource-pack.use-resourcepack", false);
    }
    
    public String getResourcePackUrl() {
        return config.getString("karasu.resource-pack.url", "");
    }
    
    public String getResourcePackFile() {
        return config.getString("karasu.resource-pack.pack-file", "plugins/ModelEngine/resource pack.zip");
    }
    
    public void reload(JavaPlugin plugin) {
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }
}