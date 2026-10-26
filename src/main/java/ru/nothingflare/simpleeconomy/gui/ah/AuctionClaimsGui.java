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
import ru.nothingflare.simpleeconomy.database.models.AuctionClaim;
import ru.nothingflare.simpleeconomy.gui.GuiHolder;
import ru.nothingflare.simpleeconomy.scheduler.TaskScheduler;
import ru.nothingflare.simpleeconomy.util.ItemBuilder;
import ru.nothingflare.simpleeconomy.util.ItemNameUtil;
import ru.nothingflare.simpleeconomy.util.MessageUtil;
import ru.nothingflare.simpleeconomy.util.SoundUtil;

import java.util.List;

public class AuctionClaimsGui {

    private final SimpleEconomyPlugin plugin;
    private final Player player;

    public AuctionClaimsGui(SimpleEconomyPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        TaskScheduler.runAsync(plugin, () -> {
            List<AuctionClaim> claims = plugin.getDatabaseManager().getPlayerClaims(player.getUniqueId());
            TaskScheduler.runEntity(plugin, player, () -> buildAndShow(claims));
        });
    }

    private void buildAndShow(List<AuctionClaim> claims) {
        LanguageManager lm = plugin.getLanguageManager();
        String title = lm.getMessage("auction.claims_title");
        GuiHolder holder = new GuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        // Display claims in slots 0 - 44
        for (int i = 0; i < claims.size() && i < 45; i++) {
            AuctionClaim claim = claims.get(i);
            ItemStack item = claim.getItem();
            if (item == null) continue;

            ItemBuilder builder = ItemBuilder.from(item);
            if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
                builder.name("&f" + ItemNameUtil.formatMaterial(item.getType()));
            }
            builder.addLore("");
            builder.addLore(lm.getMessage("auction.claim_lore"));

            inv.setItem(i, builder.build());

            final long claimId = claim.getId();
            final ItemStack itemToGive = item.clone();

            holder.setAction(i, event -> {
                if (player.getInventory().firstEmpty() == -1) {
                    SoundUtil.playError(player);
                    MessageUtil.sendMessage(player, lm.getMessage("auction.inventory_full"));
                    return;
                }

                TaskScheduler.runAsync(plugin, () -> {
                    boolean removed = plugin.getDatabaseManager().removeClaim(claimId);
                    if (removed) {
                        TaskScheduler.runEntity(plugin, player, () -> {
                            player.getInventory().addItem(itemToGive);
                            SoundUtil.playClaim(player);
                            MessageUtil.sendMessage(player, lm.getMessage("auction.item_claimed"));
                            open();
                        });
                    }
                });
            });
        }

        // Bottom row filler (slots 45-53)
        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s = 45; s < 54; s++) {
            inv.setItem(s, filler);
        }

        // Slot 49: Back Arrow
        ItemStack backArrow = ItemBuilder.from(Material.ARROW)
                .name(lm.getMessage("shop.back_button"))
                .lore(lm.getMessageList("shop.back_lore"))
                .build();
        inv.setItem(49, backArrow);
        holder.setAction(49, event -> {
            SoundUtil.playClick(player);
            new AuctionMainGui(plugin, player, 1).open();
        });

        player.openInventory(inv);
        SoundUtil.playOpen(player);
    }
}
