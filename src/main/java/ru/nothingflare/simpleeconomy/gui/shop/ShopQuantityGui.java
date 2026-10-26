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
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;
import ru.nothingflare.simpleeconomy.config.LanguageManager;
import ru.nothingflare.simpleeconomy.database.models.ShopItem;
import ru.nothingflare.simpleeconomy.database.models.ShopSection;
import ru.nothingflare.simpleeconomy.gui.GuiHolder;
import ru.nothingflare.simpleeconomy.util.ItemBuilder;
import ru.nothingflare.simpleeconomy.util.ItemNameUtil;
import ru.nothingflare.simpleeconomy.util.MessageUtil;
import ru.nothingflare.simpleeconomy.util.SoundUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShopQuantityGui {

    private final SimpleEconomyPlugin plugin;
    private final Player player;
    private final ShopSection section;
    private final ShopItem shopItem;
    private final int returnPage;
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private int amount = 1;

    public ShopQuantityGui(SimpleEconomyPlugin plugin, Player player, ShopSection section, ShopItem shopItem) {
        this(plugin, player, section, shopItem, 1);
    }

    public ShopQuantityGui(SimpleEconomyPlugin plugin, Player player, ShopSection section, ShopItem shopItem, int returnPage) {
        this.plugin = plugin;
        this.player = player;
        this.section = section;
        this.shopItem = shopItem;
        this.returnPage = Math.max(1, returnPage);
    }

    public void open() {
        LanguageManager lm = plugin.getLanguageManager();
        String title = lm.getMessage("shop.quantity_title");
        GuiHolder holder = new GuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        // Fill background with dark glass panes
        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        renderControls(inv, holder);
        player.openInventory(inv);
        SoundUtil.playOpen(player);
    }

    private void renderControls(Inventory inv, GuiHolder holder) {
        LanguageManager lm = plugin.getLanguageManager();
        double unitPrice = plugin.getDynamicMarketManager().getShopBuyPrice(section, shopItem);
        double totalPrice = amount * unitPrice;
        String itemName = (shopItem.getDisplayName() != null && !shopItem.getDisplayName().isEmpty())
                ? shopItem.getDisplayName()
                : ItemNameUtil.formatMaterial(shopItem.getMaterial());

        Map<String, String> ph = new HashMap<>();
        ph.put("amount", String.valueOf(amount));
        ph.put("price", MessageUtil.formatCurrency(totalPrice));
        ph.put("item", itemName);

        // Center preview item (slot 13)
        ItemBuilder centerBuilder = ItemBuilder.from(shopItem.getMaterial())
                .amount(Math.min(amount, 64))
                .name(itemName);

        if (shopItem.getLore() != null && !shopItem.getLore().isEmpty()) {
            for (String l : shopItem.getLore()) {
                if (!l.contains("Недоступно") && !l.contains("недоступно")
                        && !l.contains("Unavailable") && !l.contains("unavailable")
                        && !l.contains("✖")) {
                    centerBuilder.addLore(l);
                }
            }
        }

        centerBuilder.addLore("");
        centerBuilder.addLore(lm.getMessage("shop.quantity_selector", ph));
        centerBuilder.addLore(lm.getMessage("shop.total_price", ph));

        String trendLore = plugin.getDynamicMarketManager().getShopTrendLore(section, shopItem);
        if (trendLore != null && !trendLore.isEmpty()) {
            centerBuilder.addLore(trendLore);
        }

        inv.setItem(13, centerBuilder.build());

        // Adjustment buttons
        // -10 (slot 10)
        inv.setItem(10, ItemBuilder.from(Material.RED_DYE).name("&c-10").build());
        holder.setAction(10, event -> {
            amount = Math.max(1, amount - 10);
            SoundUtil.playAdjust(player, false);
            renderControls(inv, holder);
        });

        // -1 (slot 11)
        inv.setItem(11, ItemBuilder.from(Material.ORANGE_DYE).name("&6-1").build());
        holder.setAction(11, event -> {
            amount = Math.max(1, amount - 1);
            SoundUtil.playAdjust(player, false);
            renderControls(inv, holder);
        });

        // Set to 1 (slot 4)
        inv.setItem(4, ItemBuilder.from(Material.PAPER).name(lm.getMessage("shop.set_amount_1")).build());
        holder.setAction(4, event -> {
            amount = 1;
            SoundUtil.playQuantityPreset(player);
            renderControls(inv, holder);
        });

        // Set to 64 (slot 22)
        inv.setItem(22, ItemBuilder.from(Material.PAPER).name(lm.getMessage("shop.set_amount_64")).build());
        holder.setAction(22, event -> {
            amount = 64;
            SoundUtil.playQuantityPreset(player);
            renderControls(inv, holder);
        });

        // +1 (slot 15)
        inv.setItem(15, ItemBuilder.from(Material.LIME_DYE).name("&a+1").build());
        holder.setAction(15, event -> {
            amount = Math.min(640, amount + 1);
            SoundUtil.playAdjust(player, true);
            renderControls(inv, holder);
        });

        // +10 (slot 16)
        inv.setItem(16, ItemBuilder.from(Material.GREEN_DYE).name("&2+10").build());
        holder.setAction(16, event -> {
            amount = Math.min(640, amount + 10);
            SoundUtil.playAdjust(player, true);
            renderControls(inv, holder);
        });

        // Bottom row:
        // Green glass panes (slots 18, 19, 20) -> Confirm buy
        ItemStack greenGlass = ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                .name(lm.getMessage("shop.button_confirm_buy"))
                .lore(lm.getMessage("shop.total_price", ph))
                .build();
        int[] greenSlots = {18, 19, 20};
        for (int s : greenSlots) {
            inv.setItem(s, greenGlass);
            holder.setAction(s, event -> executePurchase());
        }

        // Red glass panes (slots 24, 25, 26) -> Cancel / Back
        ItemStack redGlass = ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                .name(lm.getMessage("shop.button_cancel_buy"))
                .build();
        int[] redSlots = {24, 25, 26};
        for (int s : redSlots) {
            inv.setItem(s, redGlass);
            holder.setAction(s, event -> {
                SoundUtil.playClick(player);
                new ShopItemsGui(plugin, player, section, returnPage).open();
            });
        }
    }

    private void executePurchase() {
        if (!processing.compareAndSet(false, true)) {
            return;
        }

        LanguageManager lm = plugin.getLanguageManager();
        double unitPrice = plugin.getDynamicMarketManager().getShopBuyPrice(section, shopItem);
        double totalPrice = amount * unitPrice;

        if (!plugin.getEconomyHook().has(player, totalPrice)) {
            processing.set(false);
            SoundUtil.playError(player);
            Map<String, String> ph = new HashMap<>();
            ph.put("price", MessageUtil.formatCurrency(totalPrice));
            MessageUtil.sendMessage(player, lm.getMessage("shop.not_enough_money", ph));
            return;
        }

        // Deduct money
        plugin.getEconomyHook().withdraw(player, totalPrice);

        // Record market purchase -> price increases due to demand!
        plugin.getDynamicMarketManager().recordShopPurchase(section, shopItem, amount);

        // Give items with metadata preserved
        int remaining = amount;
        int maxStack = shopItem.getMaterial().getMaxStackSize();
        while (remaining > 0) {
            int giveAmount = Math.min(remaining, maxStack);
            ItemBuilder ib = ItemBuilder.from(shopItem.getMaterial()).amount(giveAmount);
            if (shopItem.getDisplayName() != null && !shopItem.getDisplayName().isEmpty()) {
                ib.name(shopItem.getDisplayName());
            }
            if (shopItem.getLore() != null && !shopItem.getLore().isEmpty()) {
                ib.lore(shopItem.getLore());
            }
            ItemStack itemToGive = ib.build();

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemToGive);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
            remaining -= giveAmount;
        }

        SoundUtil.playSuccess(player);
        String boughtName = (shopItem.getDisplayName() != null && !shopItem.getDisplayName().isEmpty())
                ? shopItem.getDisplayName()
                : ItemNameUtil.formatMaterial(shopItem.getMaterial());

        Map<String, String> ph = new HashMap<>();
        ph.put("amount", String.valueOf(amount));
        ph.put("item", boughtName);
        ph.put("price", MessageUtil.formatCurrency(totalPrice));
        MessageUtil.sendMessage(player, lm.getMessage("shop.buy_success", ph));

        if (plugin.getConfigManager().isShopCloseOnBuy()) {
            player.closeInventory();
        } else {
            new ShopItemsGui(plugin, player, section, returnPage).open();
        }
    }
}
