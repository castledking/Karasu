package codes.castled.karasu.effects;

import codes.castled.karasu.KarasuPlugin;
import codes.castled.karasu.managers.CrowConfig;
import codes.castled.karasu.managers.ModelEngineBridge;
import com.destroystokyo.paper.ClientOption;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Crow swarm effects: crows are invisible marker armor stands carrying the crow_fly ModelEngine model,
 * moved along quadratic bezier curves one step per tick.
 */
public class CrowSwarm {
    private static final String TAG = "karasu_swarm";
    private static final EquipmentSlot[] MANNEQUIN_SLOTS = {
        EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final KarasuPlugin plugin;
    private final CrowConfig config;
    private final ModelEngineBridge modelEngine;
    private final Set<Entity> spawned = ConcurrentHashMap.newKeySet();

    public CrowSwarm(KarasuPlugin plugin, CrowConfig config, ModelEngineBridge modelEngine) {
        this.plugin = plugin;
        this.config = config;
        this.modelEngine = modelEngine;
    }

    /**
     * Player vanished from {@code from}. Instant teleports leave nothing behind to burst, so a mannequin
     * wearing the player's skin stands in briefly and then bursts into crows flying outward.
     */
    public void depart(Player player, Location from, boolean withMannequin) {
        Location origin = from.clone();
        int count = config.getDepartureCrowCount();
        if (!withMannequin) {
            burst(origin, count, 0.1, 0.6);
            return;
        }
        Mannequin mannequin = spawnMannequin(player, origin);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            remove(mannequin);
            burst(origin, count, 0.2, 1.7);
        }, config.getMannequinTicks());
    }

    /** Crows fly outward from a body-sized column at {@code origin} (minY..maxY above it). */
    public void burst(Location origin, int count, double minY, double maxY) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double spread = config.getSwarmSpread();
        List<Flight> flights = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vector start = origin.toVector().add(bodyOffset(minY, maxY));
            double angle = random.nextDouble(Math.PI * 2);
            Vector dir = new Vector(Math.cos(angle), random.nextDouble(0.2, 0.8), Math.sin(angle)).normalize();
            Vector end = start.clone().add(dir.multiply(spread * random.nextDouble(0.7, 1.2)));
            flights.add(new Flight(start, controlPoint(start, end), () -> end, 0, vary(config.getDepartureTicks()), false));
        }
        run(origin.getWorld(), flights, null);
    }

    /** Crows start on a ring around the player and converge onto their body, following them if they move. */
    public void converge(Player player, int count, double minY, double maxY) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double spread = config.getSwarmSpread();
        Vector base = player.getLocation().toVector();
        List<Flight> flights = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble(Math.PI * 2);
            double distance = spread * random.nextDouble(0.8, 1.1);
            Vector start = base.clone().add(new Vector(Math.cos(angle) * distance, random.nextDouble(1, 4), Math.sin(angle) * distance));
            Vector offset = bodyOffset(minY, maxY);
            Supplier<Vector> end = () -> player.getLocation().toVector().add(offset);
            int delay = random.nextInt(6);
            flights.add(new Flight(start, controlPoint(start, end.get()), end, delay, vary(config.getArrivalTicks()), true));
        }
        run(player.getWorld(), flights, player);
    }

    private void run(World world, List<Flight> flights, Player tracked) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tracked != null && (!tracked.isOnline() || tracked.getWorld() != world)) {
                    flights.forEach(flight -> remove(flight.stand));
                    cancel();
                    return;
                }
                boolean alive = false;
                for (Flight flight : flights) {
                    if (flight.done) continue;
                    int local = tick - flight.delay;
                    if (local < 0) {
                        alive = true;
                        continue;
                    }
                    double t = Math.min(1, local / (double) flight.duration);
                    if (flight.easeInOut) t = t * t * (3 - 2 * t);
                    Vector end = flight.end.get();
                    Location location = bezier(flight.start, flight.control, end, t).toLocation(world);
                    face(location, derivative(flight.start, flight.control, end, t));

                    if (flight.stand == null) {
                        flight.stand = spawnCrow(location);
                        if (flight.stand == null) {
                            flight.done = true;
                            continue;
                        }
                    } else {
                        flight.stand.teleport(location);
                    }
                    if (local >= flight.duration) {
                        remove(flight.stand);
                        flight.done = true;
                    } else {
                        alive = true;
                    }
                }
                if (!alive) cancel();
                tick++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private ArmorStand spawnCrow(Location location) {
        World world = location.getWorld();
        if (!modelEngine.isEnabled() || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return null;
        }
        ArmorStand stand = world.spawn(location, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            as.setSilent(true);
            as.setInvulnerable(true);
            as.setPersistent(false);
            as.addScoreboardTag(TAG);
        });
        if (!stand.isValid()) return null;
        spawned.add(stand);
        if (!modelEngine.applyModel(stand, config.getFlyingModelId(), config.getSwarmCrowScale())) {
            remove(stand);
            return null;
        }
        modelEngine.setBaseEntityVisible(stand, false);
        return stand;
    }

    private Mannequin spawnMannequin(Player player, Location location) {
        Mannequin mannequin = location.getWorld().spawn(location, Mannequin.class, m -> {
            m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            m.setSkinParts(player.getClientOption(ClientOption.SKIN_PARTS));
            m.setMainHand(player.getMainHand());
            m.setDescription(null);
            m.setCustomNameVisible(false);
            m.setImmovable(true);
            m.setGravity(false);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setPersistent(false);
            m.addScoreboardTag(TAG);
            if (player.isSneaking()) {
                m.setPose(Pose.SNEAKING, true);
            }
            EntityEquipment from = player.getEquipment();
            EntityEquipment to = m.getEquipment();
            for (EquipmentSlot slot : MANNEQUIN_SLOTS) {
                to.setItem(slot, from.getItem(slot));
            }
        });
        spawned.add(mannequin);
        return mannequin;
    }

    private void remove(Entity entity) {
        if (entity == null) return;
        spawned.remove(entity);
        if (entity instanceof ArmorStand) {
            modelEngine.removeModel(entity, config.getFlyingModelId());
        }
        entity.remove();
    }

    public void cleanup() {
        new ArrayList<>(spawned).forEach(this::remove);
    }

    private static Vector bodyOffset(double minY, double maxY) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2);
        double radius = random.nextDouble(0.35);
        return new Vector(Math.cos(angle) * radius, random.nextDouble(minY, maxY), Math.sin(angle) * radius);
    }

    // Bends the path sideways and upward so crows arc instead of flying in straight lines.
    private static Vector controlPoint(Vector start, Vector end) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector mid = start.clone().add(end).multiply(0.5);
        Vector along = end.clone().subtract(start);
        Vector side = new Vector(-along.getZ(), 0, along.getX());
        if (side.lengthSquared() > 1.0E-6) {
            side.normalize().multiply(random.nextDouble(-0.35, 0.35) * along.length());
        }
        return mid.add(side).add(new Vector(0, random.nextDouble(0.5, 2), 0));
    }

    private static Vector bezier(Vector p0, Vector p1, Vector p2, double t) {
        double u = 1 - t;
        return p0.clone().multiply(u * u).add(p1.clone().multiply(2 * u * t)).add(p2.clone().multiply(t * t));
    }

    private static Vector derivative(Vector p0, Vector p1, Vector p2, double t) {
        return p1.clone().subtract(p0).multiply(2 * (1 - t)).add(p2.clone().subtract(p1).multiply(2 * t));
    }

    private static void face(Location location, Vector direction) {
        if (direction.getX() * direction.getX() + direction.getZ() * direction.getZ() < 1.0E-6) return;
        location.setYaw((float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())));
    }

    private static int vary(int ticks) {
        return Math.max(1, (int) Math.round(ticks * ThreadLocalRandom.current().nextDouble(0.85, 1.15)));
    }

    private static final class Flight {
        final Vector start;
        final Vector control;
        final Supplier<Vector> end;
        final int delay;
        final int duration;
        final boolean easeInOut;
        ArmorStand stand;
        boolean done;

        Flight(Vector start, Vector control, Supplier<Vector> end, int delay, int duration, boolean easeInOut) {
            this.start = start;
            this.control = control;
            this.end = end;
            this.delay = delay;
            this.duration = duration;
            this.easeInOut = easeInOut;
        }
    }
}
