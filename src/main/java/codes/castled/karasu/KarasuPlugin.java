package codes.castled.karasu;

import codes.castled.karasu.commands.CrowsCommand;
import codes.castled.karasu.commands.CrowCommand;
import codes.castled.karasu.hooks.UnlimitedNametagsHook;
import codes.castled.karasu.listeners.CrowTransformationListener;
import codes.castled.karasu.listeners.PackJoinListener;
import codes.castled.karasu.managers.CrowConfig;
import codes.castled.karasu.managers.CrowEffectManager;
import codes.castled.karasu.managers.ModelEngineBridge;
import codes.castled.karasu.pack.ResourcePackService;
import org.bukkit.plugin.java.JavaPlugin;

public class KarasuPlugin extends JavaPlugin {
    private static KarasuPlugin instance;
    
    private CrowEffectManager crowEffectManager;
    private CrowTransformationListener crowListener;
    private ModelEngineBridge modelEngineBridge;
    private ResourcePackService resourcePackService;
    private UnlimitedNametagsHook nametagHook;

    public static KarasuPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        
        CrowConfig.initialize(this);
        
        modelEngineBridge = new ModelEngineBridge(this);
        if (getServer().getPluginManager().isPluginEnabled("UnlimitedNameTags")) {
            nametagHook = new UnlimitedNametagsHook();
            getServer().getPluginManager().registerEvents(nametagHook, this);
        }
        crowEffectManager = new CrowEffectManager(this);
        crowListener = new CrowTransformationListener(this);
        resourcePackService = new ResourcePackService(this, CrowConfig.getInstance());
        resourcePackService.setup();
        
        getServer().getPluginManager().registerEvents(crowListener, this);
        getServer().getPluginManager().registerEvents(new PackJoinListener(resourcePackService), this);

        getCommand("crows").setExecutor(new CrowsCommand(this));
        getCommand("crows").setTabCompleter(new CrowsCommand(this));
        getCommand("crow").setExecutor(new CrowCommand(this));
        getCommand("crow").setTabCompleter(new CrowCommand(this));
        
        getLogger().info("Karasu - Crow Transformation Plugin enabled!");
        getLogger().info("Commands: /crows morph, /crow spawn");
    }

    @Override
    public void onDisable() {
        if (crowEffectManager != null) {
            crowEffectManager.cleanup();
        }
        getLogger().info("Karasu - Crow Transformation Plugin disabled!");
    }
    
    public CrowEffectManager getCrowEffectManager() {
        return crowEffectManager;
    }
    
    public ModelEngineBridge getModelEngineBridge() {
        return modelEngineBridge;
    }

    /** Null when UnlimitedNameTags is not installed. */
    public UnlimitedNametagsHook getNametagHook() {
        return nametagHook;
    }
}