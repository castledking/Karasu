package codes.castled.karasu;

import codes.castled.karasu.commands.KarasuCommand;
import codes.castled.karasu.hooks.SelfItemHider;
import codes.castled.karasu.hooks.UnlimitedNametagsHook;
import codes.castled.karasu.listeners.CrowTransformationListener;
import codes.castled.karasu.listeners.PackJoinListener;
import codes.castled.karasu.managers.BetterModelBridge;
import codes.castled.karasu.managers.CrowConfig;
import codes.castled.karasu.managers.ConfigMigrator;
import codes.castled.karasu.managers.CrowEffectManager;
import codes.castled.karasu.managers.CrowModelEngine;
import codes.castled.karasu.managers.ModelEngineBridge;
import codes.castled.karasu.managers.ModelEngineType;
import codes.castled.karasu.managers.NoopCrowModelEngine;
import codes.castled.karasu.managers.VanillaCrowModelEngine;
import codes.castled.karasu.pack.ResourcePackService;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public class KarasuPlugin extends JavaPlugin {
    private static KarasuPlugin instance;
    
    private CrowEffectManager crowEffectManager;
    private CrowTransformationListener crowListener;
    private CrowModelEngine crowModelEngine;
    private ModelEngineBridge modelEngineBridge;
    private BetterModelBridge betterModelBridge;
    private VanillaCrowModelEngine vanillaEngine;
    private ResourcePackService resourcePackService;
    private UnlimitedNametagsHook nametagHook;
    private SelfItemHider selfItemHider;

    public static KarasuPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        
        CrowConfig.initialize(this);
        
        CrowConfig config = CrowConfig.getInstance();
        crowModelEngine = resolveEngine(config);
        
        if (getServer().getPluginManager().isPluginEnabled("UnlimitedNameTags")) {
            nametagHook = new UnlimitedNametagsHook();
            getServer().getPluginManager().registerEvents(nametagHook, this);
        }
        boolean hideAnyItems = config.isHideHeldItemFromSelf() || config.isHideArmorFromSelf();
        if (config.isSelfView() && hideAnyItems && getServer().getPluginManager().isPluginEnabled("packetevents")) {
            selfItemHider = new SelfItemHider(this, config.isHideHeldItemFromSelf(), config.isHideArmorFromSelf());
            selfItemHider.register();
        }
        crowEffectManager = new CrowEffectManager(this);
        crowListener = new CrowTransformationListener(this);
        resourcePackService = new ResourcePackService(this, config);
        resourcePackService.setup();
        
        getServer().getPluginManager().registerEvents(crowListener, this);
        getServer().getPluginManager().registerEvents(new PackJoinListener(resourcePackService), this);

        KarasuCommand karasu = new KarasuCommand(this);
        getCommand("karasu").setExecutor(karasu);
        getCommand("karasu").setTabCompleter(karasu);

        getLogger().info("Karasu - Crow Transformation Plugin enabled!");
        getLogger().info("Commands: /karasu morph | info | spawn [count] | debug | reload");
    }

    /**
     * Re-reads the config, re-picks the model engine and re-attaches the crow model to anyone currently
     * transformed, without making them re-morph.
     *
     * @return one message per thing that changed, for the caller to relay to whoever ran it
     */
    public List<String> reloadKarasu() {
        CrowConfig config = getCrowConfig();
        // Detach under the engine that attached the models, or a switch would strand the old ones.
        crowEffectManager.detachModels();
        ConfigMigrator.Result result = config.reload();
        crowModelEngine = resolveEngine(config);
        crowEffectManager.setEngine(crowModelEngine);
        crowEffectManager.refreshModels();

        List<String> messages = new ArrayList<>();
        messages.add("§aReloaded karasu.yml.");
        if (!result.added().isEmpty()) {
            messages.add("§eAdded missing options: §f" + String.join("§e, §f", result.added()));
        }
        if (!result.removed().isEmpty()) {
            messages.add("§eRemoved obsolete options: §f" + String.join("§e, §f", result.removed()));
        }
        messages.add("§eModel engine: §f" + crowModelEngine.name());
        return messages;
    }

    public CrowConfig getCrowConfig() {
        return CrowConfig.getInstance();
    }

    /**
     * Picks the model engine to draw crows with. Both bridges are constructed regardless of what was
     * asked for, so {@code /karasu debug} can report on the one that was passed over, but only the
     * winner is returned; the loser stays inert. Records the outcome in the config so model names
     * resolve against the engine that actually won rather than the one that was requested.
     */
    private CrowModelEngine resolveEngine(CrowConfig config) {
        ModelEngineType requested = config.getModelEngineType();

        if (requested == ModelEngineType.NONE) {
            config.setModelEngineType(ModelEngineType.NONE);
            getLogger().info("Model engine: none (karasu.visual.model-engine is 'none').");
            return new NoopCrowModelEngine("disabled in config");
        }

        if (requested == ModelEngineType.VANILLA) {
            // Bat mode needs no model plugin at all, so it is settled before either bridge is probed.
            vanillaEngine = new VanillaCrowModelEngine(this);
            config.setModelEngineType(ModelEngineType.VANILLA);
            getLogger().info("Model engine: " + vanillaEngine.name() + " (requested vanilla).");
            return vanillaEngine;
        }

        betterModelBridge = new BetterModelBridge(this, config);
        modelEngineBridge = new ModelEngineBridge(this);
        boolean hasBetterModel = betterModelBridge.isAvailable();
        boolean hasModelEngine = modelEngineBridge.isAvailable();

        ModelEngineType resolved;
        switch (requested) {
            case BETTERMODEL -> resolved = ModelEngineType.BETTERMODEL;
            case MODELENGINE -> resolved = ModelEngineType.MODELENGINE;
            default -> resolved = hasBetterModel ? ModelEngineType.BETTERMODEL : ModelEngineType.MODELENGINE;
        }
        config.setModelEngineType(resolved);

        boolean chosen = switch (resolved) {
            case BETTERMODEL -> hasBetterModel;
            case MODELENGINE -> hasModelEngine;
            default -> false;
        };

        if (!chosen) {
            // The operator pinned an engine that is not usable. Say exactly which one and why rather
            // than falling back silently, since a silent fallback would hide the misconfiguration.
            getLogger().warning("karasu.visual.model-engine is '" + requested.name().toLowerCase()
                    + "' but that plugin is not usable: BetterModel=" + hasBetterModel
                    + ", ModelEngine=" + hasModelEngine
                    + ". Crows will transform with no model. Set it to 'auto' to pick whichever is installed.");
            // Record NONE so the config reflects what is actually in use; anything else would leave the
            // model-name getters resolving against an engine that never got a chance to load anything.
            config.setModelEngineType(ModelEngineType.NONE);
            return new NoopCrowModelEngine(requested.name().toLowerCase() + " was requested but is unavailable");
        }

        CrowModelEngine engine = resolved == ModelEngineType.BETTERMODEL ? betterModelBridge : modelEngineBridge;
        String otherEngine = resolved == ModelEngineType.BETTERMODEL
                ? "BetterModel won, ModelEngine=" + hasModelEngine
                : "ModelEngine won, BetterModel=" + hasBetterModel;
        getLogger().info("Model engine: " + engine.name() + " (requested " + requested.name().toLowerCase()
                + "; " + otherEngine + ").");
        return engine;
    }

    @Override
    public void onDisable() {
        if (crowEffectManager != null) {
            crowEffectManager.cleanup();
        }
        if (selfItemHider != null) {
            selfItemHider.unregister();
        }
        if (vanillaEngine != null) {
            // A reload into another engine must not strand the previous run's bats.
            vanillaEngine.shutdown();
            vanillaEngine = null;
        }
        getLogger().info("Karasu - Crow Transformation Plugin disabled!");
    }
    
    public CrowEffectManager getCrowEffectManager() {
        return crowEffectManager;
    }

    /** The engine drawing crows, never null; a {@link NoopCrowModelEngine} when none is in use. */
    public CrowModelEngine getCrowModelEngine() {
        return crowModelEngine;
    }

    /** Null unless ModelEngine was probed at startup; for {@code /crows debug}. */
    public ModelEngineBridge getModelEngineBridge() {
        return modelEngineBridge;
    }

    /** Null unless bat mode was selected; for {@code /crows debug}. */
    public VanillaCrowModelEngine getVanillaEngine() {
        return vanillaEngine;
    }

    /** Null unless BetterModel was probed at startup; for {@code /crows debug}. */
    public BetterModelBridge getBetterModelBridge() {
        return betterModelBridge;
    }

    /** Null when UnlimitedNameTags is not installed. */
    public UnlimitedNametagsHook getNametagHook() {
        return nametagHook;
    }
    
    /** Null when packetevents is not installed or item hiding is disabled. */
    public SelfItemHider getSelfItemHider() {
        return selfItemHider;
    }
}