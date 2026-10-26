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
import ru.nothingflare.simpleeconomy.util.ItemNameUtil;
import ru.nothingflare.simpleeconomy.util.MessageUtil;
import ru.nothingflare.simpleeconomy.util.SoundUtil;

import java.util.*;

public class BuyerPriceListGui {

    private final SimpleEconomyPlugin plugin;
    private final Player player;
    private int page;

    public BuyerPriceListGui(SimpleEconomyPlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = Math.max(1, page);
    }

    public void open() {
        LanguageManager lm = plugin.getLanguageManager();
        Map<Material, Double> allPrices = plugin.getDatabaseManager().getAllBuyerPrices();

        List<Map.Entry<Material, Double>> validPrices = new ArrayList<>();
        for (Map.Entry<Material, Double> entry : allPrices.entrySet()) {
            if (entry.getValue() > 0 && entry.getKey() != null && entry.getKey() != Material.AIR) {
                validPrices.add(entry);
            }
        }

        // Sort alphabetically by material name
        validPrices.sort(Comparator.comparing(e -> e.getKey().name()));

        int total = validPrices.size();
        int maxPage = Math.max(1, (int) Math.ceil((double) total / 45.0));
        if (page > maxPage) {
            page = maxPage;
        }

        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("page", String.valueOf(page));
        titlePh.put("max_page", String.valueOf(maxPage));

        String title = lm.getMessage("buyer.price_list_title", titlePh);
        GuiHolder holder = new GuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        // Fill items (slots 0 - 44)
        int startIndex = (page - 1) * 45;
        for (int i = 0; i < 45 && (startIndex + i) < total; i++) {
            Map.Entry<Material, Double> entry = validPrices.get(startIndex + i);
            Material mat = entry.getKey();
            double unitPrice = plugin.getDynamicMarketManager().getBuyerSellPrice(mat);
            double stackPrice = unitPrice * 64;

            Map<String, String> itemPh = new HashMap<>();
            itemPh.put("price", MessageUtil.formatCurrency(unitPrice));
            itemPh.put("stack_price", MessageUtil.formatCurrency(stackPrice));

            ItemBuilder builder = ItemBuilder.from(mat)
                    .name("&f" + ItemNameUtil.formatMaterial(mat))
                    .addLore("")
                    .addLore(lm.getMessage("buyer.price_list_unit", itemPh))
                    .addLore(lm.getMessage("buyer.price_list_stack", itemPh));

            String trendLore = plugin.getDynamicMarketManager().getBuyerTrendLore(mat);
            if (trendLore != null && !trendLore.isEmpty()) {
                builder.addLore(trendLore);
            }

            inv.setItem(i, builder.build());
        }

        // Bottom row filler (slots 45 - 53)
        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s = 45; s < 54; s++) {
            inv.setItem(s, filler);
        }

        // Slot 48: Arrow Back
        // If page == 1 -> Exit back to /buyer GUI
        // If page > 1 -> Go to previous page
        if (page == 1) {
            ItemStack backToBuyer = ItemBuilder.from(Material.ARROW)
                    .name(lm.getMessage("buyer.button_back_to_buyer"))
                    .build();
            inv.setItem(48, backToBuyer);
            holder.setAction(48, event -> {
                SoundUtil.playClick(player);
                new BuyerGui(plugin, player).open();
            });
        } else {
            ItemStack prevPage = ItemBuilder.from(Material.ARROW)
                    .name(lm.getMessage("auction.button_previous_page"))
                    .build();
            inv.setItem(48, prevPage);
            holder.setAction(48, event -> {
                SoundUtil.playPageTurn(player);
                new BuyerPriceListGui(plugin, player, page - 1).open();
            });
        }

        // Slot 50: Arrow Next (if page < maxPage)
        if (page < maxPage) {
            ItemStack nextPage = ItemBuilder.from(Material.ARROW)
                    .name(lm.getMessage("auction.button_next_page"))
                    .build();
            inv.setItem(50, nextPage);
            holder.setAction(50, event -> {
                SoundUtil.playPageTurn(player);
                new BuyerPriceListGui(plugin, player, page + 1).open();
            });
        }

        player.openInventory(inv);
        SoundUtil.playOpen(player);
    }
}
