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
import ru.nothingflare.simpleeconomy.config.LanguageManager;

import java.util.*;

public class ShopSection {

    private final String id;
    private final boolean enabled;
    private final int slot;
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final String shopFile;

    private final Map<Integer, List<ShopItem>> pages = new LinkedHashMap<>();

    public ShopSection(String id, boolean enabled, int slot, Material material, String name, List<String> lore, String shopFile) {
        this.id = id;
        this.enabled = enabled;
        this.slot = slot;
        this.material = material != null ? material : Material.CHEST;
        this.name = name != null ? name : id;
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
        this.shopFile = shopFile;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSlot() {
        return slot;
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName(LanguageManager lm) {
        if (lm != null) {
            String localized = lm.getMessage("sections." + id + ".name");
            if (localized != null && !localized.isEmpty() && !localized.equals("sections." + id + ".name")) {
                return localized;
            }
        }
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public List<String> getDisplayLore(LanguageManager lm) {
        if (lm != null) {
            List<String> localized = lm.getMessageList("sections." + id + ".lore");
            if (localized != null && !localized.isEmpty()) {
                return localized;
            }
        }
        return lore;
    }

    public String getShopFile() {
        return shopFile;
    }

    public int getMaxPage() {
        return Math.max(1, pages.size());
    }

    public List<ShopItem> getPageItems(int page) {
        return pages.getOrDefault(page, Collections.emptyList());
    }

    public void setPageItems(int page, List<ShopItem> items) {
        if (items != null) {
            pages.put(page, new ArrayList<>(items));
        } else {
            pages.remove(page);
        }
    }

    public void clearPages() {
        pages.clear();
    }

    public int getTotalItemCount() {
        int count = 0;
        for (List<ShopItem> list : pages.values()) {
            count += list.size();
        }
        return count;
    }

    public List<ShopItem> getItems() {
        List<ShopItem> all = new ArrayList<>();
        for (List<ShopItem> list : pages.values()) {
            all.addAll(list);
        }
        return all;
    }

    public void setItems(List<ShopItem> newItems) {
        clearPages();
        if (newItems != null && !newItems.isEmpty()) {
            int pageNum = 1;
            for (int i = 0; i < newItems.size(); i += 45) {
                int end = Math.min(i + 45, newItems.size());
                setPageItems(pageNum++, newItems.subList(i, end));
            }
        }
    }
}
