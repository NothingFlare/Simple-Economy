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

package ru.nothingflare.simpleeconomy.gui.ah;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;
import ru.nothingflare.simpleeconomy.config.LanguageManager;
import ru.nothingflare.simpleeconomy.database.models.AuctionListing;
import ru.nothingflare.simpleeconomy.gui.GuiHolder;
import ru.nothingflare.simpleeconomy.scheduler.TaskScheduler;
import ru.nothingflare.simpleeconomy.util.ItemBuilder;
import ru.nothingflare.simpleeconomy.util.ItemNameUtil;
import ru.nothingflare.simpleeconomy.util.MessageUtil;
import ru.nothingflare.simpleeconomy.util.SoundUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuctionAdminGui {

    private final SimpleEconomyPlugin plugin;
    private final Player player;
    private int page;

    public AuctionAdminGui(SimpleEconomyPlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = Math.max(1, page);
    }

    public void open() {
        TaskScheduler.runAsync(plugin, () -> {
            int total = plugin.getDatabaseManager().getTotalActiveListings();
            int maxPage = Math.max(1, (int) Math.ceil((double) total / 45.0));
            if (page > maxPage) {
                page = maxPage;
            }

            int offset = (page - 1) * 45;
            List<AuctionListing> listings = plugin.getDatabaseManager().getActiveListings(offset, 45);

            TaskScheduler.runEntity(plugin, player, () -> buildAndShow(listings, page, maxPage));
        });
    }

    private void buildAndShow(List<AuctionListing> listings, int curPage, int maxPage) {
        LanguageManager lm = plugin.getLanguageManager();
        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("page", String.valueOf(curPage));
        titlePlaceholders.put("max_page", String.valueOf(maxPage));

        String title = lm.getMessage("auction.admin_gui_title", titlePlaceholders);
        GuiHolder holder = new GuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color(title));
        holder.setInventory(inv);

        long expireMillis = (long) plugin.getConfigManager().getExpireHours() * 3600000L;
        long now = System.currentTimeMillis();

        for (int i = 0; i < listings.size() && i < 45; i++) {
            AuctionListing listing = listings.get(i);
            ItemStack item = listing.getItem();
            if (item == null) continue;

            ItemBuilder builder = ItemBuilder.from(item);
            if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
                builder.name("&f" + ItemNameUtil.formatMaterial(item.getType()));
            }

            long timeLeft = (listing.getCreatedAt() + expireMillis) - now;
            String timeFormatted = MessageUtil.formatDuration(Math.max(0, timeLeft));

            Map<String, String> itemPlaceholders = new HashMap<>();
            itemPlaceholders.put("price", MessageUtil.formatCurrency(listing.getPrice()));
            itemPlaceholders.put("seller", listing.getSellerName());
            itemPlaceholders.put("time", timeFormatted);

            builder.addLore("");
            builder.addLore(lm.getMessage("auction.item_lore.price", itemPlaceholders));
            builder.addLore(lm.getMessage("auction.item_lore.seller", itemPlaceholders));
            builder.addLore(lm.getMessage("auction.item_lore.expires", itemPlaceholders));
            builder.addLore("");
            builder.addLore(lm.getMessage("auction.item_lore.click_to_delete_admin"));

            inv.setItem(i, builder.build());

            final long listingId = listing.getId();
            holder.setAction(i, event -> {
                SoundUtil.playClick(player);
                new AuctionAdminConfirmGui(plugin, player, listingId, curPage).open();
            });
        }

        // Bottom row (slots 45-53)
        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s = 45; s < 54; s++) {
            inv.setItem(s, filler);
        }

        // Slot 45: Back Arrow to normal /ah
        ItemStack backArrow = ItemBuilder.from(Material.ARROW)
                .name(lm.getMessage("auction.button_back_to_ah"))
                .build();
        inv.setItem(45, backArrow);
        holder.setAction(45, event -> {
            SoundUtil.playClick(player);
            new AuctionMainGui(plugin, player, 1).open();
        });

        // Slot 48: Prev Page
        if (curPage > 1) {
            ItemStack prevArrow = ItemBuilder.from(Material.ARROW)
                    .name(lm.getMessage("auction.button_previous_page"))
                    .build();
            inv.setItem(48, prevArrow);
            holder.setAction(48, event -> {
                SoundUtil.playPageTurn(player);
                new AuctionAdminGui(plugin, player, curPage - 1).open();
            });
        }

        // Slot 49: Refresh
        ItemStack refreshStar = ItemBuilder.from(Material.NETHER_STAR)
                .name(lm.getMessage("auction.button_refresh"))
                .build();
        inv.setItem(49, refreshStar);
        holder.setAction(49, event -> {
            SoundUtil.playRefresh(player);
            new AuctionAdminGui(plugin, player, curPage).open();
        });

        // Slot 50: Next Page
        if (curPage < maxPage) {
            ItemStack nextArrow = ItemBuilder.from(Material.ARROW)
                    .name(lm.getMessage("auction.button_next_page"))
                    .build();
            inv.setItem(50, nextArrow);
            holder.setAction(50, event -> {
                SoundUtil.playPageTurn(player);
                new AuctionAdminGui(plugin, player, curPage + 1).open();
            });
        }

        // Slot 53: Barrier icon indicator
        ItemStack adminIndicator = ItemBuilder.from(Material.BARRIER)
                .name(lm.getMessage("auction.admin_indicator_name"))
                .lore(lm.getMessageList("auction.admin_indicator_lore"))
                .build();
        inv.setItem(53, adminIndicator);
        holder.setAction(53, event -> event.setCancelled(true));

        player.openInventory(inv);
        SoundUtil.playOpen(player);
    }
}
