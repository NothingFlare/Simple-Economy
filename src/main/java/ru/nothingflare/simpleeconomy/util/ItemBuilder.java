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

package ru.nothingflare.simpleeconomy.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(new ItemStack(material != null ? material : Material.STONE));
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public static ItemBuilder from(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder from(ItemStack item) {
        return new ItemBuilder(item);
    }

    public ItemBuilder name(String name) {
        if (meta != null && name != null) {
            meta.setDisplayName(MessageUtil.color(name));
        }
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        if (meta != null && lore != null) {
            meta.setLore(MessageUtil.color(lore));
        }
        return this;
    }

    public ItemBuilder lore(String... lore) {
        return lore(Arrays.asList(lore));
    }

    public ItemBuilder addLore(String line) {
        if (meta != null && line != null) {
            List<String> currentLore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            currentLore.add(MessageUtil.color(line));
            meta.setLore(currentLore);
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(amount, 64)));
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null && flags != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }
}
