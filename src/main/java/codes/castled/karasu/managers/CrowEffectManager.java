package codes.castled.karasu.managers;

import codes.castled.karasu.KarasuPlugin;
import codes.castled.karasu.effects.CrowSwarm;
import codes.castled.karasu.hooks.UnlimitedNametagsHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrowEffectManager {
    private static final float VANILLA_FLY_SPEED = 0.1f;
    private final KarasuPlugin plugin;
    private final CrowConfig config;
    private final ModelEngineBridge modelEngine;
    private final UnlimitedNametagsHook nametags;
    private final CrowSwarm swarm;
    private final ConcurrentHashMap<UUID, CrowState> playerStates = new ConcurrentHashMap<>();
    private final BukkitTask modelSyncTask;

    public CrowEffectManager(KarasuPlugin plugin) {
        this.plugin = plugin;
        this.config = CrowConfig.getInstance();
        this.modelEngine = plugin.getModelEngineBridge();
        this.nametags = plugin.getNametagHook();
        this.swarm = new CrowSwarm(plugin, config, modelEngine);
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

            boolean flying;
            if (!state.flyingMode && player.isFlying()) {
                flying = true;
            } else if (state.flyingMode && !player.isFlying() && player.isOnGround()) {
                flying = false;
            } else {
                return;
            }

            state.flyingMode = flying;
            modelEngine.removeModel(player, flying ? config.getPerchedModelId() : config.getFlyingModelId());
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

    private boolean isTransformed(Player player) {
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
        
        player.setWalkSpeed(0);
        // No jumping while perched; double-tap space still toggles flight for players allowed to fly.
        setJumpStrength(player, 0);
        state.originalFlySpeed = player.getFlySpeed();
        player.setFlySpeed((float) Math.max(0, Math.min(1, VANILLA_FLY_SPEED * config.getMorphFlightSpeed())));

        state.flyingMode = player.isFlying();
        applyModelToPlayer(player, state);
        if (nametags != null) nametags.hide(player);
        
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
    
    private void applyModelToPlayer(Player player, CrowState state) {
        if (!config.isUseModelEngine() || !modelEngine.isEnabled()) {
            return;
        }
        String modelId = state.flyingMode ? config.getFlyingModelId() : config.getPerchedModelId();
        modelEngine.setBaseEntityVisible(player, false);
        modelEngine.applyModel(player, modelId);
    }
    
    private void removeTransformation(Player player, CrowState state) {
        restorePlayer(player, state);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);

        startBlindness(player, config.getBlindDurationTicks());
    }

    // Undoes the crow model and attribute changes; these persist in player data if left applied.
    private void restorePlayer(Player player, CrowState state) {
        player.removeScoreboardTag("karasu_transformed");

        modelEngine.removeModel(player, config.getFlyingModelId());
        modelEngine.removeModel(player, config.getPerchedModelId());
        modelEngine.setBaseEntityVisible(player, true);
        if (nametags != null) nametags.show(player);

        setPlayerScale(player, 1.0);
        resetJumpStrength(player);
        player.setWalkSpeed(0.2f);
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
    }
}