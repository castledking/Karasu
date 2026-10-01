package codes.castled.karasu.pack;

import codes.castled.karasu.KarasuPlugin;
import codes.castled.karasu.managers.CrowConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Owns how the model engine's resource pack reaches players so the crow models have their textures.
 *
 * <p>Behaviour is gated by {@code karasu.resource-pack.use-resourcepack} (default false), mirroring
 * Chess's resource-pack delivery:
 *
 * <ul>
 *   <li><b>false</b> - the plugin does nothing: it neither registers nor sends the pack, letting
 *       you manage it yourself (BetterModel, ModelEngine or ResourcePackManager).
 *   <li><b>true + ResourcePackManager installed</b> - the pack at {@code pack-file} is registered
 *       with RSPM, which merges it into the server's combined pack (essential alongside
 *       Nexo/ItemsAdder, where a direct send would fight their pack).
 *   <li><b>true + no ResourcePackManager</b> - the pack is pushed to each joining player with
 *       {@code setResourcePack} from {@code resource-pack.url}. The SHA-1 is computed from the
 *       pack file, so the admin only needs to host that exact file and point the URL at it.
 * </ul>
 *
 * <p>All RSPM API calls are isolated in {@link ResourcePackManagerBootstrap} (reflection only), so
 * this class stays safe to load when RSPM is absent.
 */
public final class ResourcePackService {

  private static final String RSPM_PLUGIN_NAME = "ResourcePackManager";
  private static final long FIRST_ATTEMPT_DELAY_TICKS = 1L;
  private static final long MAX_ATTEMPT_DELAY_TICKS = 200L;

  private final KarasuPlugin plugin;
  private final boolean useResourcePack;
  private final String packUrl;
  private final String packFile;

  /** True while RSPM owns pack distribution, so we must not push a pack ourselves. */
  private volatile boolean managedByResourcePackManager;

  /** SHA-1 of the pack file, for the direct-send path; null until computed. */
  private byte[] packSha1;

  public ResourcePackService(KarasuPlugin plugin, CrowConfig config) {
    this.plugin = plugin;
    this.useResourcePack = config.getUseResourcePack();
    this.packUrl = config.getResourcePackUrl();
    this.packFile = config.getResourcePackFile();
  }

  /** Arms pack delivery per config: register with RSPM or prepare the direct-send path. */
  public void setup() {
    if (!useResourcePack) {
      plugin
          .getLogger()
          .info(
              "karasu.resource-pack.use-resourcepack is false; not sending or merging a pack. "
                  + "Manage it yourself; the active engine writes its pack to " + packFile + ".");
      return;
    }

    if (Bukkit.getPluginManager().getPlugin(RSPM_PLUGIN_NAME) != null) {
      managedByResourcePackManager = true;
      scheduleRegistration(FIRST_ATTEMPT_DELAY_TICKS);
      return;
    }

    if (packUrl.isBlank()) {
      plugin
          .getLogger()
          .warning(
              "No ResourcePackManager installed and karasu.resource-pack.url is blank, so players "
                  + "will not receive the model engine pack. Host " + packFile
                  + " somewhere and set karasu.resource-pack.url, or install ResourcePackManager.");
      return;
    }
    packSha1 = sha1Of(packFile);
    if (packSha1 != null) {
      plugin.getLogger().info("Sending the " + packFile + " pack directly from " + packUrl + " on join.");
    }
  }

  /**
   * Pushes the pack to a joining player when the direct-send path is armed. No-op when RSPM owns
   * distribution, when disabled, or when no URL/hash is available.
   */
  public void sendPackTo(Player player) {
    if (!useResourcePack || managedByResourcePackManager || packSha1 == null || packUrl.isBlank()) {
      return;
    }
    Bukkit.getScheduler().runTask(plugin, () -> player.setResourcePack(packUrl, packSha1));
  }

  private void scheduleRegistration(long delayTicks) {
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              if (Bukkit.getPluginManager().isPluginEnabled(RSPM_PLUGIN_NAME)) {
                if (ResourcePackManagerBootstrap.register(plugin, packFile)) {
                  return;
                }
              }
              long nextDelay = delayTicks * 2;
              if (nextDelay > MAX_ATTEMPT_DELAY_TICKS) {
                managedByResourcePackManager = false;
                plugin
                    .getLogger()
                    .warning(
                        "Gave up registering the Karasu pack with ResourcePackManager; "
                            + "set karasu.resource-pack.url for the direct-send path instead.");
                return;
              }
              scheduleRegistration(nextDelay);
            },
            delayTicks);
  }

  private byte[] sha1Of(String file) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      Path path = Path.of(file);
      if (!Files.isReadable(path)) {
        plugin.getLogger().warning("Pack file not readable: " + file);
        return null;
      }
      return digest.digest(Files.readAllBytes(path));
    } catch (NoSuchAlgorithmException | IOException exception) {
      plugin.getLogger().warning("Could not hash the Karasu pack: " + exception.getMessage());
      return null;
    }
  }
}