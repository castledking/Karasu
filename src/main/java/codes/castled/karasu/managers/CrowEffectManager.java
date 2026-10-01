package codes.castled.karasu.managers;

import codes.castled.karasu.KarasuPlugin;
import codes.castled.karasu.effects.CrowSwarm;
import codes.castled.karasu.hooks.SelfItemHider;
import codes.castled.karasu.hooks.UnlimitedNametagsHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrowEffectManager {
    private static final float VANILLA_FLY_SPEED = 0.1f;
    private static final float VANILLA_WALK_SPEED = 0.2f;
    // Long enough for the entity to pick up a SCALE change before BetterModel reads its dimensions.
    private static final long MODEL_ATTACH_DELAY_TICKS = 2L;
    private final KarasuPlugin plugin;
    private final CrowConfig config;
    private CrowModelEngine engine;
    private final UnlimitedNametagsHook nametags;
    private final SelfItemHider selfItemHider;
    private final CrowSwarm swarm;
    private final ConcurrentHashMap<UUID, CrowState> playerStates = new ConcurrentHashMap<>();
    private final BukkitTask modelSyncTask;

    public CrowEffectManager(KarasuPlugin plugin) {
        this.plugin = plugin;
        this.config = CrowConfig.getInstance();
        this.engine = plugin.getCrowModelEngine();
        this.nametags = plugin.getNametagHook();
        this.selfItemHider = plugin.getSelfItemHider();
        this.swarm = new CrowSwarm(plugin, config, engine);
        this.modelSyncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncModels, 2, 2);
    }
    
    public void toggleTransformation(Player player) {
        UUID playerId = player.getUniqueId();
        CrowState state = playerStates.get(playerId);
        
        if (state == null) {
            state = new CrowState();
            state.transformed = true;
            playerStates.put(playerId, state);
            
            applyTransformation(player, state);
            player.sendMessage("§aYou have been transformed into a crow!");
        } else {
            if (state.blindnessActive) {
                player.sendMessage("§cWait for the transformation to finish first!");
                return;
            }

            playerStates.remove(playerId);
            removeTransformation(player, state);
            arrive(player);
            player.sendMessage("§aYou have returned to your normal form!");
        }
    }
    
    // Swaps to crow_fly on take-off, and back to crow_stand only once the player has landed.
    private void syncModels() {
        playerStates.forEach((playerId, state) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) return;

            // The sneak listener only sees new toggles, so this also catches a player who was already
            // crouching when they morphed.
            if (config.isSneakShiftDisabled() && player.isSneaking()) {
                player.setSneaking(false);
            }

            boolean flying;
            if (!state.flyingMode && player.isFlying()) {
                flying = true;
            } else if (state.flyingMode && !player.isFlying() && player.isOnGround()) {
                flying = false;
            } else {
                return;
            }

            state.flyingMode = flying;
            engine.removeModel(player, flying ? config.getPerchedModelId() : config.getFlyingModelId());
            applyModelToPlayer(player, state);
        });
    }
    
    public void spawnCrows(Player player, int count) {
        if (!player.hasPermission("karasu.crow.spawn")) {
            player.sendMessage("§cYou don't have permission to spawn crows!");
            return;
        }
        
        int spawned = Math.min(count, config.getMaxCrowCount());
        swarm.burst(player.getLocation(), spawned, 0.2, 1.7);

        player.sendMessage("§aSpawned " + spawned + " crows!");
    }

    // Called before the teleport is applied, so the player is still at the origin.
    public void onPlayerTeleport(Player player, Location from) {
        swarm.depart(player, from, !isTransformed(player));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) arrive(player);
        }, 1);
    }

    public void onPlayerEnterSpectator(Player player) {
        swarm.depart(player, player.getLocation(), !isTransformed(player));
    }

    // Called before the game mode changes; wait a tick so the player is visible again.
    public void onPlayerExitSpectator(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) arrive(player);
        }, 1);
    }

    // Crows fly in and converge onto the player (no mannequin: the player is already there).
    private void arrive(Player player) {
        boolean crow = isTransformed(player);
        swarm.converge(player, config.getArrivalCrowCount(), crow ? 0.1 : 0.2, crow ? 0.6 : 1.7);
    }

    public boolean isTransformed(Player player) {
        return playerStates.containsKey(player.getUniqueId());
    }
    
    public void showInfo(Player player) {
        UUID playerId = player.getUniqueId();
        CrowState state = playerStates.get(playerId);
        
        player.sendMessage("§8=== §6Crow Transformation Info §8===");
        if (state != null) {
            player.sendMessage("§eStatus: §aTransformed");
            player.sendMessage("§eModel: §a" + getCurrentModelId(player));
            player.sendMessage("§eScale: §a" + config.getBaseScale());
            player.sendMessage("§eBlindness: " + (state.blindnessActive ? "§aActive" : "§cInactive"));
        } else {
            player.sendMessage("§cYou are not transformed! Use /crows morph.");
        }
    }
    
    private void applyTransformation(Player player, CrowState state) {
        player.addScoreboardTag("karasu_transformed");
        
        setPlayerScale(player, config.getBaseScale());

        state.originalWalkSpeed = player.getWalkSpeed();
        player.setWalkSpeed(config.getWalkSpeed());
        // No jumping while perched; double-tap space still toggles flight for players allowed to fly.
        setJumpStrength(player, 0);
        state.originalFlySpeed = player.getFlySpeed();
        player.setFlySpeed((float) Math.max(0, Math.min(1, VANILLA_FLY_SPEED * config.getMorphFlightSpeed())));

        state.flyingMode = player.isFlying();
        attachModelWhenScaleSettles(player, state);
        if (nametags != null) nametags.hide(player);
        if (selfItemHider != null) selfItemHider.hide(player);
        // The model engine hides the body through its own entity data, but that does not appear to
        // reach the player viewing themselves. Bukkit's invisible flag is a separate path.
        if (config.isSelfBodyHidden()) {
            player.setInvisible(true);
        }
        
        startBlindness(player, config.getBlindDurationTicks() + 10);
    }
    
    private void setPlayerScale(Player player, double value) {
        org.bukkit.attribute.AttributeInstance scale =
            player.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(value);
        }
    }

    private void setJumpStrength(Player player, double value) {
        org.bukkit.attribute.AttributeInstance jump =
            player.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.setBaseValue(value);
        }
    }

    private void resetJumpStrength(Player player) {
        org.bukkit.attribute.AttributeInstance jump =
            player.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.setBaseValue(jump.getDefaultValue());
        }
    }
    
    /**
     * Attaches the model a tick or two after the scale change rather than in the same tick.
     *
     * <p>BetterModel builds a tracker's bone anchors from the entity's dimensions, and those only
     * reflect a SCALE change once the entity has ticked. Attaching immediately anchors the model about a
     * block below where it belongs, and the only reason it looked right on later swaps was that
     * {@link #syncModels} re-attaches several ticks after take-off and landing.
     */
    private void attachModelWhenScaleSettles(Player player, CrowState state) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // The player may have un-morphed, or morphed again, while this was queued.
            if (playerStates.get(player.getUniqueId()) != state) {
                return;
            }
            applyModelToPlayer(player, state);
        }, MODEL_ATTACH_DELAY_TICKS);
    }

    private void applyModelToPlayer(Player player, CrowState state) {
        if (!config.isModelsEnabled() || !engine.isAvailable()) {
            return;
        }
        String modelId = state.flyingMode ? config.getFlyingModelId() : config.getPerchedModelId();
        engine.setBaseEntityVisible(player, false);
        // Self view rides along with the attach: each engine has to apply it at a different point
        // relative to the model existing, so it is the engine's business, not the caller's.
        engine.applyModel(player, modelId, config.isSelfView());
    }

    /**
     * Takes the crow model off every transformed player without touching their attributes, so a reload
     * can re-attach under a newly resolved engine instead of leaving the old engine's model behind.
     */
    public void detachModels() {
        playerStates.forEach((playerId, state) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) return;
            engine.removeModel(player, config.getFlyingModelId());
            engine.removeModel(player, config.getPerchedModelId());
        });
    }

    /** Re-attaches the crow model for everyone currently transformed, e.g. after {@code /karasu reload}. */
    public void refreshModels() {
        playerStates.forEach((playerId, state) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) applyModelToPlayer(player, state);
        });
    }

    /** Re-points this manager and its swarm at a different engine, after a config reload. */
    public void setEngine(CrowModelEngine engine) {
        this.engine = engine;
        this.swarm.setEngine(engine);
    }
    
    private void removeTransformation(Player player, CrowState state) {
        restorePlayer(player, state);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);

        startBlindness(player, config.getBlindDurationTicks());
    }

    // Undoes the crow model and attribute changes; these persist in player data if left applied.
    private void restorePlayer(Player player, CrowState state) {
        player.removeScoreboardTag("karasu_transformed");

        engine.removeModel(player, config.getFlyingModelId());
        engine.removeModel(player, config.getPerchedModelId());
        engine.setSelfView(player, false);
        engine.setBaseEntityVisible(player, true);
        if (nametags != null) nametags.show(player);
        if (selfItemHider != null) selfItemHider.show(player);
        player.setInvisible(false);

        setPlayerScale(player, 1.0);
        resetJumpStrength(player);
        // Restore whatever the player had, not a hardcoded 0.2, so /speed or another plugin survives.
        player.setWalkSpeed(state != null ? state.originalWalkSpeed : VANILLA_WALK_SPEED);
        if (state != null) {
            player.setFlySpeed(state.originalFlySpeed);
        }
    }

    public void onPlayerQuit(Player player) {
        CrowState state = playerStates.remove(player.getUniqueId());
        if (state != null) {
            restorePlayer(player, state);
        }
    }

    private void startBlindness(Player player, int ticks) {
        UUID playerId = player.getUniqueId();
        CrowState state = playerStates.get(playerId);
        if (state == null) return;
        
        if (state.blindnessActive) {
            return;
        }
        
        state.blindnessActive = true;
        
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.BLINDNESS,
            ticks,
            1
        ));
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            CrowState currentState = playerStates.get(playerId);
            if (currentState != null) {
                currentState.blindnessActive = false;
            }
            player.sendMessage("§aTransformation complete!");
        }, ticks + 10);
    }
    
    public void cleanup() {
        modelSyncTask.cancel();
        swarm.cleanup();
        restoreAll();
    }

    /** Un-morphs everyone, leaving the model sync task running. */
    public void restoreAll() {
        playerStates.forEach((playerId, state) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) restorePlayer(player, state);
        });
        playerStates.clear();
    }
    
    public String getCurrentModelId(Player player) {
        CrowState state = playerStates.get(player.getUniqueId());
        if (state == null || !state.transformed) return "none";
        return state.flyingMode ? config.getFlyingModelId() : config.getPerchedModelId();
    }
    
    public static class CrowState {
        boolean transformed = false;
        boolean flyingMode = false;
        boolean blindnessActive = false;
        float originalFlySpeed = VANILLA_FLY_SPEED;
        float originalWalkSpeed = VANILLA_WALK_SPEED;
    }
}