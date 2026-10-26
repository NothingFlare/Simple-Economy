/*
 * MIT License
 *
 * Copyright (c) 2026 NothingFlare
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.nothingflare.simpleeconomy.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;
import ru.nothingflare.simpleeconomy.config.LanguageManager;
import ru.nothingflare.simpleeconomy.util.MessageUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimpleEconomyCommand implements CommandExecutor, TabCompleter {

    private final SimpleEconomyPlugin plugin;

    public SimpleEconomyCommand(SimpleEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();

        if (!sender.hasPermission("simpleeconomy.admin")) {
            MessageUtil.sendMessage(sender, lm.getPrefix() + lm.getMessage("no_permission"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().load();
            plugin.getLanguageManager().load();
            if (plugin.getDynamicMarketManager() != null) {
                plugin.getDynamicMarketManager().load();
            }
            MessageUtil.sendMessage(sender, lm.getPrefix() + lm.getMessage("reload_success"));
            return true;
        }

        MessageUtil.sendMessage(sender, "&6SimpleEconomy &7v" + plugin.getDescription().getVersion() + " by &eNothingFlare");
        MessageUtil.sendMessage(sender, "&7Commands: &f/ah&7, &f/buyer&7, &f/shop&7, &f/se reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("simpleeconomy.admin")) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                list.add("reload");
            }
        }
        return list;
    }
}
