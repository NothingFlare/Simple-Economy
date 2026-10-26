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
import org.bukkit.Sound;
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

public class AuctionMainGui {

    private final SimpleEconomyPlugin plugin;
    private final Player player;
    private int page;

    public AuctionMainGui(SimpleEconomyPlugin plugin, Player player, int page) {
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

        String title = lm.getMessage("auction.gui_title", titlePlaceholders);
        GuiHolder holder = new GuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        // Fill listings (slots 0 - 44)
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
            itemPlaceholders.put("seconds", String.valueOf(listing.getSecondsUntilPublish()));

            builder.addLore("");
            builder.addLore(lm.getMessage("auction.item_lore.price", itemPlaceholders));
            builder.addLore(lm.getMessage("auction.item_lore.seller", itemPlaceholders));

            boolean isOwner = listing.getSellerUuid().equals(player.getUniqueId());

            if (!listing.isPublished()) {
                builder.addLore(lm.getMessage("auction.item_lore.published_in", itemPlaceholders));
                builder.addLore("");
                if (isOwner) {
                    builder.addLore(lm.getMessage("auction.item_lore.your_item", itemPlaceholders));
                } else {
                    builder.addLore(lm.getMessage("auction.item_lore.countdown_status", itemPlaceholders));
                }
            } else {
                builder.addLore(lm.getMessage("auction.item_lore.expires", itemPlaceholders));
                builder.addLore("");
                if (isOwner) {
                    builder.addLore(lm.getMessage("auction.item_lore.your_item", itemPlaceholders));
                } else {
                    builder.addLore(lm.getMessage("auction.item_lore.click_to_buy", itemPlaceholders));
                }
            }

            inv.setItem(i, builder.build());

            final long listingId = listing.getId();
            final boolean published = listing.isPublished();
            holder.setAction(i, event -> {
                if (isOwner) {
                    SoundUtil.playClick(player);
                    new AuctionMyListingsGui(plugin, player).open();
                } else if (!published) {
                    SoundUtil.playError(player);
                    MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.not_published_yet", itemPlaceholders));
                } else {
                    SoundUtil.playClick(player);
                    new AuctionConfirmGui(plugin, player, listingId).open();
                }
            });
        }

        // Bottom row (slots 45-53)
        // Dark glass filler
        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s = 45; s < 54; s++) {
            inv.setItem(s, filler);
        }

        // Slot 45: Ender Chest (Purchased items storage)
        ItemStack claimChest = ItemBuilder.from(Material.ENDER_CHEST)
                .name(lm.getMessage("auction.button_claim_chest"))
                .lore(lm.getMessageList("auction.button_claim_chest_lore"))
                .build();
        inv.setItem(45, claimChest);
        holder.setAction(45, event -> {
            SoundUtil.playClick(player);
            new AuctionClaimsGui(plugin, player).open();
        });

        // Slot 48: Previous Page Arrow
        if (curPage > 1) {
            ItemStack prevArrow = ItemBuilder.from(Material.ARROW)
                    .name(lm.getMessage("auction.button_previous_page"))
                    .build();
            inv.setItem(48, prevArrow);
            holder.setAction(48, event -> {
                SoundUtil.playPageTurn(player);
                new AuctionMainGui(plugin, player, curPage - 1).open();
            });
        }

        // Slot 49: Refresh Nether Star
        ItemStack refreshStar = ItemBuilder.from(Material.NETHER_STAR)
                .name(lm.getMessage("auction.button_refresh"))
                .build();
        inv.setItem(49, refreshStar);
        holder.setAction(49, event -> {
            SoundUtil.playRefresh(player);
            new AuctionMainGui(plugin, player, curPage).open();
        });

        // Slot 50: Next Page Arrow
        if (curPage < maxPage) {
            ItemStack nextArrow = ItemBuilder.from(Material.ARROW)
                    .name(lm.getMessage("auction.button_next_page"))
                    .build();
            inv.setItem(50, nextArrow);
            holder.setAction(50, event -> {
                SoundUtil.playPageTurn(player);
                new AuctionMainGui(plugin, player, curPage + 1).open();
            });
        }

        // Slot 53: Minecart with Chest (My active listings)
        ItemStack myListings = ItemBuilder.from(Material.CHEST_MINECART)
                .name(lm.getMessage("auction.button_my_listings"))
                .lore(lm.getMessageList("auction.button_my_listings_lore"))
                .build();
        inv.setItem(53, myListings);
        holder.setAction(53, event -> {
            SoundUtil.playClick(player);
            new AuctionMyListingsGui(plugin, player).open();
        });

        player.openInventory(inv);
        SoundUtil.playOpen(player);
    }
}
