package codes.castled.karasu.commands;

import codes.castled.karasu.KarasuPlugin;
import codes.castled.karasu.managers.CrowEffectManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.Arrays;
import java.util.List;

public class CrowsCommand implements CommandExecutor, TabCompleter {
    private final KarasuPlugin plugin;
    private final CrowEffectManager crowEffectManager;
    
    public CrowsCommand(KarasuPlugin plugin) {
        this.plugin = plugin;
        this.crowEffectManager = plugin.getCrowEffectManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("karasu.crows")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "morph":
                crowEffectManager.toggleTransformation(player);
                return true;
            case "info":
                crowEffectManager.showInfo(player);
                return true;
            case "debug":
                showDebug(player);
                return true;
            default:
                showHelp(player);
                return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("morph", "info", "debug");
        }
        return null;
    }
    
    private void showHelp(Player player) {
        player.sendMessage("§8=== §6Karasu Crow Commands §8===");
        player.sendMessage("§e/crows morph §7- Toggle crow transformation (flying model while flying)");
        player.sendMessage("§e/crows info §7- Show crow transformation status");
        player.sendMessage("§e/crows debug §7- Show ModelEngine integration status");
    }
    
    private void showDebug(Player player) {
        codes.castled.karasu.managers.ModelEngineBridge bridge = plugin.getModelEngineBridge();
        player.sendMessage("§8=== §6Karasu Debug §8===");
        player.sendMessage("§eModelEngine plugin: " + (plugin.getServer().getPluginManager().getPlugin("ModelEngine") != null ? "§afound" : "§cmissing"));
        player.sendMessage("§eBridge enabled: " + (bridge.isBridgeEnabled() ? "§a" : "§c") + bridge.isBridgeEnabled());
        player.sendMessage("§eBlueprint crow_fly: " + (bridge.isBlueprintAvailable("crow_fly") ? "§a" : "§c") + bridge.isBlueprintAvailable("crow_fly"));
        player.sendMessage("§eBlueprint crow_stand: " + (bridge.isBlueprintAvailable("crow_stand") ? "§a" : "§c") + bridge.isBlueprintAvailable("crow_stand"));
        player.sendMessage("§eCurrent model: §f" + crowEffectManager.getCurrentModelId(player));
    }
}