package codes.castled.karasu.managers;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Stand-in used when no model engine is available or the operator asked for none. Keeps the morph and
 * swarm code free of null checks: they call the engine exactly as they would a live one and simply get
 * nothing back.
 */
public class NoopCrowModelEngine implements CrowModelEngine {

    private final String reason;

    public NoopCrowModelEngine(String reason) {
        this.reason = reason;
    }

    /** Why no engine is in use, for {@code /crows debug}. */
    public String reason() {
        return reason;
    }

    @Override
    public String name() {
        return "none";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean applyModel(Entity entity, String modelId) {
        return false;
    }

    @Override
    public boolean applyModel(Entity entity, String modelId, double scale) {
        return false;
    }

    @Override
    public boolean applyModel(Entity entity, String modelId, boolean selfView) {
        return false;
    }

    @Override
    public boolean removeModel(Entity entity, String modelId) {
        return false;
    }

    @Override
    public boolean isModelLoaded(String modelId) {
        return false;
    }

    @Override
    public void setBaseEntityVisible(Entity entity, boolean visible) {
    }

    @Override
    public void setSelfView(Player player, boolean selfView) {
    }
}