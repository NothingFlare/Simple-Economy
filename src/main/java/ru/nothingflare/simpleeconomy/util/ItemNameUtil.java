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
import org.bukkit.inventory.ItemStack;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemNameUtil {

    private static final Map<Material, String> MATERIAL_NAMES = new ConcurrentHashMap<>();

    private ItemNameUtil() {}

    public static void load(SimpleEconomyPlugin plugin, String lang) {
        MATERIAL_NAMES.clear();
        String current = (lang != null && !lang.isEmpty()) ? lang.toLowerCase() : "ru";

        // Try to load from languages/items/<lang>.properties
        InputStream in = plugin.getResource("languages/items/" + current + ".properties");
        if (in == null) {
            in = plugin.getResource("languages/items/en.properties");
        }

        if (in != null) {
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Properties props = new Properties();
                props.load(reader);
                for (String key : props.stringPropertyNames()) {
                    try {
                        Material mat = Material.matchMaterial(key);
                        if (mat != null) {
                            MATERIAL_NAMES.put(mat, props.getProperty(key));
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load item names for " + current + ": " + e.getMessage());
            }
        }
    }

    public static String getItemName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return formatMaterial(item.getType());
    }

    public static String formatMaterial(Material material) {
        if (material == null) {
            return "";
        }
        String name = MATERIAL_NAMES.get(material);
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return formatPrettyName(material.name());
    }

    public static String formatPrettyName(String enumName) {
        if (enumName == null || enumName.isEmpty()) {
            return "";
        }
        String[] parts = enumName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                sb.append(parts[i].substring(1));
            }
            if (i < parts.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }
}
