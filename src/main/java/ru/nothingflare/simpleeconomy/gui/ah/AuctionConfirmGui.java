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
import org.bukkit.OfflinePlayer;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class AuctionConfirmGui {

    private final SimpleEconomyPlugin plugin;
    private final Player player;
    private final long listingId;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public AuctionConfirmGui(SimpleEconomyPlugin plugin, Player player, long listingId) {
        this.plugin = plugin;
        this.player = player;
        this.listingId = listingId;
    }

    public void open() {
        TaskScheduler.runAsync(plugin, () -> {
            AuctionListing listing = plugin.getDatabaseManager().getListing(listingId);
            if (listing == null || !listing.isActive()) {
                TaskScheduler.runEntity(plugin, player, () -> {
                    MessageUtil.sendMessage(player, plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("auction.item_already_sold"));
                    new AuctionMainGui(plugin, player, 1).open();
                });
                return;
            }

            TaskScheduler.runEntity(plugin, player, () -> buildAndShow(listing));
        });
    }

    private void buildAndShow(AuctionListing listing) {
        LanguageManager lm = plugin.getLanguageManager();
        String title = lm.getMessage("auction.confirm_title");
        GuiHolder holder = new GuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("price", MessageUtil.formatCurrency(listing.getPrice()));
        placeholders.put("seller", listing.getSellerName());
        placeholders.put("item", ItemNameUtil.getItemName(listing.getItem()));

        // Left side: Confirm Purchase (Green glass)
        ItemStack greenGlass = ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                .name(lm.getMessage("auction.confirm_buy_button"))
                .lore(lm.getMessageList("auction.confirm_buy_lore", placeholders))
                .build();

        int[] greenSlots = {0, 1, 2, 9, 10, 11, 18, 19, 20};
        for (int slot : greenSlots) {
            inv.setItem(slot, greenGlass);
            holder.setAction(slot, event -> processPurchase(listing));
        }

        // Center item (slot 13)
        if (listing.getItem() != null) {
            ItemStack preview = ItemBuilder.from(listing.getItem())
                    .addLore("")
                    .addLore(lm.getMessage("auction.item_lore.price", placeholders))
                    .addLore(lm.getMessage("auction.item_lore.seller", placeholders))
                    .build();
            inv.setItem(13, preview);
        }

        // Dividers (Gray glass)
        ItemStack grayGlass = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        int[] graySlots = {3, 4, 5, 12, 14, 21, 22, 23};
        for (int slot : graySlots) {
            inv.setItem(slot, grayGlass);
        }

        // Right side: Cancel Purchase (Red glass)
        ItemStack redGlass = ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                .name(lm.getMessage("auction.cancel_button"))
                .lore(lm.getMessageList("auction.cancel_lore"))
                .build();

        int[] redSlots = {6, 7, 8, 15, 16, 17, 24, 25, 26};
        for (int slot : redSlots) {
            inv.setItem(slot, redGlass);
            holder.setAction(slot, event -> {
                SoundUtil.playClick(player);
                new AuctionMainGui(plugin, player, 1).open();
            });
        }

        player.openInventory(inv);
        SoundUtil.playOpen(player);
    }

    private void processPurchase(AuctionListing listing) {
        if (!processing.compareAndSet(false, true)) {
            return;
        }

        LanguageManager lm = plugin.getLanguageManager();

        if (!plugin.getEconomyHook().has(player, listing.getPrice())) {
            processing.set(false);
            SoundUtil.playError(player);
            Map<String, String> ph = new HashMap<>();
            ph.put("price", MessageUtil.formatCurrency(listing.getPrice()));
            MessageUtil.sendMessage(player, lm.getMessage("auction.not_enough_money", ph));
            return;
        }

        TaskScheduler.runAsync(plugin, () -> {
            boolean success = plugin.getDatabaseManager().purchaseListing(listing.getId());
            if (!success) {
                TaskScheduler.runEntity(plugin, player, () -> {
                    processing.set(false);
                    SoundUtil.playError(player);
                    MessageUtil.sendMessage(player, lm.getMessage("auction.item_already_sold"));
                    new AuctionMainGui(plugin, player, 1).open();
                });
                return;
            }

            // Deduct from buyer
            plugin.getEconomyHook().withdraw(player, listing.getPrice());

            // Deliver item to buyer's claim storage
            plugin.getDatabaseManager().addClaim(player.getUniqueId(), listing.getItem(), "BOUGHT");

            // Give money to seller
            OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.getSellerUuid());
            plugin.getEconomyHook().deposit(seller, listing.getPrice());

            TaskScheduler.runEntity(plugin, player, () -> {
                SoundUtil.playSuccess(player);

                String itemName = ItemNameUtil.getItemName(listing.getItem());

                Map<String, String> ph = new HashMap<>();
                ph.put("item", itemName);
                ph.put("price", MessageUtil.formatCurrency(listing.getPrice()));
                ph.put("buyer", player.getName());

                MessageUtil.sendMessage(player, lm.getMessage("auction.buy_success", ph));

                // Notify seller if online
                Player onlineSeller = Bukkit.getPlayer(listing.getSellerUuid());
                if (onlineSeller != null && onlineSeller.isOnline()) {
                    TaskScheduler.runEntity(plugin, onlineSeller, () -> {
                        SoundUtil.playSuccess(onlineSeller);
                        MessageUtil.sendMessage(onlineSeller, lm.getMessage("auction.item_sold_notify", ph));
                    });
                }

                // Open claim storage so player can immediately see/take the item!
                new AuctionClaimsGui(plugin, player).open();
            });
        });
    }
}
