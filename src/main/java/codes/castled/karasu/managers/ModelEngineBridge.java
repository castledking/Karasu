package codes.castled.karasu.managers;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

public class ModelEngineBridge {
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

    public ModelEngineBridge(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("ModelEngine") == null) {
            plugin.getLogger().warning("ModelEngine not found - crow model disguises disabled.");
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

            enabled = true;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to initialize ModelEngine bridge: " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean applyModel(Entity entity, String modelId) {
        return applyModel(entity, modelId, 1.0);
    }

    public boolean applyModel(Entity entity, String modelId, double scale) {
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

    public boolean isBlueprintAvailable(String modelId) {
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

    public void setBaseEntityVisible(Entity entity, boolean visible) {
        if (!enabled || entity == null) return;
        try {
            Object modeled = getOrCreateModeledEntity.invoke(null, entity);
            setBaseEntityVisible.invoke(modeled, visible);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}