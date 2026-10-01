package codes.castled.karasu.managers;

import java.util.List;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.platform.PlatformEntity;
import kr.toxicity.model.api.platform.PlatformPlayer;
import kr.toxicity.model.api.tracker.EntityHideOption;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.TrackerModifier;
import kr.toxicity.model.api.tracker.TrackerUpdateAction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BetterModel-backed {@link CrowModelEngine}.
 *
 * <p>Karasu compiles against {@code bettermodel-bukkit-api} but reaches BetterModel through
 * softdepend, so the plugin may be absent at runtime. BetterModel publishes no "is initialised?"
 * accessor -- {@code BetterModel.platform()} throws a NullPointerException until the plugin's onLoad
 * has run -- so availability is probed once in the constructor and every call re-checks it.
 *
 * <p>The version skew this absorbs is deliberate: Karasu compiles against the published 3.5.0 API
 * surface but runs against whatever the server has installed, including the 3.5.1 build that targets
 * 26.3.
 */
public class BetterModelBridge implements CrowModelEngine {

    private static final String PLUGIN_NAME = "BetterModel";

    // A disguised player needs their armour and held item hidden as well as their body, or those pieces
    // render at the entity's real position while the model's bones are placed by BetterModel - which is
    // what leaves detached slivers hanging off the model. DEFAULT hides equipment, fire, body and glow.
    private static final EntityHideOption HIDE_BASE_BODY = EntityHideOption.DEFAULT;
    private static final EntityHideOption SHOW_BASE_BODY = EntityHideOption.FALSE;

    private final JavaPlugin plugin;
    private final List<String> hiddenBones;
    private final double modelScale;
    private final boolean limbModels;
    private boolean available;

    public BetterModelBridge(JavaPlugin plugin, CrowConfig config) {
        this.plugin = plugin;
        this.hiddenBones = config.getHiddenBones();
        this.modelScale = config.getModelScale();
        this.limbModels = config.isLimbModelsEnabled();
        if (Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) == null) {
            return;
        }
        try {
            // Touching any accessor throws until BetterModel.register() has run in its onLoad.
            BetterModel.modelKeys();
            available = true;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("BetterModel is installed but its API is not ready yet: " + e);
        }
    }

    /**
     * Reports what BetterModel actually computes for an entity's live tracker, for {@code /karasu
     * debug}. Reads the tracker rather than the config, so it distinguishes "the config never reached
     * the bridge" from "the value is computed but not rendered".
     */
    public String describeScaler(Entity entity) {
        if (!available || entity == null) return "unavailable";
        EntityTrackerRegistry registry = EntityTrackerRegistry.registry(entity.getUniqueId());
        if (registry == null) return "no registry";
        EntityTracker tracker = registry.first();
        if (tracker == null) return "no tracker";
        return tracker.scaler().name() + " -> " + tracker.scaler().scale(tracker)
                + " (model " + tracker.name() + ")";
    }

    /** The model scale this bridge was built with, captured at construction. */
    public double modelScale() {
        return modelScale;
    }

    /** The bone names this bridge hides, captured at construction. */
    public List<String> hiddenBones() {
        return hiddenBones;
    }

    @Override
    public String name() {
        return "BetterModel";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean applyModel(Entity entity, String modelId) {
        return attach(entity, modelId, modelScale, true);
    }

    @Override
    public boolean applyModel(Entity entity, String modelId, double scale) {
        return attach(entity, modelId, scale, true);
    }

    @Override
    public boolean applyModel(Entity entity, String modelId, boolean selfView) {
        return attach(entity, modelId, modelScale, selfView);
    }

    /**
     * @param scale absolute model scale, never null: the crow is sized independently of the entity's
     *              SCALE attribute, which would otherwise drag the model's anchor down with the player
     * @param selfView whether a player wearing this model sees it themselves
     */
    private boolean attach(Entity entity, String modelId, double scale, boolean selfView) {
        if (!available || entity == null || modelId == null || !entity.isValid()) return false;

        boolean asLimb = limbModels && entity instanceof Player;
        ModelRenderer renderer = resolve(modelId, asLimb);
        if (renderer == null) {
            plugin.getLogger().warning("[Karasu] BetterModel has no "
                    + (asLimb ? "limb" : "model") + " named \"" + modelId + "\" - it must live in "
                    + (asLimb ? "plugins/BetterModel/players/" : "plugins/BetterModel/models/")
                    + ", and matches karasu.visual.bettermodel.models. Reload BetterModel after adding it.");
            return false;
        }

        PlatformEntity target = BukkitAdapter.adapt(entity);
        // The scaler has to be set BEFORE the tracker's first spawn. getOrCreate spawns for every
        // viewer during construction, and a bone only computes its transform when marked dirty - which
        // an unanimated model never does, so that first spawn is the only time the scale is ever sent.
        // forceUpdate cannot rescue it afterwards: dirtyUpdate only re-sends entity data, not the
        // transformation. Setting it here means the very first transform already carries the right
        // scale. The unconditional set below then covers a tracker that already existed.
        EntityTracker tracker = renderer.getOrCreate(target, TrackerModifier.DEFAULT, t -> {
            t.scaler(ModelScaler.value((float) scale));
            t.hideOption(HIDE_BASE_BODY);
        });
        // Applied unconditionally rather than in getOrCreate's creation callback: that callback only
        // runs when the tracker is created, so a tracker that outlived a config change would keep its
        // old scale and hide options no matter what the config now says.
        //
        // The scaler is set explicitly rather than left at BetterModel's default. The default is
        // ModelScaler.entity(), which multiplies by the entity's SCALE attribute; because that
        // attribute is also what shrinks a morphed player, the two were coupled and a 0.6 player sank
        // the model into the ground. karasu.visual.model-scale sizes the crow on its own.
        tracker.scaler(ModelScaler.value((float) scale));
        // Karasu always wants the model to replace the entity's own body.
        tracker.hideOption(HIDE_BASE_BODY);
        hideHiddenBones(tracker);
        if (entity instanceof Player player) {
            PlatformPlayer owner = BukkitAdapter.adapt(player);
            if (selfView) {
                // Tracker.show() only un-hides a model that is already hidden from the viewer, so it
                // does nothing here. The real problem is upstream: BetterModel discovers a viewer when
                // their client receives an AddEntity packet for the entity, and a client only ever
                // receives that once for its own player - at join, before any tracker exists. So the
                // wearer is never discovered and never spawned for. spawnIfNotSpawned registers the
                // viewer explicitly and spawns, which is the only entry point that does so.
                tracker.registry().spawnIfNotSpawned(owner);
            } else {
                tracker.hide(owner);
            }
        }
        // A bone only recomputes and sends its transform when marked dirty (RenderedBone's
        // updateCurrent guard), and changing the scaler marks nothing dirty - so without this the new
        // model-scale sits on the server and never reaches the client. Must run after the spawn above,
        // since a forced update with no viewers yet is a no-op.
        tracker.forceUpdate(true);
        // INFO, not FINE: attach runs on morph and on every fly/land model swap, so this is a handful
        // of lines per minute, and it is the only way to see the scale actually in force.
        logAttach(entity, modelId, scale, selfView, asLimb, tracker);
        return !tracker.isClosed();
    }

    private void logAttach(Entity entity, String modelId, double scale, boolean selfView,
                           boolean asLimb, EntityTracker tracker) {
        String playerScale = "-";
        if (entity instanceof org.bukkit.attribute.Attributable attributable) {
            var attribute = attributable.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            if (attribute != null) {
                playerScale = String.valueOf(attribute.getBaseValue());
            }
        }
        plugin.getLogger().info("[Karasu] attach " + modelId + " to " + entity.getType()
                + " | model-scale=" + scale
                + " | playerScaleAttr=" + playerScale
                + " | scalerNow=" + tracker.scaler().name() + "->" + tracker.scaler().scale(tracker)
                + " | selfView=" + selfView + (asLimb ? " limb" : ""));
    }

    /**
     * Hides the bones the operator listed, which is how a model that bakes its hitbox in as visible
     * geometry gets cleaned up. Only the display is hidden, so the bone keeps working for hit detection.
     */
    private void hideHiddenBones(EntityTracker tracker) {
        if (hiddenBones.isEmpty()) return;
        tracker.update(
                TrackerUpdateAction.togglePart(false),
                bone -> hiddenBones.contains(bone.name().rawName()));
    }

    /**
     * The registry keys its trackers by model id, so removing the entry also closes the tracker,
     * which is what despawns the model for every viewer. Looking up by UUID is deliberate: the
     * BaseEntity overload would create a registry for entities that have no model attached.
     */
    @Override
    public boolean removeModel(Entity entity, String modelId) {
        if (!available || entity == null || modelId == null) return false;
        EntityTrackerRegistry registry = EntityTrackerRegistry.registry(entity.getUniqueId());
        return registry != null && registry.remove(modelId);
    }

    @Override
    public boolean isModelLoaded(String modelId) {
        if (!available || modelId == null) return false;
        // Report on both, so /karasu debug is useful whichever mode is configured.
        return BetterModel.limbOrNull(modelId) != null || BetterModel.modelOrNull(modelId) != null;
    }

    private ModelRenderer resolve(String modelId, boolean asLimb) {
        return asLimb ? BetterModel.limbOrNull(modelId) : BetterModel.modelOrNull(modelId);
    }

    @Override
    public void setBaseEntityVisible(Entity entity, boolean visible) {
        if (!available || entity == null) return;
        EntityTrackerRegistry registry = EntityTrackerRegistry.registry(entity.getUniqueId());
        if (registry == null) return;
        // Applies to every tracker on the entity, since a player can wear the flying and perched models at
        // different times and only the one currently attached is the one that matters.
        EntityHideOption option = visible ? SHOW_BASE_BODY : HIDE_BASE_BODY;
        for (EntityTracker tracker : registry.trackers()) {
            tracker.hideOption(option);
        }
    }

    /**
     * BetterModel has no owner exclusion in its spawn path -- a player tracker is spawned for the
     * entity's owner too -- so self view is on by default and turning it off means hiding the model
     * from that one viewer.
     */
    @Override
    public void setSelfView(Player player, boolean selfView) {
        if (!available || player == null) return;
        EntityTrackerRegistry registry = EntityTrackerRegistry.registry(player.getUniqueId());
        if (registry == null) return;
        PlatformPlayer viewer = BukkitAdapter.adapt(player);
        for (EntityTracker tracker : registry.trackers()) {
if (selfView) {
                tracker.show(viewer);
            } else {
                tracker.hide(viewer);
            }
        }
    }
}