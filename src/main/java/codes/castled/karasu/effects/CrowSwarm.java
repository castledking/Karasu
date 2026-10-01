package codes.castled.karasu.effects;

import codes.castled.karasu.KarasuPlugin;
import codes.castled.karasu.managers.CrowConfig;
import codes.castled.karasu.managers.CrowModelEngine;
import java.lang.reflect.Method;
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
 * Crow swarm effects: crows are invisible marker armor stands carrying the flying crow model on
 * whichever {@link CrowModelEngine} is active, moved along quadratic bezier curves one step per tick.
 */
public class CrowSwarm {
    private static final String TAG = "karasu_swarm";
    private static final EquipmentSlot[] MANNEQUIN_SLOTS = {
        EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final KarasuPlugin plugin;
    private final CrowConfig config;
    private CrowModelEngine engine;
    private final Set<Entity> spawned = ConcurrentHashMap.newKeySet();

    public CrowSwarm(KarasuPlugin plugin, CrowConfig config, CrowModelEngine engine) {
        this.plugin = plugin;
        this.config = config;
        this.engine = engine;
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
        // Mannequin arrived in 1.21.9. Below that there is no stand-in to leave behind, so the crows
        // burst immediately instead. The only loss is the brief pause; the burst itself is unchanged.
        if (!supportsMannequin()) {
            burst(origin, count, 0.2, 1.7);
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
        if (!engine.isAvailable() || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
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
        if (!engine.applyModel(stand, config.getFlyingModelId(), config.getSwarmCrowScale())) {
            remove(stand);
            return null;
        }
        engine.setBaseEntityVisible(stand, false);
        return stand;
    }

    private Mannequin spawnMannequin(Player player, Location location) {
        Mannequin mannequin = location.getWorld().spawn(location, Mannequin.class, m -> {
            // Reached reflectively so the plugin still loads on Spigot, which has neither
            // Mannequin#setProfile(PlayerProfile) nor the client's SKIN_PARTS preference. On Spigot
            // the mannequin keeps its default skin; everything else about the effect is unchanged.
            applyPlayerSkin(m, player);
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

    /** Re-points the swarm at a different engine, after a config reload. */
    public void setEngine(CrowModelEngine engine) {
        this.engine = engine;
    }

    /**
     * Mirrors the player's skin onto the departure mannequin.
     *
     * <p>Both the profile and the skin-layer preference are Paper-only, so both are called
     * reflectively and resolved from the methods themselves rather than from imported types. That keeps
     * the plugin loadable on Spigot, where neither exists, at the cost of the mannequin keeping its
     * default skin there. The mannequin itself is a 1.21.9+ entity, so on older servers the departure
     * effect degrades to bursting the crows immediately - see {@link #supportsMannequin()}.
     */
    private void applyPlayerSkin(Mannequin mannequin, Player player) {
        applyMannequinProfile(mannequin, player);
        applySkinParts(mannequin, player);
    }

    /**
     * Paper returns its own {@code com.destroystokyo.paper.profile.PlayerProfile} from
     * {@code getPlayerProfile()}, while the conversion helper takes a {@code ResolvableProfile}. Neither
     * type is referenced at compile time, so both the profile and the matching static factory method are
     * resolved from the objects at runtime - looking the factory up by a hardcoded parameter type
     * silently fails, because the Bukkit and Paper profile classes are unrelated.
     */
    private void applyMannequinProfile(Mannequin mannequin, Player player) {
        Method setter = findSingleArgMethod(mannequin, "setProfile");
        if (setter == null) return;
        try {
            Object profile = player.getClass().getMethod("getPlayerProfile").invoke(player);
            if (profile == null) return;
            Method factory = findStaticFactoryTaking(profile, "resolvableProfile");
            if (factory == null) return;
            setter.invoke(mannequin, factory.invoke(null, profile));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // No Paper profile support here; the mannequin keeps its default skin.
        }
    }

    /** Finds a static single-argument method on the profile's own class that accepts that profile. */
    private static Method findStaticFactoryTaking(Object profile, String name) {
        for (Method method : profile.getClass().getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    && method.getParameterTypes()[0].isInstance(profile)) {
                return method;
            }
        }
        return null;
    }

    private void applySkinParts(Mannequin mannequin, Player player) {
        Method setter = findSingleArgMethod(mannequin, "setSkinParts");
        Method reader = findSingleArgMethod(player, "getClientOption");
        if (setter == null || reader == null) return;
        try {
            Class<?> optionType = reader.getParameterTypes()[0];
            Object skinPartsOption = optionType.getField("SKIN_PARTS").get(null);
            Object value = reader.invoke(player, skinPartsOption);
            setter.invoke(mannequin, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // No client skin-layer preference here; the mannequin keeps its default layers.
        }
    }

    private static Method findSingleArgMethod(Object target, String name) {
        try {
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1) {
                    return method;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through: treated as unsupported.
        }
        return null;
    }

    /**
     * Whether this server has the {@link Mannequin} entity. It arrived in 1.21.9, so on older servers
     * the departure effect skips the brief stand-in and bursts the crows straight away.
     */
    private static boolean supportsMannequin() {
        return MANNEQUIN != null;
    }

    private static final Class<?> MANNEQUIN = resolveMannequin();

    private static Class<?> resolveMannequin() {
        try {
            return Class.forName("org.bukkit.entity.Mannequin");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private void remove(Entity entity) {
        if (entity == null) return;
        spawned.remove(entity);
        if (entity instanceof ArmorStand) {
            engine.removeModel(entity, config.getFlyingModelId());
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
