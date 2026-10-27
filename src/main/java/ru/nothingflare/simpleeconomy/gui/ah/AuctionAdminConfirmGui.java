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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class AuctionAdminConfirmGui {

    private final SimpleEconomyPlugin plugin;
    private final Player player;
    private final long listingId;
    private final int returnPage;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public AuctionAdminConfirmGui(SimpleEconomyPlugin plugin, Player player, long listingId, int returnPage) {
        this.plugin = plugin;
        this.player = player;
        this.listingId = listingId;
        this.returnPage = Math.max(1, returnPage);
    }

    public void open() {
        TaskScheduler.runAsync(plugin, () -> {
            AuctionListing listing = plugin.getDatabaseManager().getListing(listingId);
            if (listing == null || !listing.isActive()) {
                TaskScheduler.runEntity(plugin, player, () -> {
                    MessageUtil.sendMessage(player, plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("auction.item_already_sold"));
                    new AuctionAdminGui(plugin, player, returnPage).open();
                });
                return;
            }

            TaskScheduler.runEntity(plugin, player, () -> buildAndShow(listing));
        });
    }

    private void buildAndShow(AuctionListing listing) {
        LanguageManager lm = plugin.getLanguageManager();
        String title = lm.getMessage("auction.admin_confirm_title");
        GuiHolder holder = new GuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, MessageUtil.color(title));
        holder.setInventory(inv);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("price", MessageUtil.formatCurrency(listing.getPrice()));
        placeholders.put("seller", listing.getSellerName());
        placeholders.put("item", ItemNameUtil.getItemName(listing.getItem()));

        // Left side: Confirm Admin Delete (Red glass)
        ItemStack deleteGlass = ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                .name(lm.getMessage("auction.button_confirm_admin_delete"))
                .lore(lm.getMessageList("auction.confirm_admin_delete_lore", placeholders))
                .build();

        int[] deleteSlots = {0, 1, 2, 9, 10, 11, 18, 19, 20};
        for (int slot : deleteSlots) {
            inv.setItem(slot, deleteGlass);
            holder.setAction(slot, event -> processAdminDelete(listing));
        }

        // Center preview item (slot 13)
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

        // Right side: Cancel (Lime glass)
        ItemStack cancelGlass = ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                .name(lm.getMessage("auction.cancel_button"))
                .lore(lm.getMessageList("auction.cancel_lore"))
                .build();

        int[] cancelSlots = {6, 7, 8, 15, 16, 17, 24, 25, 26};
        for (int slot : cancelSlots) {
            inv.setItem(slot, cancelGlass);
            holder.setAction(slot, event -> {
                SoundUtil.playClick(player);
                new AuctionAdminGui(plugin, player, returnPage).open();
            });
        }

        player.openInventory(inv);
        SoundUtil.playOpen(player);
    }

    private void processAdminDelete(AuctionListing listing) {
        if (!processing.compareAndSet(false, true)) {
            return;
        }

        LanguageManager lm = plugin.getLanguageManager();

        TaskScheduler.runAsync(plugin, () -> {
            boolean success = plugin.getDatabaseManager().adminDeleteListing(listing.getId(), player.getUniqueId(), player.getName());

            TaskScheduler.runEntity(plugin, player, () -> {
                if (success) {
                    SoundUtil.playSuccess(player);
                    Map<String, String> ph = new HashMap<>();
                    ph.put("seller", listing.getSellerName());
                    ph.put("item", ItemNameUtil.getItemName(listing.getItem()));
                    ph.put("price", MessageUtil.formatCurrency(listing.getPrice()));

                    MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.admin_delete_success", ph));
                } else {
                    SoundUtil.playError(player);
                    MessageUtil.sendMessage(player, lm.getPrefix() + lm.getMessage("auction.item_already_sold"));
                }

                new AuctionAdminGui(plugin, player, returnPage).open();
            });
        });
    }
}
