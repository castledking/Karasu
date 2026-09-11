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

public class CrowCommand implements CommandExecutor, TabCompleter {
    private final KarasuPlugin plugin;
    private final CrowEffectManager crowEffectManager;
    
    public CrowCommand(KarasuPlugin plugin) {
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
        
        if (!player.hasPermission("karasu.crow")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "spawn":
                int count = 5;
                if (args.length > 1) {
                    try {
                        count = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid number!");
                        return true;
                    }
                }
                crowEffectManager.spawnCrows(player, count);
                return true;
            default:
                showHelp(player);
                return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("spawn");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return Arrays.asList("1", "2", "3", "4", "5", "10", "20");
        }
        return null;
    }
    
    private void showHelp(Player player) {
        player.sendMessage("§8=== §6Karasu Crow Commands §8===");
        player.sendMessage("§e/crow spawn [count] §7- Spawn crows at location (default: 5)");
    }
}