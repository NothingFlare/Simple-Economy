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
import ru.nothingflare.simpleeconomy.config.LanguageManager;
import ru.nothingflare.simpleeconomy.database.models.ShopSection;
import ru.nothingflare.simpleeconomy.gui.GuiHolder;
import ru.nothingflare.simpleeconomy.util.ItemBuilder;
import ru.nothingflare.simpleeconomy.util.SoundUtil;

import java.util.*;

public class ShopSectionsGui {

    private final SimpleEconomyPlugin plugin;
    private final Player player;

    public ShopSectionsGui(SimpleEconomyPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        LanguageManager lm = plugin.getLanguageManager();
        String title = lm.getMessage("shop.main_title");
        GuiHolder holder = new GuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        // Fill background with dark glass panes
        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        Map<String, ShopSection> sections = plugin.getConfigManager().getSections();
        Set<Integer> occupiedSlots = new HashSet<>();
        occupiedSlots.add(48); // Reserved for exit arrow

        List<ShopSection> unplaced = new ArrayList<>();

        // Place sections with valid explicit slots first
        for (ShopSection section : sections.values()) {
            if (!section.isEnabled()) continue;
            int slot = section.getSlot();
            if (slot >= 0 && slot < 54 && slot != 48 && !occupiedSlots.contains(slot)) {
                occupiedSlots.add(slot);
                inv.setItem(slot, buildSectionIcon(section));
                holder.setAction(slot, event -> {
                    SoundUtil.playClick(player);
                    new ShopItemsGui(plugin, player, section, 1).open();
                });
            } else if (section.isEnabled()) {
                unplaced.add(section);
            }
        }

        // Place any remaining enabled sections into free inner slots
        int checkSlot = 10;
        for (ShopSection section : unplaced) {
            while (checkSlot < 45 && (occupiedSlots.contains(checkSlot) || checkSlot % 9 == 0 || checkSlot % 9 == 8)) {
                checkSlot++;
            }
            if (checkSlot < 45) {
                occupiedSlots.add(checkSlot);
                inv.setItem(checkSlot, buildSectionIcon(section));
                holder.setAction(checkSlot, event -> {
                    SoundUtil.playClick(player);
                    new ShopItemsGui(plugin, player, section, 1).open();
                });
                checkSlot++;
            }
        }

        // Bottom row: Exit Arrow (slot 48, book removed)
        ItemStack exitArrow = ItemBuilder.from(Material.ARROW)
                .name(lm.getMessage("shop.exit_button"))
                .lore(lm.getMessageList("shop.exit_lore"))
                .build();
        inv.setItem(48, exitArrow);
        holder.setAction(48, event -> {
            SoundUtil.playClick(player);
            player.closeInventory();
        });

        player.openInventory(inv);
        SoundUtil.playOpen(player);
    }

    private ItemStack buildSectionIcon(ShopSection section) {
        LanguageManager lm = plugin.getLanguageManager();
        ItemBuilder builder = ItemBuilder.from(section.getMaterial())
                .name(section.getDisplayName(lm));

        List<String> sectionLore = section.getDisplayLore(lm);
        if (sectionLore != null && !sectionLore.isEmpty()) {
            builder.lore(sectionLore);
        }

        builder.addLore("");
        Map<String, String> ph = Collections.singletonMap("total", String.valueOf(section.getTotalItemCount()));
        List<String> extraLore = plugin.getLanguageManager().getMessageList("shop.section_icon_lore", ph);
        if (extraLore.isEmpty()) {
            builder.addLore("&7Всего товаров: &e" + section.getTotalItemCount());
            builder.addLore("&eНажмите для просмотра товаров");
        } else {
            for (String line : extraLore) {
                builder.addLore(line);
            }
        }
        return builder.build();
    }
}
