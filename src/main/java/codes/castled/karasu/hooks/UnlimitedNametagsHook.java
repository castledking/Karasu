package codes.castled.karasu.hooks;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.alexdev.unlimitednametags.api.UNTPaperAPI;
import org.alexdev.unlimitednametags.api.event.PlayerNametagVisibilityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Keeps UnlimitedNameTags' packet nametags off morphed players. ModelEngine hides the player entity
 * client-side, so UNT's text displays lose their vehicle and freeze in place unless they are removed.
 * Only load this class when UnlimitedNameTags is enabled.
 */
public class UnlimitedNametagsHook implements Listener {
    // UNT fires visibility events off the main thread too.
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    public void hide(Player player) {
        hidden.add(player.getUniqueId());
        UNTPaperAPI.getInstance().hideNametag(player);
    }

    public void show(Player player) {
        if (hidden.remove(player.getUniqueId())) {
            UNTPaperAPI.getInstance().showNametag(player);
        }
    }

    // Vetoes every attempt UNT makes to (re)show the tag, e.g. when a viewer starts tracking the player.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNametagVisibility(PlayerNametagVisibilityEvent event) {
        if (hidden.contains(event.getOwner().getUniqueId())) {
            event.setVisible(false);
        }
    }
}
