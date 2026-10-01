package codes.castled.karasu.managers;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ModelEngine-backed {@link CrowModelEngine}, bound reflectively.
 *
 * <p>Nothing here is a compile-time dependency on ModelEngine, so Karasu builds without ModelEngine's
 * premium jar. That also means the reflection below silently stops matching when ModelEngine changes
 * shape, which is why it degrades to unavailable rather than throwing: ModelEngine has not been
 * updated for 26.3 yet, and a server running Karasu on 26.3 will normally prefer BetterModel anyway.
 */
public class ModelEngineBridge implements CrowModelEngine {
    private static final String API_CLASS = "com.ticxo.modelengine.api.ModelEngineAPI";

    private boolean enabled = false;
    private Class<?> apiClass;
    private Method getOrCreateModeledEntity;
    private Method createActiveModel;
    private Method getBlueprint;
    private Method addModel;
    private Method removeModel;
    private Method getModel;
    private Method setBaseEntityVisible;
    private Method setAutoRendererInitialization;
    private Method destroyModel;
    private Method setScale;
    private Method getBase;
    private Method getData;
    private Class<?> bukkitEntityDataClass;
    private Method getTracked;
    private Method addForcedPairing;
    private Method removeForcedPairing;
    private Method getEntityHandler;
    private Method setForcedInvisible;

    public ModelEngineBridge(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("ModelEngine") == null) {
            // Not an error: the caller may well have picked BetterModel instead.
            return;
        }
        try {
            apiClass = Class.forName(API_CLASS);
            getOrCreateModeledEntity = apiClass.getMethod("getOrCreateModeledEntity", Entity.class);
            createActiveModel = apiClass.getMethod("createActiveModel", String.class);
            getBlueprint = apiClass.getMethod("getBlueprint", String.class);
            setAutoRendererInitialization = Class.forName("com.ticxo.modelengine.api.model.ActiveModel")
                    .getMethod("setAutoRendererInitialization", boolean.class);

            Class<?> modeledEntityClass = apiClass.getClassLoader().loadClass("com.ticxo.modelengine.api.model.ModeledEntity");
            Class<?> activeModelClass = apiClass.getClassLoader().loadClass("com.ticxo.modelengine.api.model.ActiveModel");

            addModel = modeledEntityClass.getMethod("addModel", activeModelClass, boolean.class);
            removeModel = modeledEntityClass.getMethod("removeModel", String.class);
            getModel = modeledEntityClass.getMethod("getModel", String.class);
            setBaseEntityVisible = modeledEntityClass.getMethod("setBaseEntityVisible", boolean.class);
            destroyModel = activeModelClass.getMethod("destroy");
            setScale = activeModelClass.getMethod("setScale", double.class);

            ClassLoader loader = apiClass.getClassLoader();
            getBase = modeledEntityClass.getMethod("getBase");
            getData = loader.loadClass("com.ticxo.modelengine.api.entity.BaseEntity").getMethod("getData");
            bukkitEntityDataClass = loader.loadClass("com.ticxo.modelengine.api.entity.data.BukkitEntityData");
            getTracked = bukkitEntityDataClass.getMethod("getTracked");
            Class<?> trackedEntityClass = loader.loadClass("com.ticxo.modelengine.api.nms.entity.wrapper.TrackedEntity");
            addForcedPairing = trackedEntityClass.getMethod("addForcedPairing", java.util.UUID.class);
            removeForcedPairing = trackedEntityClass.getMethod("removeForcedPairing", java.util.UUID.class);
            getEntityHandler = apiClass.getMethod("getEntityHandler");
            setForcedInvisible = loader.loadClass("com.ticxo.modelengine.api.nms.entity.EntityHandler")
                    .getMethod("setForcedInvisible", Entity.class, boolean.class);

            enabled = true;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to initialize ModelEngine bridge: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "ModelEngine";
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    /** Kept as an alias for call sites that predate the {@link CrowModelEngine} abstraction. */
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean applyModel(Entity entity, String modelId) {
        return applyModel(entity, modelId, 1.0);
    }

    @Override
    public boolean applyModel(Entity entity, String modelId, double scale) {
        return attach(entity, modelId, scale);
    }

    @Override
    public boolean applyModel(Entity entity, String modelId, boolean selfView) {
        // ModelEngine only renders a model to players tracking its base entity, which never includes the
        // entity itself. Force-pair the owner first so the model's initial spawn is sent to them too.
        if (enabled && selfView && entity instanceof Player player) {
            setSelfView(player, true);
        }
        return attach(entity, modelId, 1.0);
    }

    private boolean attach(Entity entity, String modelId, double scale) {
        if (!enabled) return false;
        if (entity == null || modelId == null) return false;
        try {
            Object modeled = getOrCreateModeledEntity.invoke(null, entity);
            if (modeled == null) {
                Bukkit.getLogger().warning("[Karasu] ModelEngine: getOrCreateModeledEntity returned null for " + entity.getType());
                return false;
            }
            Object active = createActiveModel.invoke(null, modelId);
            if (active == null) {
                Bukkit.getLogger().warning("[Karasu] ModelEngine: createActiveModel(\"" + modelId + "\") returned null - blueprint not loaded?");
                return false;
            }
            setAutoRendererInitialization.invoke(active, true);
            if (scale != 1.0) {
                setScale.invoke(active, scale);
            }
            addModel.invoke(modeled, active, false);
            if (!hasModel(modeled, modelId)) {
                Bukkit.getLogger().warning("[Karasu] ModelEngine: model \"" + modelId + "\" is not attached to " + entity.getType());
                return false;
            }
            return true;
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().warning("[Karasu] ModelEngine bridge call failed: " + e);
            return false;
        }
    }

    private boolean hasModel(Object modeled, String modelId) {
        try {
            Object result = getModel.invoke(modeled, modelId);
            return result instanceof java.util.Optional<?> optional && optional.isPresent();
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Override
    public boolean isModelLoaded(String modelId) {
        if (!enabled || modelId == null) return false;
        try {
            return getBlueprint.invoke(null, modelId) != null;
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().warning("[Karasu] ModelEngine getBlueprint call failed: " + e);
            return false;
        }
    }

    public boolean isBridgeEnabled() {
        return enabled;
    }

    @Override
    public boolean removeModel(Entity entity, String modelId) {
        if (!enabled || entity == null || modelId == null) return false;
        try {
            Object modeled = getOrCreateModeledEntity.invoke(null, entity);
            if (modeled == null) return false;
            // removeModel only detaches; the ActiveModel must be destroyed or its bones stay rendered where they were.
            Object removed = removeModel.invoke(modeled, modelId);
            if (removed instanceof java.util.Optional<?> optional && optional.isPresent()) {
                destroyModel.invoke(optional.get());
            }
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /**
     * Lets a player see their own model, the same way ModelEngine's /meg disguise does. ModelEngine only
     * renders a model to players tracking its base entity, which never includes the entity itself, so the
     * owner is force-paired as a viewer; their own player body is made invisible so only the model shows.
     */
    @Override
    public void setSelfView(Player player, boolean selfView) {
        if (!enabled || player == null) return;
        try {
            Object modeled = getOrCreateModeledEntity.invoke(null, player);
            if (modeled == null) return;
            Object data = getData.invoke(getBase.invoke(modeled));
            if (bukkitEntityDataClass.isInstance(data)) {
                Object tracked = getTracked.invoke(data);
                (selfView ? addForcedPairing : removeForcedPairing).invoke(tracked, player.getUniqueId());
            }
            setForcedInvisible.invoke(getEntityHandler.invoke(null), player, selfView);
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().warning("[Karasu] ModelEngine self-view call failed: " + e);
        }
    }

    @Override
    public void setBaseEntityVisible(Entity entity, boolean visible) {
        if (!enabled || entity == null) return;
        try {
            Object modeled = getOrCreateModeledEntity.invoke(null, entity);
            setBaseEntityVisible.invoke(modeled, visible);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}