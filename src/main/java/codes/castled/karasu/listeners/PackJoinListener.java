package codes.castled.karasu.listeners;

import codes.castled.karasu.pack.ResourcePackService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PackJoinListener implements Listener {
    private final ResourcePackService resourcePackService;

    public PackJoinListener(ResourcePackService resourcePackService) {
        this.resourcePackService = resourcePackService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        resourcePackService.sendPackTo(event.getPlayer());
    }
}