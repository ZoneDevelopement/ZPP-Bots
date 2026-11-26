package dev.nandi0813.zppbots.command;

import dev.nandi0813.api.Interface.BotRegistry;
import dev.nandi0813.api.Interface.BotSetup;
import dev.nandi0813.api.ZonePracticeApi;
import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.config.Messages;
import dev.nandi0813.zppbots.menu.BotSetupMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** {@code /botpractice} opens the difficulty menu; {@code /botpractice rematch} starts the last bot setup again. */
public final class BotPracticeCommand implements CommandExecutor, TabCompleter {

    private final ZPPBots plugin;

    public BotPracticeCommand(ZPPBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        Messages messages = plugin.getMessages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "PLAYER-ONLY");
            return true;
        }
        if (!player.hasPermission("zppbots.use")) {
            messages.send(player, "BOT-PRACTICE.NO-PERMISSION");
            return true;
        }
        BotRegistry registry = ZonePracticeApi.getInstance().getBotRegistry();
        if (args.length == 0) {
            if (!registry.isAvailable() || plugin.getProvider() == null || plugin.getSettings() == null
                    || plugin.getSettings().error() != null) {
                messages.send(player, "BOT-PRACTICE.UNAVAILABLE");
                return true;
            }
            new BotSetupMenu(plugin, player).open();
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("rematch")) {
            BotSetup setup = registry.getLastSetup(player);
            if (setup == null) messages.send(player, "BOT-PRACTICE.NO-REMATCH");
            else registry.start(player, setup);
            return true;
        }
        messages.send(player, "BOT-PRACTICE.HELP");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        if (args.length == 1) return matching(args[0], "rematch");
        return List.of();
    }

    private static List<String> matching(String prefix, String... options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return Arrays.stream(options).filter(option -> option.startsWith(lower)).toList();
    }
}
