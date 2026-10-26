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
import org.bukkit.entity.Player;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;
import ru.nothingflare.simpleeconomy.config.LanguageManager;
import ru.nothingflare.simpleeconomy.gui.shop.ShopSectionsGui;
import ru.nothingflare.simpleeconomy.util.MessageUtil;

public class ShopCommand implements CommandExecutor {

    private final SimpleEconomyPlugin plugin;

    public ShopCommand(SimpleEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();

        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, lm.getPrefix() + lm.getMessage("player_only"));
            return true;
        }

        Player player = (Player) sender;

        if (!plugin.getConfigManager().isShopEnabled()) {
            MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("module_disabled"));
            return true;
        }

        if (!player.hasPermission("simpleeconomy.shop.use")) {
            MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("no_permission"));
            return true;
        }

        new ShopSectionsGui(plugin, player).open();
        return true;
    }
}
