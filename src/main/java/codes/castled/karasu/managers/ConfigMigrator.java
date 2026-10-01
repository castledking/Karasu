package codes.castled.karasu.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Owns Karasu's configuration defaults and brings an existing {@code karasu.yml} up to date.
 *
 * <p>Karasu writes {@code karasu.yml} only when it is absent, so an install that predates a config
 * option silently kept running on the in-code fallback for that option forever: the file said one
 * thing, the plugin did another, and nothing in the file explained why. {@link #migrate} rewrites the
 * file instead, so options show up with their default and their documentation.
 *
 * <p>{@link #DEFAULTS} is the authoritative list: it fixes both the value and the position of every
 * option, and {@link CrowConfig}'s getters read their fallbacks from it, so a default is declared once.
 * Its order is the layout of the generated file. The list also mirrors
 * {@code src/main/resources/karasu.yml}, which is what a fresh install gets - keep the two in step.
 *
 * <p>Migration never discards operator input. Values already in the file are kept, comments already on
 * a key are kept, and keys the plugin does not recognise are carried over untouched at the end of
 * their own section.
 */
public final class ConfigMigrator {

    /**
     * One option: where it lives, its default, and the comment that documents it above the key.
     *
     * @param path full dotted path, matching the key's position in the file
     * @param value default value; the YAML type follows the value's Java type
     * @param comments comment lines written directly above the key, without the {@code #} prefix
     */
    public record Entry(String path, Object value, String... comments) {

        public Entry(String path, Object value, String... comments) {
            this.path = Objects.requireNonNull(path, "path");
            this.value = Objects.requireNonNull(value, "value");
            this.comments = comments;
        }
    }

    /** Options that used to exist, mapped to what took over. Dropped on migration. */
    private static final Map<String, String> OBSOLETE = Map.of(
            "karasu.visual.use-model-engine", "karasu.visual.model-engine"
    );

    /**
     * Every option, in file order. Sections are implicit: an option creates its parents, so listing
     * {@code karasu.swarm.spread} is enough - there is no entry for the {@code swarm} section itself.
     */
    private static final List<Entry> DEFAULTS = List.of(
            new Entry("karasu.transformation.base-scale", 0.6),
            new Entry("karasu.transformation.morph-flight-speed", 0.4,
                    "Flight speed while morphed, as a fraction of vanilla flight speed (1.0 = vanilla, 0.4 = 40%)."),
            new Entry("karasu.transformation.max-crow-count", 5,
                    "Cap for /crow spawn [count]."),
            new Entry("karasu.transformation.blind-duration-ticks", 30),
            new Entry("karasu.transformation.walk-speed", 0.2,
                    "Walking speed while morphed. 0.2 is vanilla. 0 roots you in place, which is what",
                    "earlier versions hardcoded - use it if you want the perched crow planted."),

            new Entry("karasu.swarm.departure-crows", 12,
                    "Crow swarm played when a player vanishes (teleporting away, entering spectator) and reappears",
                    "(arriving, leaving spectator, un-morphing)."),
            new Entry("karasu.swarm.arrival-crows", 10),
            new Entry("karasu.swarm.crow-scale", 0.5,
                    "Scale of each swarm crow (1.0 = same size as the morph model)."),
            new Entry("karasu.swarm.spread", 8.0,
                    "How far (blocks) crows fly out on departure / start from on arrival."),
            new Entry("karasu.swarm.departure-ticks", 30),
            new Entry("karasu.swarm.arrival-ticks", 16),
            new Entry("karasu.swarm.mannequin-ticks", 5,
                    "How long the player-skinned mannequin stands at the departure spot before bursting into crows."),
            new Entry("karasu.swarm.min-teleport-distance", 4.0,
                    "Teleports shorter than this (same world) play no swarm."),

            new Entry("karasu.visual.model-engine", "auto",
                    "Which plugin draws the crow: auto | bettermodel | modelengine | none.",
                    "  auto       - prefer BetterModel, fall back to ModelEngine (default)",
                    "  bettermodel- BetterModel only, never falls back",
                    "  modelengine- ModelEngine only",
                    "  none       - no models; players still transform, the crow is just invisible",
                    "'auto' is the right choice on 26.3+: BetterModel targets it, ModelEngine does not yet, so on",
                    "26.3 'auto' picks BetterModel and on 26.2 it falls back to ModelEngine. Pinning 'modelengine'",
                    "on 26.3 gets you no models, with a warning at startup."),
            new Entry("karasu.visual.self-view", true,
                    "Let morphed players see their own crow model (F5). Their own body is hidden so only the crow shows."),
            new Entry("karasu.visual.hide-held-item-from-self", true,
                    "With self-view, hide your own held items / armor so they don't float around the crow in F5.",
                    "Needs packetevents. Those slots look empty in your own hotbar/inventory while morphed; the",
                    "items themselves are untouched. Changing these needs a restart."),
            new Entry("karasu.visual.hide-armor-from-self", true),
            new Entry("karasu.visual.hide-self-body", false,
                    "Also hide the morphed player's own body through Bukkit, not just through the model",
                    "engine. BetterModel writes the invisible flag itself, but its packets do not seem",
                    "to reach the player viewing themselves, which is why the wearer still sees their own",
                    "body in third person. This sets Bukkit's invisible flag as well, which travels a",
                    "different path. Hides the body from everyone, which is what a disguise wants."),
            new Entry("karasu.visual.model-scale", 1.0,
                    "Size of the crow model itself, independent of transformation.base-scale. BetterModel",
                    "would otherwise size the model from the player's SCALE attribute, which couples the two:",
                    "shrinking the player to sit inside the crow also moves the model's anchor and sinks it",
                    "into the ground. 1.0 renders the model at its authored size; raise it to make the crow",
                    "taller than the player's hitbox, lower it to shrink the model."),
            new Entry("karasu.visual.disable-sneak-shift", true,
                    "Stop a morphed player from sneaking, which would otherwise make the client crouch the",
                    "entity and drag the model down with it. false lets them sneak and the model follows.",
                    "Canceling the sneak can make the player twitch briefly, since the client crouches",
                    "before the server refuses it. Set false if that is bothersome."),
            new Entry("karasu.visual.models.flying", "crow_fly",
                    "ModelEngine blueprint names. Only read when the engine in use is ModelEngine."),
            new Entry("karasu.visual.models.perched", "crow_stand"),
            new Entry("karasu.visual.bettermodel.models.flying", "crow_fly",
                    "BetterModel model names. Only read when the engine in use is BetterModel. These are model",
                    "ids in BetterModel's own model folder, not the same namespace ModelEngine blueprints live",
                    "in, so they can differ from the pair above."),
            new Entry("karasu.visual.bettermodel.models.perched", "crow_stand"),
            new Entry("karasu.visual.bettermodel.use-limb-models", false,
                    "Draw the morph as a BetterModel limb model instead of a general model. Limb models",
                    "live in plugins/BetterModel/players/ and go through BetterModel's player code path,",
                    "which is the only one built to render on a player - and the only remaining candidate",
                    "for making the wearer see their own crow in third person. Put the crow models in that",
                    "folder and set their names under bettermodel.models. Affects the morph only; the swarm",
                    "crows are armor stands and keep using the general models."),
            new Entry("karasu.visual.bettermodel.hidden-bones", List.of("hitbox"),
                    "Bone names to hide while rendering. 'hitbox' is hidden by default because models",
                    "converted from ModelEngine often bake the hitbox in as visible, fully textured",
                    "geometry, which shows up as a box around the crow. Hiding the display keeps the",
                    "bone's hit detection intact. BetterModel only."),

            new Entry("karasu.sounds.enter-sound", "ENTITY_BAT_TAKEOFF"),
            new Entry("karasu.sounds.exit-sound", "ENTITY_BAT_HURT"),
            new Entry("karasu.sounds.flying-sound", "ENTITY_ENDER_DRAGON_FLAP"),
            new Entry("karasu.sounds.swarm-sound", "ENTITY_WITCH_AMBIENT"),

            new Entry("karasu.resource-pack.use-resourcepack", false,
                    "Master switch. true: Karasu hands the model engine's pack to ResourcePackManager (if",
                    "installed) so its crow model textures get merged into the server's combined pack. false:",
                    "Karasu does nothing with packs - skip it and manage the pack yourself (e.g. BetterModel",
                    "or RSPM). The referenced pack file is always left in place either way."),
            new Entry("karasu.resource-pack.pack-file", "",
                    "Pack file this plugin manages, relative to the server root. Leave blank to use whichever pack",
                    "the active engine builds: plugins/BetterModel/build.zip under BetterModel,",
                    "plugins/ModelEngine/resource pack.zip under ModelEngine. Both engines write their pack on their",
                    "own reload (/bettermodel reload or /meg reload), so the file may not exist on first boot."),
            new Entry("karasu.resource-pack.url", "",
                    "Direct-send URL, used ONLY when ResourcePackManager is NOT installed. Host the pack file",
                    "somewhere and point this at it; the SHA-1 is computed automatically. Leave blank to send",
                    "nothing directly.")
    );

    private static final Map<String, Entry> BY_PATH = index();

    private ConfigMigrator() {
    }

    private static Map<String, Entry> index() {
        Map<String, Entry> byPath = new LinkedHashMap<>();
        for (Entry entry : DEFAULTS) {
            byPath.put(entry.path(), entry);
        }
        return Map.copyOf(byPath);
    }

    /** The declared default for {@code path}, or {@code fallback} when the option is unknown. */
    public static Object defaultValue(String path, Object fallback) {
        Entry entry = BY_PATH.get(path);
        return entry == null ? fallback : entry.value();
    }

    /** What {@link #migrate} ended up doing, so the caller can report it once. */
    public record Result(List<String> added, List<String> removed, boolean changed) {
    }

    /**
     * Rewrites {@code file} so it carries every option in {@link #DEFAULTS}, in that order.
     *
     * <p>Rebuilds the document rather than editing it in place so the result always reads in the
     * documented order, whichever order the options happen to be in now. Values, comments and
     * unrecognised keys come across from {@code current} untouched; options missing from it are written
     * at their default, which is what makes a new release's options show up on an old install.
     *
     * @return what changed, so the caller can log it instead of rewriting the file every boot
     */
    public static Result migrate(File file, YamlConfiguration current) {
        Set<String> known = new LinkedHashSet<>();
        for (Entry entry : DEFAULTS) {
            known.add(entry.path());
        }
        for (String path : OBSOLETE.keySet()) {
            known.add(path);
        }

        // Sections the plugin does not define, plus options an operator added, so nothing is lost.
        List<String> extras = new ArrayList<>();
        for (String path : current.getKeys(true)) {
            if (known.contains(path) || isParentOfKnown(path, known) || current.isConfigurationSection(path)) {
                continue;
            }
            extras.add(path);
        }

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        YamlConfiguration merged = new YamlConfiguration();
        for (Entry entry : DEFAULTS) {
            String path = entry.path();
            boolean present = current.contains(path);
            Object value = present ? current.get(path) : entry.value();
            merged.set(path, value);
            if (!present) {
                added.add(path);
            }
            // A comment an operator wrote about their own value outranks the shipped one.
            List<String> comments = present && !current.getComments(path).isEmpty()
                    ? current.getComments(path)
                    : List.of(entry.comments());
            if (!comments.isEmpty()) {
                merged.setComments(path, comments);
            }
        }
        for (String path : OBSOLETE.keySet()) {
            if (current.contains(path)) {
                removed.add(path);
            }
        }
        for (String path : extras) {
            merged.set(path, current.get(path));
            List<String> comments = current.getComments(path);
            if (!comments.isEmpty()) {
                merged.setComments(path, comments);
            }
        }

        // Operator keys are always "extra", so their presence alone is not a change; sameLayout decides
        // whether they are still sitting where this migration would put them.
        boolean changed = !added.isEmpty() || !removed.isEmpty() || !sameLayout(current, merged);
        if (changed) {
            // No header: the result should be indistinguishable from the file a fresh install gets.
            try {
                merged.save(file);
            } catch (IOException e) {
                // A read-only data folder must not stop the plugin from running on the values it has.
                return new Result(added, removed, false);
            }
        }
        return new Result(added, removed, changed);
    }

    /** True when {@code path} is an ancestor of a known option, i.e. a section rather than an option. */
    private static boolean isParentOfKnown(String path, Set<String> known) {
        String prefix = path + '.';
        for (String candidate : known) {
            if (candidate.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code a} already reads in the layout {@link #migrate} would write.
     *
     * <p>{@code getKeys(true)} is a LinkedHashSet in document order, so the lists compare order as well
     * as membership: a file whose options are present but shuffled is reported as needing a rewrite.
     */
    private static boolean sameLayout(YamlConfiguration a, YamlConfiguration b) {
        List<String> left = new ArrayList<>(a.getKeys(true));
        List<String> right = new ArrayList<>(b.getKeys(true));
        return left.equals(right) && valuesMatch(a, b, left);
    }

    private static boolean valuesMatch(YamlConfiguration a, YamlConfiguration b, List<String> keys) {
        for (String key : keys) {
            // Sections are rebuilt as fresh objects and never compare equal to themselves, so only
            // leaf values can be compared. Every section is implied by the options under it anyway.
            if (a.isConfigurationSection(key)) {
                continue;
            }
            if (!Objects.equals(a.get(key), b.get(key))) {
                return false;
            }
        }
        return true;
    }
}