package codes.castled.karasu.listeners;

import codes.castled.karasu.KarasuPlugin;
import codes.castled.karasu.managers.CrowConfig;
import codes.castled.karasu.managers.CrowEffectManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import java.util.EnumSet;
import java.util.Set;

public class CrowTransformationListener implements Listener {
    // Command/plugin teleports (/spawn, /home, /back, /tp, /tpa); pearls, portals, dismounts etc. play no swarm.
    private static final Set<TeleportCause> SWARM_CAUSES =
        EnumSet.of(TeleportCause.COMMAND, TeleportCause.PLUGIN, TeleportCause.UNKNOWN);

    private final KarasuPlugin plugin;
    private final CrowEffectManager crowEffectManager;

    public CrowTransformationListener(KarasuPlugin plugin) {
        this.plugin = plugin;
        this.crowEffectManager = plugin.getCrowEffectManager();
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!canApplyTransformation(player) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!SWARM_CAUSES.contains(event.getCause())) {
            return;
        }

        if (isTeleportingAway(event.getFrom(), event.getTo())) {
            crowEffectManager.onPlayerTeleport(player, event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();

        if (!canApplyTransformation(player)) {
            return;
        }

        boolean wasSpectator = player.getGameMode() == GameMode.SPECTATOR;
        boolean isSpectator = event.getNewGameMode() == GameMode.SPECTATOR;
        if (!wasSpectator && isSpectator) {
            crowEffectManager.onPlayerEnterSpectator(player);
        } else if (wasSpectator && !isSpectator) {
            crowEffectManager.onPlayerExitSpectator(player);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        crowEffectManager.onPlayerQuit(event.getPlayer());
    }

    /**
     * Refuses a sneak while transformed, so the client does not crouch the entity and drag the crow down
     * with it. Refused here rather than corrected with an offset: the client plays its own crouch
     * animation before the server's refusal reaches it, so there is a frame of twitch either way.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        CrowConfig config = CrowConfig.getInstance();
        if (!config.isSneakShiftDisabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (!crowEffectManager.isTransformed(player)) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean canApplyTransformation(Player player) {
        return player.hasPermission("karasu.crows") && 
               player.isOnline() && 
               !player.hasPermission("karasu.exempt");
    }
    
    private boolean isTeleportingAway(Location from, Location to) {
        return from.getWorld() != to.getWorld()
            || from.distance(to) >= CrowConfig.getInstance().getMinTeleportDistance();
    }
}