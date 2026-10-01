package codes.castled.karasu.managers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Draws crows as bats, so Karasu still works on a server with no model engine installed at all.
 *
 * <p>Preferred path is LibsDisguises, bound reflectively the same way ModelEngine is: it disguises the
 * player as a bat client-side, so the client keeps treating them as a player and damage, targeting and
 * the tab list all behave. No resource pack and no extra entity are involved.
 *
 * <p>Without LibsDisguises the fallback spawns a real bat and hides the player. That is purely
 * cosmetic: the bat is a separate entity, so it can be hit independently of the player and its own
 * health is not theirs. Good enough for a swarm and for servers with no model plugin, which is why it
 * warns rather than failing.
 */
public class VanillaCrowModelEngine implements CrowModelEngine {

    private static final String LD_API = "me.libraryaddict.disguise.DisguiseAPI";
    private static final String LD_TYPE = "me.libraryaddict.disguise.disguisetypes.DisguiseType";
    private static final String LD_MOB = "me.libraryaddict.disguise.disguisetypes.MobDisguise";
    private static final String LD_DISGUISE = "me.libraryaddict.disguise.disguisetypes.Disguise";
    private static final String BAT_TAG = "karasu_bat";
    private static final long FOLLOW_PERIOD = 1L;

    private final JavaPlugin plugin;
    /** Bats this engine owns, keyed by the UUID of whatever they stand in for. */
    private final Map<UUID, Entity> bats = new ConcurrentHashMap<>();
    private final boolean disguised;

    private Method disguiseToAll;
    private Method undisguiseToAll;
    private Constructor<?> mobDisguise;
    private Class<?> disguiseType;
    private BukkitTask followTask;

    public VanillaCrowModelEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.disguised = bindLibsDisguises();
        if (!disguised) {
            plugin.getLogger().warning("Karasu is in bat mode and LibsDisguises is not installed, so it"
                    + " will spawn a real bat instead of disguising you as one. That is cosmetic only, but"
                    + " the bat can be attacked separately from you. Install LibsDisguises for a real"
                    + " disguise.");
        }
    }

    /** @return true if LibsDisguises is installed and every piece Karasu needs resolved */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean bindLibsDisguises() {
        if (Bukkit.getPluginManager().getPlugin("LibsDisguises") == null) return false;
        try {
            Class<?> api = Class.forName(LD_API);
            disguiseType = Class.forName(LD_TYPE);
            mobDisguise = Class.forName(LD_MOB).getConstructor(disguiseType);
            disguiseToAll = api.getMethod("disguiseToAll", Entity.class, Class.forName(LD_DISGUISE));
            undisguiseToAll = api.getMethod("undisguiseToAll", Entity.class);
            // Touch the enum so a LibsDisguises build without BAT fails here rather than on first morph.
            Enum.valueOf((Class<? extends Enum>) disguiseType.asSubclass(Enum.class), "BAT");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("LibsDisguises is installed but Karasu could not bind to it ("
                    + e + "). Falling back to spawning a real bat.");
            return false;
        }
    }

    @Override
    public String name() {
        return disguised ? "Vanilla (LibsDisguises)" : "Vanilla (bats)";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean applyModel(Entity entity, String modelId) {
        return applyModel(entity, modelId, true);
    }

    @Override
    public boolean applyModel(Entity entity, String modelId, double scale) {
        // A bat is a vanilla mob at its own size; there is no model here to scale.
        return applyModel(entity, modelId, true);
    }

    @Override
    public boolean applyModel(Entity entity, String modelId, boolean selfView) {
        if (entity == null || !entity.isValid()) return false;
        if (entity instanceof Player player) {
            return disguisePlayer(player);
        }
        // A swarm carrier is an invisible marker stand, so it needs a bat standing in for it.
        return spawnBat(entity.getLocation(), entity.getUniqueId()) != null;
    }

    private boolean disguisePlayer(Player player) {
        if (disguised) {
            try {
                Object bat = Enum.valueOf(disguiseType.asSubclass(Enum.class), "BAT");
                disguiseToAll.invoke(null, player, mobDisguise.newInstance(bat));
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().warning("LibsDisguises disguise failed for " + player.getName()
                        + " (" + e + "). Using a real bat for them instead.");
            }
        }
        if (bats.containsKey(player.getUniqueId())) return true;
        Bat bat = spawnBat(player.getLocation(), player.getUniqueId());
        if (bat == null) return false;
        player.setInvisible(true);
        startFollowing();
        return true;
    }

    /**
     * A bat with its AI off, so it holds position with its owner and cannot be knocked out of the
     * swarm mid-flight.
     */
    private Bat spawnBat(Location location, UUID owner) {
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return null;
        }
        Entity existing = bats.get(owner);
        if (existing != null && existing.isValid()) return (Bat) existing;

        Bat bat = world.spawn(location, Bat.class, b -> {
            b.setAI(false);
            b.setSilent(true);
            b.setInvulnerable(true);
            b.setCollidable(false);
            b.setRemoveWhenFarAway(false);
            b.setPersistent(false);
            b.addScoreboardTag(BAT_TAG);
        });
        if (!bat.isValid()) return null;
        bats.put(owner, bat);
        return bat;
    }

    /** Keeps every fallback bat on top of whatever it is standing in for. */
    private void startFollowing() {
        if (followTask != null) return;
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Entity> entry : bats.entrySet()) {
                Entity bat = entry.getValue();
                if (bat == null || bat.isDead() || !bat.isValid()) {
                    bats.remove(entry.getKey());
                    continue;
                }
                if (entry.getKey().equals(bat.getUniqueId())) continue;
                Entity carrier = Bukkit.getEntity(entry.getKey());
                if (carrier != null && carrier.isValid()) {
                    bat.teleport(carrier.getLocation());
                }
            }
        }, FOLLOW_PERIOD, FOLLOW_PERIOD);
    }

    @Override
    public boolean removeModel(Entity entity, String modelId) {
        if (entity == null) return false;
        Entity bat = bats.remove(entity.getUniqueId());
        if (bat != null && bat.isValid()) {
            bat.remove();
        }
        if (entity instanceof Player player) {
            player.setInvisible(false);
            if (disguised && undisguiseToAll != null) {
                try {
                    undisguiseToAll.invoke(null, player);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // The fallback bat is already gone; nothing further to undo.
                }
            }
            return true;
        }
        return bat != null;
    }

    @Override
    public boolean isModelLoaded(String modelId) {
        return true;
    }

    @Override
    public void setBaseEntityVisible(Entity entity, boolean visible) {
        // In fallback mode the bat is the visible stand-in and the carrier stays hidden either way.
        if (!disguised && entity instanceof Player player) {
            player.setInvisible(!visible);
        }
    }

    @Override
    public void setSelfView(Player player, boolean selfView) {
        // A vanilla entity is already visible to its own owner; nothing to negotiate.
    }

    @Override
    public void tickModel(Entity carrier) {
        if (carrier == null) return;
        Entity bat = bats.get(carrier.getUniqueId());
        if (bat != null && bat.isValid() && !bat.isDead()) {
            bat.teleport(carrier.getLocation());
        }
    }

    /** Drops every bat and stops the follow task. */
    public void shutdown() {
        for (Entity bat : bats.values()) {
            if (bat != null && bat.isValid()) {
                bat.remove();
            }
        }
        bats.clear();
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
    }
}
