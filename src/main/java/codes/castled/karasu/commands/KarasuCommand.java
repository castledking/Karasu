package codes.castled.karasu.commands;

import codes.castled.karasu.KarasuPlugin;
import codes.castled.karasu.managers.CrowEffectManager;
import codes.castled.karasu.managers.CrowModelEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Single entry point for Karasu: {@code /karasu <subcommand>}, aliased to {@code /kcrows} and
 * {@code /crow} so the older {@code /crow spawn} still works.
 */
public class KarasuCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBCOMMANDS = List.of("morph", "info", "debug", "spawn");
    private static final List<String> ANY_SUBCOMMANDS = List.of("morph", "info", "debug", "spawn", "reload");
    private static final List<String> COUNTS = List.of("1", "2", "3", "4", "5", "10", "20");

    private final KarasuPlugin plugin;
    private final CrowEffectManager crowEffectManager;

    public KarasuCommand(KarasuPlugin plugin) {
        this.plugin = plugin;
        this.crowEffectManager = plugin.getCrowEffectManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> {
                if (!require(sender, "karasu.reload")) yield true;
                plugin.reloadKarasu().forEach(sender::sendMessage);
                yield true;
            }
            case "morph", "info", "debug", "spawn" -> {
                if (!require(sender, "karasu.crows")) yield true;
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c/" + label + " " + sub + " can only be used by players!");
                    yield true;
                }
                yield handlePlayer(player, label, sub, args);
            }
            default -> {
                showHelp(sender);
                yield true;
            }
        };
    }

    private boolean handlePlayer(Player player, String label, String sub, String[] args) {
        switch (sub) {
            case "morph" -> crowEffectManager.toggleTransformation(player);
            case "info" -> crowEffectManager.showInfo(player);
            case "debug" -> showDebug(player);
            case "spawn" -> spawnCrows(player, args);
            default -> showHelp(player);
        }
        return true;
    }

    private void spawnCrows(Player player, String[] args) {
        int count = 5;
        if (args.length > 1) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid number!");
                return;
            }
        }
        crowEffectManager.spawnCrows(player, count);
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage("§cYou don't have permission to do that!");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options =
                sender instanceof Player ? PLAYER_SUBCOMMANDS : ANY_SUBCOMMANDS;
        if (args.length == 1) {
            return prefixed(options, args[0], sender);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return prefixed(COUNTS, args[1], sender);
        }
        return List.of();
    }

    private List<String> prefixed(List<String> options, String prefix, CommandSender sender) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lower) && sender.hasPermission(permissionFor(option))) {
                matches.add(option);
            }
        }
        return matches;
    }

    private static String permissionFor(String subcommand) {
        return "reload".equals(subcommand) ? "karasu.reload" : "karasu.crows";
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§8=== §6Karasu §8===");
        if (sender.hasPermission("karasu.crows")) {
            sender.sendMessage("§e/karasu morph §7- Toggle crow transformation");
            sender.sendMessage("§e/karasu info §7- Show your crow transformation status");
            sender.sendMessage("§e/karasu spawn [count] §7- Spawn crows at your location (default: 5)");
            sender.sendMessage("§e/karasu debug §7- Show model engine status");
        }
        if (sender.hasPermission("karasu.reload")) {
            sender.sendMessage("§e/karasu reload §7- Reload the config and re-pick the model engine");
        }
    }

    private void showDebug(Player player) {
        CrowModelEngine active = plugin.getCrowModelEngine();
        player.sendMessage("§8=== §6Karasu Debug §8===");
        player.sendMessage("§eActive engine: §f" + active.name() + status(active.isAvailable()));
        player.sendMessage("§eConfigured as: §f"
                + plugin.getCrowConfig().getModelEngineType().name().toLowerCase(Locale.ROOT));
        player.sendMessage("§eBetterModel: " + probe(betterModelInstalled(), betterModelUsable()));
        player.sendMessage("§eModelEngine: " + probe(modelEngineInstalled(), modelEngineUsable()));
        player.sendMessage("§eFlying model §f" + plugin.getCrowConfig().getFlyingModelId()
                + "§e loaded: " + status(active.isModelLoaded(plugin.getCrowConfig().getFlyingModelId())));
        player.sendMessage("§ePerched model §f" + plugin.getCrowConfig().getPerchedModelId()
                + "§e loaded: " + status(active.isModelLoaded(plugin.getCrowConfig().getPerchedModelId())));
        if (active instanceof codes.castled.karasu.managers.BetterModelBridge betterModel) {
            player.sendMessage("§eBridge model scale: §f" + betterModel.modelScale()
                    + "§e | hidden bones: §f" + betterModel.hiddenBones());
            player.sendMessage("§eLive scaler: §f" + betterModel.describeScaler(player));
        }
        player.sendMessage("§eModel scale: §f" + plugin.getCrowConfig().getModelScale()
                + "§e | base-scale: §f" + plugin.getCrowConfig().getBaseScale()
                + "§e | hide-self-body: " + status(plugin.getCrowConfig().isSelfBodyHidden()));
        player.sendMessage("§eCurrent model: §f" + crowEffectManager.getCurrentModelId(player));
    }

    private boolean betterModelInstalled() {
        return plugin.getServer().getPluginManager().getPlugin("BetterModel") != null;
    }

    private boolean betterModelUsable() {
        return plugin.getBetterModelBridge() != null && plugin.getBetterModelBridge().isAvailable();
    }

    private boolean modelEngineInstalled() {
        return plugin.getServer().getPluginManager().getPlugin("ModelEngine") != null;
    }

    private boolean modelEngineUsable() {
        return plugin.getModelEngineBridge() != null && plugin.getModelEngineBridge().isAvailable();
    }

    /** Distinguishes "plugin not installed" from "installed but its API did not resolve". */
    private static String probe(boolean installed, boolean usable) {
        if (!installed) return "§cmissing";
        return usable ? "§ausable" : "§cinstalled but unusable";
    }

    private static String status(boolean ok) {
        return ok ? "§ayes" : "§cno";
    }
}