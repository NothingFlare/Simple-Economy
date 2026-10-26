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

package ru.nothingflare.simpleeconomy.gui.buyer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;
import ru.nothingflare.simpleeconomy.config.LanguageManager;
import ru.nothingflare.simpleeconomy.gui.GuiHolder;
import ru.nothingflare.simpleeconomy.util.ItemBuilder;
import ru.nothingflare.simpleeconomy.util.MessageUtil;
import ru.nothingflare.simpleeconomy.util.SoundUtil;

import java.util.HashMap;
import java.util.Map;

public class BuyerGui {

    private final SimpleEconomyPlugin plugin;
    private final Player player;

    public BuyerGui(SimpleEconomyPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        LanguageManager lm = plugin.getLanguageManager();
        String title = lm.getMessage("buyer.gui_title");
        GuiHolder holder = new GuiHolder();
        holder.setAllowPlayerInventoryClick(true);
        holder.setAllowUpperInventoryPlacement(true);
        holder.setMaxPlaceableSlot(44);

        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        // Fill entire bottom row with dark glass
        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s = 45; s < 54; s++) {
            inv.setItem(s, filler);
            holder.setAction(s, event -> event.setCancelled(true));
        }

        // Slot 47 (3rd slot from left in bottom row): Snowball (View sellable items)
        ItemStack snowball = ItemBuilder.from(Material.SNOWBALL)
                .name(lm.getMessage("buyer.price_list_button"))
                .lore(lm.getMessageList("buyer.price_list_lore"))
                .build();
        inv.setItem(47, snowball);
        holder.setAction(47, event -> {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            new BuyerPriceListGui(plugin, player, 1).open();
        });

        // Slot 49 (5th slot from left in bottom row): Emerald (Current balance)
        updateBalanceSlot(inv, holder);

        // Slot 51 (7th slot from left in bottom row): Name Tag (Sell button)
        ItemStack sellButton = ItemBuilder.from(Material.NAME_TAG)
                .name(lm.getMessage("buyer.sell_button"))
                .lore(lm.getMessageList("buyer.sell_lore"))
                .build();
        inv.setItem(51, sellButton);
        holder.setAction(51, event -> {
            event.setCancelled(true);
            processSell(inv, holder);
        });

        // Handle Close: return all unsold items in slots 0 - 44
        holder.setCloseAction(event -> {
            for (int i = 0; i < 45; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                    if (!leftover.isEmpty()) {
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    }
                    inv.setItem(i, null);
                }
            }
        });

        player.openInventory(inv);
        SoundUtil.playOpen(player);
    }

    private void updateBalanceSlot(Inventory inv, GuiHolder holder) {
        LanguageManager lm = plugin.getLanguageManager();
        double balance = plugin.getEconomyHook().getBalance(player);
        Map<String, String> ph = new HashMap<>();
        ph.put("balance", MessageUtil.formatCurrency(balance));

        ItemStack emerald = ItemBuilder.from(Material.EMERALD)
                .name(lm.getMessage("buyer.balance_button"))
                .lore(lm.getMessageList("buyer.balance_lore", ph))
                .build();
        inv.setItem(49, emerald);
        holder.setAction(49, event -> event.setCancelled(true));
    }

    private void processSell(Inventory inv, GuiHolder holder) {
        LanguageManager lm = plugin.getLanguageManager();
        double totalEarned = 0.0;
        int totalItemsSold = 0;
        boolean hasUnsold = false;
        Map<Material, Integer> soldCounts = new HashMap<>();

        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            double pricePerUnit = plugin.getDynamicMarketManager().getBuyerSellPrice(item.getType());
            if (pricePerUnit > 0) {
                int amt = item.getAmount();
                totalEarned += pricePerUnit * amt;
                totalItemsSold += amt;
                soldCounts.merge(item.getType(), amt, Integer::sum);
                inv.setItem(i, null);
            } else {
                hasUnsold = true;
            }
        }

        if (totalEarned > 0) {
            plugin.getEconomyHook().deposit(player, totalEarned);
            updateBalanceSlot(inv, holder);

            // Record dynamic market sale -> price decreases due to market supply saturation
            for (Map.Entry<Material, Integer> entry : soldCounts.entrySet()) {
                plugin.getDynamicMarketManager().recordBuyerSale(entry.getKey(), entry.getValue());
            }

            if (plugin.getConfigManager().isBuyerSoundEnabled()) {
                SoundUtil.playCoins(player);
            }

            Map<String, String> ph = new HashMap<>();
            ph.put("count", String.valueOf(totalItemsSold));
            ph.put("money", MessageUtil.formatCurrency(totalEarned));
            MessageUtil.sendMessage(player, lm.getMessage("buyer.sell_success", ph));

            if (hasUnsold) {
                MessageUtil.sendMessage(player, lm.getMessage("buyer.returned_unsold"));
            }
        } else {
            SoundUtil.playError(player);
            MessageUtil.sendMessage(player, lm.getMessage("buyer.nothing_to_sell"));
        }
    }
}
