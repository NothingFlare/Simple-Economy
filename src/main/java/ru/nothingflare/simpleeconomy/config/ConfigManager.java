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

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;
import ru.nothingflare.simpleeconomy.database.models.ShopItem;
import ru.nothingflare.simpleeconomy.database.models.ShopSection;
import ru.nothingflare.simpleeconomy.util.MessageUtil;
import ru.nothingflare.simpleeconomy.util.SoundUtil;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigManager {

    private final SimpleEconomyPlugin plugin;

    private boolean checkUpdates = true;

    private boolean shopEnabled = true;
    private boolean auctionEnabled = true;
    private boolean buyerEnabled = true;

    private int maxAuctionSlots = 5;
    private int listingCooldownSeconds = 0;
    private int publishDelaySeconds = 5;
    private int expireHours = 48;
    private double minPrice = 1.0;
    private double maxPrice = 100000000.0;
    private boolean auctionAdminWhitelistEnabled = false;
    private final List<String> auctionAdminWhitelist = new ArrayList<>();

    private boolean buyerSoundEnabled = true;
    private boolean buyerDropIfFull = true;
    private boolean shopCloseOnBuy = false;

    private boolean shopItemLockEnabled = false;
    private double shopItemLockAllowedPercentage = 100.0;
    private String shopItemLockBypassPermission = "simpleeconomy.shop.bypass";

    private boolean dynamicMarketEnabled = true;
    private double dynamicMarketChangePercent = 0.1;
    private double dynamicMarketMinMultiplier = 0.5;
    private double dynamicMarketMaxMultiplier = 3.0;
    private int dynamicMarketDecayInterval = 300;
    private double dynamicMarketDecayPercent = 1.5;
    private boolean dynamicMarketShowTrends = true;

    private final Map<String, ShopSection> sections = new LinkedHashMap<>();
    private final Map<Material, Double> defaultBuyerPrices = new HashMap<>();

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    public ConfigManager(SimpleEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.checkUpdates = config.getBoolean("check_updates", config.getBoolean("check-updates", true));

        this.shopEnabled = config.getBoolean("modules.shop", true);
        this.auctionEnabled = config.getBoolean("modules.auction", true);
        this.buyerEnabled = config.getBoolean("modules.buyer", true);

        this.maxAuctionSlots = config.getInt("auction.max_slots", 5);
        this.listingCooldownSeconds = config.getInt("auction.listing_cooldown_seconds", 0);
        this.publishDelaySeconds = config.getInt("auction.publish_delay_seconds", 5);
        this.expireHours = config.getInt("auction.expire_hours", 48);
        this.minPrice = config.getDouble("auction.min_price", 1.0);
        this.maxPrice = config.getDouble("auction.max_price", 100000000.0);
        this.auctionAdminWhitelistEnabled = config.getBoolean("auction.admin_whitelist.enabled", false);
        this.auctionAdminWhitelist.clear();
        this.auctionAdminWhitelist.addAll(config.getStringList("auction.admin_whitelist.players"));

        this.buyerSoundEnabled = config.getBoolean("buyer.sound_enabled", true);
        this.buyerDropIfFull = config.getBoolean("buyer.drop_items_on_close_if_full", true);
        this.shopCloseOnBuy = config.getBoolean("shop.close_on_buy", false);

        this.shopItemLockEnabled = config.getBoolean("shop.item_lock.enabled", false);
        this.shopItemLockAllowedPercentage = config.getDouble("shop.item_lock.allowed_percentage", 100.0);
        this.shopItemLockBypassPermission = config.getString("shop.item_lock.bypass_permission", "simpleeconomy.shop.bypass");

        this.dynamicMarketEnabled = config.getBoolean("dynamic_market.enabled", true);
        this.dynamicMarketChangePercent = config.getDouble("dynamic_market.change_percent_per_item", 0.1);
        this.dynamicMarketMinMultiplier = config.getDouble("dynamic_market.min_price_multiplier", 0.5);
        this.dynamicMarketMaxMultiplier = config.getDouble("dynamic_market.max_price_multiplier", 3.0);
        this.dynamicMarketDecayInterval = config.getInt("dynamic_market.decay_interval_seconds", 300);
        this.dynamicMarketDecayPercent = config.getDouble("dynamic_market.decay_percent", 1.5);
        this.dynamicMarketShowTrends = config.getBoolean("dynamic_market.show_trends", true);

        SoundUtil.setEnabled(config.getBoolean("sounds.enabled", true));

        String currencySymbol = config.getString("currency.symbol", "$");
        String currencyFormat = config.getString("currency.format", "%amount%%symbol%");
        MessageUtil.setCurrencyConfig(currencySymbol, currencyFormat);

        loadSectionsAndShops();
        loadBuyerPrices();
    }

    private void loadSectionsAndShops() {
        sections.clear();

        File sectionsDir = new File(plugin.getDataFolder(), "sections");
        if (!sectionsDir.exists()) {
            sectionsDir.mkdirs();
            String[] defaultSections = {
                    "Blocks.yml", "Workstations.yml", "Farming.yml", "Redstone.yml",
                    "Decoration.yml", "Dyes.yml", "Enchanting.yml", "Food.yml", "Miscellaneous.yml",
                    "Mobs.yml", "Music.yml", "Ores.yml", "Potions.yml", "SpawnEggs.yml", "Spawners.yml", "Z_EverythingElse.yml"
            };
            for (String sec : defaultSections) {
                try {
                    plugin.saveResource("sections/" + sec, false);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        File shopsDir = new File(plugin.getDataFolder(), "shops");
        if (!shopsDir.exists()) {
            shopsDir.mkdirs();
            String[] defaultShops = {
                    "Blocks.yml", "Workstations.yml", "Farming.yml", "Redstone.yml",
                    "Decoration.yml", "Dyes.yml", "Enchanting.yml", "Food.yml", "Miscellaneous.yml",
                    "Mobs.yml", "Music.yml", "Ores.yml", "Potions.yml", "SpawnEggs.yml", "Spawners.yml", "Z_EverythingElse.yml"
            };
            for (String sh : defaultShops) {
                try {
                    plugin.saveResource("shops/" + sh, false);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        File[] secFiles = sectionsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (secFiles != null) {
            for (File file : secFiles) {
                String sectionId = file.getName().replace(".yml", "");
                YamlConfiguration secConfig = YamlConfiguration.loadConfiguration(file);

                boolean enable = secConfig.getBoolean("enable", true);
                int slot = secConfig.getInt("slot", 10);

                // Check item.material or material
                String matName = secConfig.getString("item.material", secConfig.getString("material", "CHEST"));
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.CHEST;

                String displayName = secConfig.getString("item.name",
                        secConfig.getString("item.displayname",
                                secConfig.getString("name", sectionId)));

                List<String> lore = secConfig.getStringList("item.lore");
                if (lore.isEmpty()) {
                    lore = secConfig.getStringList("lore");
                }

                String shopFile = secConfig.getString("shop_file", sectionId + ".yml");
                ShopSection section = new ShopSection(sectionId, enable, slot, mat, displayName, lore, shopFile);

                // Load pages and items for this section from shops/
                File targetShop = new File(shopsDir, shopFile);
                if (targetShop.exists()) {
                    YamlConfiguration shopConfig = YamlConfiguration.loadConfiguration(targetShop);
                    loadShopItemsIntoSection(section, shopConfig);
                }

                sections.put(sectionId, section);
            }
        }
        plugin.getLogger().info("Loaded " + sections.size() + " shop sections with total " + getTotalItemsAcrossAllSections() + " items.");
    }

    private void loadShopItemsIntoSection(ShopSection section, YamlConfiguration shopConfig) {
        ConfigurationSection pagesSec = shopConfig.getConfigurationSection("pages");
        if (pagesSec != null) {
            List<String> pageKeys = new ArrayList<>(pagesSec.getKeys(false));
            pageKeys.sort((a, b) -> Integer.compare(extractNumber(a), extractNumber(b)));

            int pageNum = 1;
            for (String pageKey : pageKeys) {
                ConfigurationSection pageItemsSec = pagesSec.getConfigurationSection(pageKey + ".items");
                if (pageItemsSec == null) {
                    pageItemsSec = pagesSec.getConfigurationSection(pageKey);
                }
                if (pageItemsSec != null) {
                    List<ShopItem> items = parseItemsSection(pageItemsSec);
                    section.setPageItems(pageNum++, items);
                }
            }
        } else {
            ConfigurationSection itemsSec = shopConfig.getConfigurationSection("items");
            if (itemsSec != null) {
                List<ShopItem> allItems = parseItemsSection(itemsSec);
                int pageNum = 1;
                for (int i = 0; i < allItems.size(); i += 45) {
                    int end = Math.min(i + 45, allItems.size());
                    section.setPageItems(pageNum++, allItems.subList(i, end));
                }
            }
        }
    }

    private List<ShopItem> parseItemsSection(ConfigurationSection sec) {
        List<ShopItem> itemsList = new ArrayList<>();
        for (String key : sec.getKeys(false)) {
            String itemMatName = sec.getString(key + ".material", "STONE");
            Material itemMat = Material.matchMaterial(itemMatName);
            if (itemMat == null) continue;

            double buy = sec.getDouble(key + ".buy", -1.0);
            double sell = sec.getDouble(key + ".sell", -1.0);
            int itemSlot = sec.getInt(key + ".slot", -1);
            String displayName = sec.getString(key + ".displayname", sec.getString(key + ".name", null));
            List<String> rawLore = sec.getStringList(key + ".lore");
            List<String> lore = new ArrayList<>();
            if (rawLore != null) {
                for (String line : rawLore) {
                    if (line.contains("Недоступно") || line.contains("недоступно")
                            || line.contains("Unavailable") || line.contains("unavailable")
                            || line.contains("✖")) {
                        continue;
                    }
                    lore.add(line);
                }
            }

            itemsList.add(new ShopItem(key, itemMat, buy, sell, itemSlot, displayName, lore));
        }
        return itemsList;
    }

    private int extractNumber(String str) {
        Matcher m = NUMBER_PATTERN.matcher(str);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private int getTotalItemsAcrossAllSections() {
        int count = 0;
        for (ShopSection s : sections.values()) {
            count += s.getTotalItemCount();
        }
        return count;
    }

    private void loadBuyerPrices() {
        defaultBuyerPrices.clear();
        File buyerFile = new File(plugin.getDataFolder(), "buyer_prices.yml");
        if (!buyerFile.exists()) {
            try {
                plugin.saveResource("buyer_prices.yml", false);
            } catch (IllegalArgumentException ignored) {}
        }

        YamlConfiguration buyerConfig = YamlConfiguration.loadConfiguration(buyerFile);
        ConfigurationSection pricesSec = buyerConfig.getConfigurationSection("prices");
        if (pricesSec != null) {
            for (String key : pricesSec.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat != null) {
                    defaultBuyerPrices.put(mat, pricesSec.getDouble(key, 0.0));
                }
            }
        }

        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().loadBuyerPrices(defaultBuyerPrices);
        }
    }

    public boolean isShopEnabled() {
        return shopEnabled;
    }

    public boolean isAuctionEnabled() {
        return auctionEnabled;
    }

    public boolean isBuyerEnabled() {
        return buyerEnabled;
    }

    public int getMaxAuctionSlots() {
        return maxAuctionSlots;
    }

    public int getListingCooldownSeconds() {
        return listingCooldownSeconds;
    }

    public int getPublishDelaySeconds() {
        return publishDelaySeconds;
    }

    public int getExpireHours() {
        return expireHours;
    }

    public double getMinPrice() {
        return minPrice;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public boolean isBuyerSoundEnabled() {
        return buyerSoundEnabled;
    }

    public boolean isBuyerDropIfFull() {
        return buyerDropIfFull;
    }

    public boolean isShopCloseOnBuy() {
        return shopCloseOnBuy;
    }

    public boolean isShopItemLockEnabled() {
        return shopItemLockEnabled;
    }

    public double getShopItemLockAllowedPercentage() {
        return shopItemLockAllowedPercentage;
    }

    public String getShopItemLockBypassPermission() {
        return shopItemLockBypassPermission;
    }

    public boolean isDynamicMarketEnabled() {
        return dynamicMarketEnabled;
    }

    public double getDynamicMarketChangePercent() {
        return dynamicMarketChangePercent;
    }

    public double getDynamicMarketMinMultiplier() {
        return dynamicMarketMinMultiplier;
    }

    public double getDynamicMarketMaxMultiplier() {
        return dynamicMarketMaxMultiplier;
    }

    public int getDynamicMarketDecayInterval() {
        return dynamicMarketDecayInterval;
    }

    public double getDynamicMarketDecayPercent() {
        return dynamicMarketDecayPercent;
    }

    public boolean isDynamicMarketShowTrends() {
        return dynamicMarketShowTrends;
    }

    public Map<String, ShopSection> getSections() {
        return sections;
    }

    public ShopSection getSection(String id) {
        return sections.get(id);
    }

    public boolean isAuctionAdminAllowed(Player player) {
        if (player == null || !player.isOp()) {
            return false;
        }
        if (!auctionAdminWhitelistEnabled) {
            return true;
        }
        for (String name : auctionAdminWhitelist) {
            if (name.equalsIgnoreCase(player.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean isAuctionAdminWhitelistEnabled() {
        return auctionAdminWhitelistEnabled;
    }

    public List<String> getAuctionAdminWhitelist() {
        return Collections.unmodifiableList(auctionAdminWhitelist);
    }

    public boolean isCheckUpdates() {
        return checkUpdates;
    }
}
