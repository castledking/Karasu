package codes.castled.karasu.hooks;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.plugin.Plugin;

/**
 * Hides a morphed player's held items and armor from their own third-person view. The invisible flag hides
 * the body but the client still renders held items and armor straight from its local inventory, so the only
 * way to hide them is to blank those slots in the inventory packets sent to that player (as LibsDisguises'
 * HideHeldItemFromSelf does). The server-side inventory is untouched. Only load when packetevents is enabled.
 */
public class SelfItemHider extends PacketListenerAbstract implements Listener {
    // Player inventory menu (window 0) slot layout.
    private static final int MENU_ARMOR_FIRST = 5;
    private static final int MENU_ARMOR_LAST = 8;
    private static final int MENU_HOTBAR_FIRST = 36;
    private static final int MENU_OFFHAND = 45;
    // Raw Inventory indices used by ClientboundSetPlayerInventory.
    private static final int INV_ARMOR_FIRST = 36;
    private static final int INV_ARMOR_LAST = 39;
    private static final int INV_OFFHAND = 40;

    private final Plugin plugin;
    private final boolean hideHeld;
    private final boolean hideArmor;
    // Read from netty threads.
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    public SelfItemHider(Plugin plugin, boolean hideHeld, boolean hideArmor) {
        super(PacketListenerPriority.HIGH);
        this.plugin = plugin;
        this.hideHeld = hideHeld;
        this.hideArmor = hideArmor;
    }

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }

    public void hide(Player player) {
        if (hidden.add(player.getUniqueId())) {
            player.updateInventory();
        }
    }

    public void show(Player player) {
        if (hidden.remove(player.getUniqueId())) {
            player.updateInventory();
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.SET_SLOT
                && event.getPacketType() != PacketType.Play.Server.WINDOW_ITEMS
                && event.getPacketType() != PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player) || !hidden.contains(player.getUniqueId())) {
            return;
        }
        int held = player.getInventory().getHeldItemSlot();

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(event);
            if (packet.getWindowId() == 0 && isHiddenMenuSlot(packet.getSlot(), held)) {
                packet.setItem(ItemStack.EMPTY);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);
            if (packet.getWindowId() != 0) return;
            List<ItemStack> items = new ArrayList<>(packet.getItems());
            for (int slot = 0; slot < items.size(); slot++) {
                if (isHiddenMenuSlot(slot, held)) items.set(slot, ItemStack.EMPTY);
            }
            packet.setItems(items);
            event.markForReEncode(true);
        } else {
            WrapperPlayServerSetPlayerInventory packet = new WrapperPlayServerSetPlayerInventory(event);
            int slot = packet.getSlot();
            boolean hide = (hideHeld && (slot == held || slot == INV_OFFHAND))
                    || (hideArmor && slot >= INV_ARMOR_FIRST && slot <= INV_ARMOR_LAST);
            if (hide) {
                packet.setStack(ItemStack.EMPTY);
                event.markForReEncode(true);
            }
        }
    }

    private boolean isHiddenMenuSlot(int slot, int held) {
        if (hideArmor && slot >= MENU_ARMOR_FIRST && slot <= MENU_ARMOR_LAST) return true;
        return hideHeld && (slot == MENU_HOTBAR_FIRST + held || slot == MENU_OFFHAND);
    }

    // Resync next tick so the newly held slot is blanked and the previous one shows its item again.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        resyncLater(event.getPlayer());
    }

    // Other containers share the client's inventory slots, so closing one can reveal the real items.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            resyncLater(player);
        }
    }

    private void resyncLater(Player player) {
        if (!hidden.contains(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && hidden.contains(player.getUniqueId())) player.updateInventory();
        });
    }
}
