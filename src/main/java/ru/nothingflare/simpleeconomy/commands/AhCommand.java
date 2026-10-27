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

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;
import ru.nothingflare.simpleeconomy.config.LanguageManager;
import ru.nothingflare.simpleeconomy.gui.ah.AuctionAdminGui;
import ru.nothingflare.simpleeconomy.gui.ah.AuctionMainGui;
import ru.nothingflare.simpleeconomy.scheduler.TaskScheduler;
import ru.nothingflare.simpleeconomy.util.ItemNameUtil;
import ru.nothingflare.simpleeconomy.util.MessageUtil;
import ru.nothingflare.simpleeconomy.util.SoundUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AhCommand implements CommandExecutor, TabCompleter {

    private final SimpleEconomyPlugin plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public AhCommand(SimpleEconomyPlugin plugin) {
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

        if (!plugin.getConfigManager().isAuctionEnabled()) {
            MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("module_disabled"));
            return true;
        }

        if (!player.hasPermission("simpleeconomy.auction.use")) {
            MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("no_permission"));
            return true;
        }

        if (args.length == 0) {
            new AuctionMainGui(plugin, player, 1).open();
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            if (!plugin.getConfigManager().isAuctionAdminAllowed(player)) {
                SoundUtil.playError(player);
                if (!player.isOp()) {
                    MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("no_permission"));
                } else {
                    MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.admin_not_whitelisted"));
                }
                return true;
            }

            SoundUtil.playClick(player);
            new AuctionAdminGui(plugin, player, 1).open();
            return true;
        }

        if (args[0].equalsIgnoreCase("sell")) {
            if (args.length < 2) {
                MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.sell_usage"));
                return true;
            }

            double price;
            try {
                price = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                Map<String, String> ph = new HashMap<>();
                ph.put("min", MessageUtil.formatCurrency(plugin.getConfigManager().getMinPrice()));
                ph.put("max", MessageUtil.formatCurrency(plugin.getConfigManager().getMaxPrice()));
                MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.invalid_price", ph));
                return true;
            }

            if (Double.isNaN(price) || Double.isInfinite(price) || price < plugin.getConfigManager().getMinPrice() || price > plugin.getConfigManager().getMaxPrice()) {
                Map<String, String> ph = new HashMap<>();
                ph.put("min", MessageUtil.formatCurrency(plugin.getConfigManager().getMinPrice()));
                ph.put("max", MessageUtil.formatCurrency(plugin.getConfigManager().getMaxPrice()));
                MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.invalid_price", ph));
                return true;
            }

            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.hold_item_to_sell"));
                return true;
            }

            // Check cooldown (if enabled)
            int cooldownSec = plugin.getConfigManager().getListingCooldownSeconds();
            long now = System.currentTimeMillis();
            if (cooldownSec > 0 && cooldowns.containsKey(player.getUniqueId())) {
                long last = cooldowns.get(player.getUniqueId());
                long diff = (now - last) / 1000;
                if (diff < cooldownSec) {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("seconds", String.valueOf(cooldownSec - diff));
                    MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.cooldown_active", ph));
                    return true;
                }
            }

            // Cleanup old cooldowns if map grows
            if (cooldowns.size() > 500) {
                cooldowns.entrySet().removeIf(entry -> (now - entry.getValue()) > 300000L);
            }

            final ItemStack toList = hand.clone();
            final double finalPrice = price;

            TaskScheduler.runAsync(plugin, () -> {
                int activeCount = plugin.getDatabaseManager().getPlayerActiveListingCount(player.getUniqueId());
                int maxSlots = plugin.getConfigManager().getMaxAuctionSlots();
                if (activeCount >= maxSlots) {
                    TaskScheduler.runEntity(plugin, player, () -> {
                        SoundUtil.playError(player);
                        Map<String, String> ph = new HashMap<>();
                        ph.put("max", String.valueOf(maxSlots));
                        MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.max_slots_reached", ph));
                    });
                    return;
                }

                TaskScheduler.runEntity(plugin, player, () -> {
                    ItemStack currentHand = player.getInventory().getItemInMainHand();
                    if (!currentHand.isSimilar(toList) || currentHand.getAmount() < toList.getAmount()) {
                        SoundUtil.playError(player);
                        MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.hold_item_to_sell"));
                        return;
                    }

                    player.getInventory().setItemInMainHand(null);

                    TaskScheduler.runAsync(plugin, () -> {
                        int delaySec = plugin.getConfigManager().getPublishDelaySeconds();
                        long id = plugin.getDatabaseManager().createListing(player.getUniqueId(), player.getName(), toList, finalPrice, delaySec);
                        if (id > 0) {
                            if (cooldownSec > 0) {
                                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                            }
                            TaskScheduler.runEntity(plugin, player, () -> {
                                SoundUtil.playListingCreated(player);
                                Map<String, String> ph = new HashMap<>();
                                ph.put("item", ItemNameUtil.getItemName(toList));
                                ph.put("price", MessageUtil.formatCurrency(finalPrice));
                                if (delaySec > 0) {
                                    ph.put("seconds", String.valueOf(delaySec));
                                    MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.listing_created_timer", ph));
                                } else {
                                    MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.listing_created", ph));
                                }
                            });
                        } else {
                            // Give item back if error
                            TaskScheduler.runEntity(plugin, player, () -> {
                                SoundUtil.playError(player);
                                player.getInventory().addItem(toList);
                            });
                        }
                    });
                });
            });

            return true;
        }

        if (plugin.getConfigManager().isAuctionAdminAllowed(player)) {
            MessageUtil.sendMessage(player, lm.getPrefix() + "&cИспользование: /ah [sell <цена> | admin]");
        } else {
            MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.sell_usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("sell".startsWith(args[0].toLowerCase())) {
                completions.add("sell");
            }
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (plugin.getConfigManager().isAuctionAdminAllowed(p) && "admin".startsWith(args[0].toLowerCase())) {
                    completions.add("admin");
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            List<String> suggestions = Arrays.asList("10", "50", "100", "500", "1000");
            for (String s : suggestions) {
                if (s.startsWith(args[1])) {
                    completions.add(s);
                }
            }
        }
        return completions;
    }
}
