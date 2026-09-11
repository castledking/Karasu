package codes.castled.karasu.pack;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

final class ResourcePackManagerBootstrap {

  private static final String API_CLASS = "com.magmaguy.resourcepackmanager.api.ResourcePackManagerAPI";

  private ResourcePackManagerBootstrap() {}

  static boolean register(Plugin plugin, String localPath) {
    try {
      Class<?> api = Class.forName(API_CLASS);
      Method register =
          api.getMethod(
              "registerLocalResourcePack",
              String.class,
              String.class,
              boolean.class,
              boolean.class,
              boolean.class,
              String.class);
      register.invoke(null, plugin.getName(), localPath, false, true, true, null);
      plugin.getLogger().info("Registered the Karasu resource pack with ResourcePackManager (" + localPath + ").");
      return true;
    } catch (Throwable throwable) {
      plugin.getLogger().fine("ResourcePackManager registration not ready yet: " + throwable);
      return false;
    }
  }
}