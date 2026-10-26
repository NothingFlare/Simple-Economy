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

package ru.nothingflare.simpleeconomy.gui.shop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;
import ru.nothingflare.simpleeconomy.config.ConfigManager;
import ru.nothingflare.simpleeconomy.config.LanguageManager;
import ru.nothingflare.simpleeconomy.database.models.ShopItem;
import ru.nothingflare.simpleeconomy.database.models.ShopSection;
import ru.nothingflare.simpleeconomy.gui.GuiHolder;
import ru.nothingflare.simpleeconomy.market.DynamicMarketManager;
import ru.nothingflare.simpleeconomy.util.ItemBuilder;
import ru.nothingflare.simpleeconomy.util.ItemNameUtil;
import ru.nothingflare.simpleeconomy.util.MessageUtil;
import ru.nothingflare.simpleeconomy.util.SoundUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopItemsGui {

    private final SimpleEconomyPlugin plugin;
    private final Player player;
    private final ShopSection section;
    private int page;

    public ShopItemsGui(SimpleEconomyPlugin plugin, Player player, ShopSection section) {
        this(plugin, player, section, 1);
    }

    public ShopItemsGui(SimpleEconomyPlugin plugin, Player player, ShopSection section, int page) {
        this.plugin = plugin;
        this.player = player;
        this.section = section;
        this.page = Math.max(1, page);
    }

    public void open() {
        LanguageManager lm = plugin.getLanguageManager();
        String sectionDisplayName = section.getDisplayName(lm);
        int maxPage = section.getMaxPage();
        if (page > maxPage) {
            page = maxPage;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("section", sectionDisplayName);
        ph.put("page", String.valueOf(page));
        ph.put("max_page", String.valueOf(maxPage));

        String title = sectionDisplayName + " [" + page + "/" + maxPage + "]";
        GuiHolder holder = new GuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color(title));
        holder.setInventory(inv);

        // Fill background with dark glass panes
        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Render page items in slots 0 - 44
        List<ShopItem> pageItems = section.getPageItems(page);
        ConfigManager cfg = plugin.getConfigManager();
        DynamicMarketManager dmm = plugin.getDynamicMarketManager();

        int totalSectionItems = section.getTotalItemCount();
        int allowedCount = cfg.isShopItemLockEnabled()
                ? (int) Math.ceil(totalSectionItems * (cfg.getShopItemLockAllowedPercentage() / 100.0))
                : totalSectionItems;

        boolean canBypassLock = player.hasPermission("simpleeconomy.admin")
                || player.hasPermission(cfg.getShopItemLockBypassPermission());

        for (int i = 0; i < pageItems.size() && i < 45; i++) {
            ShopItem shopItem = pageItems.get(i);
            int slot = shopItem.getSlot();
            if (slot < 0 || slot >= 45) {
                slot = i;
            }

            int globalIndex = (page - 1) * 45 + i;
            boolean isLocked = cfg.isShopItemLockEnabled() && !canBypassLock && (globalIndex >= allowedCount);

            double buyPrice = dmm.getShopBuyPrice(section, shopItem);
            double sellPrice = shopItem.getSellPrice();

            Map<String, String> itemPh = new HashMap<>();
            itemPh.put("buy", MessageUtil.formatCurrency(buyPrice));
            itemPh.put("sell", MessageUtil.formatCurrency(sellPrice));

            ItemBuilder builder = ItemBuilder.from(shopItem.getMaterial());

            String rawName = (shopItem.getDisplayName() != null && !shopItem.getDisplayName().isEmpty())
                    ? shopItem.getDisplayName()
                    : ItemNameUtil.formatMaterial(shopItem.getMaterial());

            if (isLocked) {
                builder.name(lm.getMessage("shop.item_locked_prefix") + " &7" + rawName);
                if (shopItem.getLore() != null && !shopItem.getLore().isEmpty()) {
                    builder.lore(shopItem.getLore());
                }
                builder.addLore("");
                builder.addLore(lm.getMessage("shop.item_locked_lore"));
                inv.setItem(slot, builder.build());
                holder.setAction(slot, event -> {
                    SoundUtil.playError(player);
                    MessageUtil.sendMessage(player, lm.getMessage("shop.item_locked_msg"));
                });
                continue;
            }

            if (shopItem.getDisplayName() != null && !shopItem.getDisplayName().isEmpty()) {
                builder.name(shopItem.getDisplayName());
            } else {
                builder.name("&f" + rawName);
            }

            if (shopItem.getLore() != null && !shopItem.getLore().isEmpty()) {
                for (String l : shopItem.getLore()) {
                    if (!l.contains("Недоступно") && !l.contains("недоступно")
                            && !l.contains("Unavailable") && !l.contains("unavailable")
                            && !l.contains("✖")) {
                        builder.addLore(l);
                    }
                }
            }

            builder.addLore("");
            if (shopItem.isBuyable()) {
                builder.addLore(lm.getMessage("shop.item_lore.buy_price", itemPh));
                String trendLore = dmm.getShopTrendLore(section, shopItem);
                if (trendLore != null && !trendLore.isEmpty()) {
                    builder.addLore(trendLore);
                }
            } else {
                builder.addLore(lm.getMessage("shop.not_for_sale"));
            }

            if (shopItem.isSellable()) {
                builder.addLore(lm.getMessage("shop.item_lore.sell_price", itemPh));
            }
            builder.addLore("");

            if (shopItem.isBuyable()) {
                builder.addLore(lm.getMessage("shop.item_lore.click_to_select"));
            }

            inv.setItem(slot, builder.build());

            if (shopItem.isBuyable()) {
                final int returnPage = page;
                holder.setAction(slot, event -> {
                    SoundUtil.playClick(player);
                    new ShopQuantityGui(plugin, player, section, shopItem, returnPage).open();
                });
            } else {
                holder.setAction(slot, event -> event.setCancelled(true));
            }
        }

        Map<String, String> navPh = new HashMap<>();
        navPh.put("page", String.valueOf(page));
        navPh.put("max_page", String.valueOf(maxPage));
        navPh.put("prev_page", String.valueOf(Math.max(1, page - 1)));
        navPh.put("next_page", String.valueOf(Math.min(maxPage, page + 1)));
        navPh.put("total", String.valueOf(section.getTotalItemCount()));

        // Bottom row:
        // Slot 48: Previous Page Arrow / Back to Categories
        if (page == 1) {
            ItemStack backArrow = ItemBuilder.from(Material.ARROW)
                    .name(lm.getMessage("shop.button_back_to_sections"))
                    .lore(lm.getMessageList("shop.button_back_to_sections_lore"))
                    .build();
            inv.setItem(48, backArrow);
            holder.setAction(48, event -> {
                SoundUtil.playClick(player);
                new ShopSectionsGui(plugin, player).open();
            });
        } else {
            ItemStack prevArrow = ItemBuilder.from(Material.ARROW)
                    .name(lm.getMessage("shop.button_prev_page", navPh))
                    .build();
            inv.setItem(48, prevArrow);
            holder.setAction(48, event -> {
                SoundUtil.playPageTurn(player);
                new ShopItemsGui(plugin, player, section, page - 1).open();
            });
        }

        // Slot 49: Section info
        ItemStack centerInfo = ItemBuilder.from(section.getMaterial())
                .name(sectionDisplayName)
                .lore(lm.getMessageList("shop.section_info_lore", navPh))
                .build();
        inv.setItem(49, centerInfo);
        holder.setAction(49, event -> event.setCancelled(true));

        // Slot 50: Next Page Arrow
        if (page < maxPage) {
            ItemStack nextArrow = ItemBuilder.from(Material.ARROW)
                    .name(lm.getMessage("shop.button_next_page", navPh))
                    .build();
            inv.setItem(50, nextArrow);
            holder.setAction(50, event -> {
                SoundUtil.playPageTurn(player);
                new ShopItemsGui(plugin, player, section, page + 1).open();
            });
        }

        player.openInventory(inv);
        SoundUtil.playOpen(player);
    }
}
