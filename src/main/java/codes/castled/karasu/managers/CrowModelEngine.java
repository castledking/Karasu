package codes.castled.karasu.managers;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * One model engine's crow renderer. The morph and swarm code only ever talks to whichever engine is
 * active through this, so neither has to know which plugin is drawing the crows.
 *
 * <p>Every engine is reached through softdepend, so an implementation may be constructed while its
 * plugin is missing or half-initialised. Implementations must therefore never throw for that: they
 * return a failure value and leave {@link #isAvailable()} false, which degrades the crow into an
 * invisible model rather than breaking the transform.
 */
public interface CrowModelEngine {

    /** Engine name for logs and {@code /crows debug}. */
    String name();

    /** True when the backing plugin is installed and its API resolved. */
    boolean isAvailable();

    /** Attaches the model at the entity's own scale. */
    boolean applyModel(Entity entity, String modelId);

    /**
     * Attaches the model at an absolute scale, independent of the entity's scale attribute.
     *
     * @param scale absolute model scale, where 1.0 is the model's authored size
     */
    boolean applyModel(Entity entity, String modelId, double scale);

    /**
     * Attaches the model and decides whether the entity's owner can see it.
     *
     * <p>Self view is part of attaching rather than a separate call because the two engines need
     * opposite orderings: ModelEngine has to force-pair the owner <em>before</em> the model exists so its
     * initial spawn reaches them, while BetterModel has to wait until the tracker exists and then show or
     * hide that single viewer. Asking the engine to do it removes the hazard of getting it wrong.
     *
     * @param selfView whether a player wearing this model sees it themselves
     */
    boolean applyModel(Entity entity, String modelId, boolean selfView);

    /**
     * Detaches the model and tears down its renderer. Detaching without destroying leaves the bones
     * rendered where they were, so this must fully despawn for every viewer.
     */
    boolean removeModel(Entity entity, String modelId);

    /** True when {@code modelId} names a model this engine has actually loaded. */
    boolean isModelLoaded(String modelId);

    /**
     * Shows or hides the underlying entity's own body, independently of any model attached to it.
     * With {@code false} the model is all a viewer sees.
     */
    void setBaseEntityVisible(Entity entity, boolean visible);

    /**
     * Turns self view off for a model that is being taken off, for the engines that need the teardown to
     * be explicit. ModelEngine's forced pairing outlives the model, so this has to undo it.
     */
void setSelfView(Player player, boolean selfView);
}