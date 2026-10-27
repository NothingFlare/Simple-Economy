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

package ru.nothingflare.simpleeconomy.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;
import ru.nothingflare.simpleeconomy.util.ItemNameUtil;
import ru.nothingflare.simpleeconomy.util.MessageUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LanguageManager {

    private final SimpleEconomyPlugin plugin;
    private FileConfiguration langConfig;
    private FileConfiguration fallbackConfig;
    private String currentLang;

    public LanguageManager(SimpleEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.currentLang = plugin.getConfig().getString("language", "ru").toLowerCase();

        // Ensure default language resources exist
        String[] supportedLangs = {"en", "ru", "zh", "de", "fr", "es", "pt", "ja", "uk", "it", "pl", "tr", "ko"};
        File langFolder = new File(plugin.getDataFolder(), "languages");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        for (String lang : supportedLangs) {
            File langFile = new File(langFolder, lang + ".yml");
            if (!langFile.exists()) {
                plugin.saveResource("languages/" + lang + ".yml", false);
            }
        }

        File currentFile = new File(langFolder, currentLang + ".yml");
        if (!currentFile.exists()) {
            plugin.getLogger().warning("Language file '" + currentLang + ".yml' not found. Falling back to 'ru.yml'.");
            currentFile = new File(langFolder, "ru.yml");
            currentLang = "ru";
        }

        this.langConfig = YamlConfiguration.loadConfiguration(currentFile);

        // Load fallback from jar
        InputStream fallbackStream = plugin.getResource("languages/en.yml");
        if (fallbackStream != null) {
            this.fallbackConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(fallbackStream, StandardCharsets.UTF_8));
        }

        // Load translated item names
        ItemNameUtil.load(plugin, currentLang);

        plugin.getLogger().info("Loaded language: " + currentLang);
    }

    public String getPrefix() {
        return "";
    }

    public String get(String path, Object... placeholders) {
        String msg = null;
        if (langConfig != null) {
            msg = langConfig.getString(path);
        }
        if (msg == null && fallbackConfig != null) {
            msg = fallbackConfig.getString(path);
        }
        if (msg == null) {
            return path;
        }

        if (placeholders != null && placeholders.length > 0 && placeholders.length % 2 == 0) {
            for (int i = 0; i < placeholders.length; i += 2) {
                String key = String.valueOf(placeholders[i]);
                String val = String.valueOf(placeholders[i + 1]);
                msg = msg.replace("%" + key + "%", val).replace("{" + key + "}", val);
            }
        }
        return MessageUtil.color(msg);
    }

    public String getMessage(String path) {
        return getMessage(path, null);
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String msg = langConfig.getString(path);
        if (msg == null && fallbackConfig != null) {
            msg = fallbackConfig.getString(path);
        }
        if (msg == null) {
            return path;
        }
        if (placeholders != null && !placeholders.isEmpty()) {
            msg = MessageUtil.replace(msg, placeholders);
        }
        return MessageUtil.color(msg);
    }

    public List<String> getMessageList(String path) {
        return getMessageList(path, null);
    }

    public List<String> getMessageList(String path, Map<String, String> placeholders) {
        List<String> list = langConfig.getStringList(path);
        if ((list == null || list.isEmpty()) && fallbackConfig != null) {
            list = fallbackConfig.getStringList(path);
        }
        if (list == null) {
            return new ArrayList<>();
        }
        if (placeholders != null && !placeholders.isEmpty()) {
            list = MessageUtil.replace(list, placeholders);
        }
        return MessageUtil.color(list);
    }

    public String getCurrentLang() {
        return currentLang;
    }
}
