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

package ru.nothingflare.simpleeconomy.database.models;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class ShopItem {

    private final String id;
    private final Material material;
    private final double buyPrice;
    private final double sellPrice;
    private final int slot;
    private final String displayName;
    private final List<String> lore;

    public ShopItem(String id, Material material, double buyPrice, double sellPrice, int slot) {
        this(id, material, buyPrice, sellPrice, slot, null, null);
    }

    public ShopItem(String id, Material material, double buyPrice, double sellPrice, int slot, String displayName, List<String> lore) {
        this.id = id;
        this.material = material != null ? material : Material.STONE;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.slot = slot;
        this.displayName = displayName;
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public int getSlot() {
        return slot;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean isBuyable() {
        return buyPrice > 0;
    }

    public boolean isSellable() {
        return sellPrice > 0;
    }
}
