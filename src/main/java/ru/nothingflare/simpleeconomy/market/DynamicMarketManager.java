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

package ru.nothingflare.simpleeconomy.market;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;
import ru.nothingflare.simpleeconomy.config.LanguageManager;
import ru.nothingflare.simpleeconomy.database.models.ShopItem;
import ru.nothingflare.simpleeconomy.database.models.ShopSection;
import ru.nothingflare.simpleeconomy.scheduler.TaskScheduler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicMarketManager {

    private final SimpleEconomyPlugin plugin;

    private boolean enabled = true;
    private boolean hardcoreMode = true;
    private double absoluteMinPrice = 0.01;
    private double percentPerStack = 2.0;
    private int impactDurationHours = 24;
    private double minPriceMultiplier = 0.5;
    private double maxPriceMultiplier = 3.0;
    private boolean showTrends = true;
    private double maxBuyerPayoutRatio = 0.75;

    // Rolling window hourly buckets:
    // Key: itemKey / material -> Map<HourEpoch, TotalAmountInThatHour>
    private final Map<String, Map<Long, Integer>> shopBuckets = new ConcurrentHashMap<>();
    private final Map<Material, Map<Long, Integer>> buyerBuckets = new ConcurrentHashMap<>();

    // Cached market item records for database synchronization
    private final Map<String, MarketItemData> shopPrices = new ConcurrentHashMap<>();
    private final Map<Material, MarketItemData> buyerPrices = new ConcurrentHashMap<>();

    private boolean taskStarted = false;

    public DynamicMarketManager(SimpleEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public static long getEpochHour() {
        return System.currentTimeMillis() / 3600000L;
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("dynamic_market.enabled", true);
        this.hardcoreMode = config.getBoolean("dynamic_market.hardcore_mode",
                config.getBoolean("dynamic_market.unlimited_limits", true));
        this.absoluteMinPrice = Math.max(0.001, config.getDouble("dynamic_market.absolute_min_price", 0.01));
        this.percentPerStack = config.getDouble("dynamic_market.percent_per_stack",
                config.getDouble("dynamic_market.change_percent_per_stack",
                        config.getDouble("dynamic_market.change_percent_per_item", 0.03125) * 64.0));
        this.impactDurationHours = Math.max(1, config.getInt("dynamic_market.impact_duration_hours", 24));
        this.minPriceMultiplier = config.getDouble("dynamic_market.min_price_multiplier", 0.5);
        this.maxPriceMultiplier = config.getDouble("dynamic_market.max_price_multiplier", 3.0);
        this.showTrends = config.getBoolean("dynamic_market.show_trends", true);
        this.maxBuyerPayoutRatio = Math.max(0.01, Math.min(1.0, config.getDouble("dynamic_market.max_buyer_payout_ratio", 0.75)));

        long currentHour = getEpochHour();
        long minValidHour = currentHour - impactDurationHours + 1;

        // 1. Cleanup expired buckets and load active hourly buckets from database
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().cleanupExpiredMarketBuckets(minValidHour);
            plugin.getDatabaseManager().loadActiveMarketBuckets(minValidHour, shopBuckets, buyerBuckets);
            plugin.getDatabaseManager().loadDynamicMarket(shopPrices, buyerPrices);
        }

        // 2. Pre-populate all configured shop items into the market cache
        if (plugin.getConfigManager() != null) {
            List<MarketItemData> modifiedShopItems = new ArrayList<>();
            for (ShopSection sec : plugin.getConfigManager().getSections().values()) {
                if (!sec.isEnabled()) continue;
                for (ShopItem it : sec.getItems()) {
                    double base = it.getBuyPrice();
                    if (base > 0) {
                        String key = sec.getId() + ":" + it.getId();
                        double livePrice = calculateShopBuyPrice(key, base);
                        MarketItemData data = shopPrices.get(key);
                        if (data == null) {
                            data = new MarketItemData("SHOP", key, livePrice, base, System.currentTimeMillis());
                            shopPrices.put(key, data);
                            modifiedShopItems.add(data);
                        } else {
                            data.setBasePrice(base);
                            data.setCurrentPrice(livePrice);
                            data.setLastUpdate(System.currentTimeMillis());
                            modifiedShopItems.add(data);
                        }
                    }
                }
            }
            if (!modifiedShopItems.isEmpty() && plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().saveAllDynamicMarketItems(modifiedShopItems);
            }
        }

        // 3. Pre-populate all configured buyer items into the market cache
        if (plugin.getDatabaseManager() != null) {
            List<MarketItemData> modifiedBuyerItems = new ArrayList<>();
            for (Map.Entry<Material, Double> entry : plugin.getDatabaseManager().getAllBuyerPrices().entrySet()) {
                double base = entry.getValue();
                if (base > 0) {
                    double livePrice = calculateBuyerSellPrice(entry.getKey(), base);
                    MarketItemData data = buyerPrices.get(entry.getKey());
                    if (data == null) {
                        data = new MarketItemData("BUYER", entry.getKey().name(), livePrice, base, System.currentTimeMillis());
                        buyerPrices.put(entry.getKey(), data);
                        modifiedBuyerItems.add(data);
                    } else {
                        data.setBasePrice(base);
                        data.setCurrentPrice(livePrice);
                        data.setLastUpdate(System.currentTimeMillis());
                        modifiedBuyerItems.add(data);
                    }
                }
            }
            if (!modifiedBuyerItems.isEmpty()) {
                plugin.getDatabaseManager().saveAllDynamicMarketItems(modifiedBuyerItems);
            }
        }

        // 4. Start periodic rolling expiration task (runs every 10 seconds / 200 ticks)
        if (!taskStarted) {
            taskStarted = true;
            TaskScheduler.runTimerAsync(plugin, this::processRollingExpiration, 200L, 200L);
        }
    }

    // --- Price Calculations based on Hourly Buckets ---

    private int getActiveShopVolume(String key) {
        Map<Long, Integer> buckets = shopBuckets.get(key);
        if (buckets == null || buckets.isEmpty()) {
            return 0;
        }
        long currentHour = getEpochHour();
        long minValidHour = currentHour - impactDurationHours + 1;
        int sum = 0;
        for (Map.Entry<Long, Integer> entry : buckets.entrySet()) {
            if (entry.getKey() >= minValidHour) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    private int getActiveBuyerVolume(Material material) {
        Map<Long, Integer> buckets = buyerBuckets.get(material);
        if (buckets == null || buckets.isEmpty()) {
            return 0;
        }
        long currentHour = getEpochHour();
        long minValidHour = currentHour - impactDurationHours + 1;
        int sum = 0;
        for (Map.Entry<Long, Integer> entry : buckets.entrySet()) {
            if (entry.getKey() >= minValidHour) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    private double calculateShopBuyPrice(String key, double basePrice) {
        if (!enabled || basePrice <= 0) {
            return basePrice;
        }

        int totalBought = getActiveShopVolume(key);
        double deltaPercent = (totalBought / 64.0) * percentPerStack;
        double multiplier = 1.0 + (deltaPercent / 100.0);

        if (!hardcoreMode) {
            if (multiplier > maxPriceMultiplier) {
                multiplier = maxPriceMultiplier;
            }
        }

        double price = basePrice * multiplier;
        if (price < absoluteMinPrice) {
            price = absoluteMinPrice;
        }
        return Math.round(price * 10000.0) / 10000.0;
    }

    private double calculateBuyerSellPrice(Material material, double basePrice) {
        if (!enabled || basePrice <= 0) {
            return basePrice;
        }

        int totalSold = getActiveBuyerVolume(material);
        double deltaPercent = (totalSold / 64.0) * percentPerStack;
        double price;

        if (hardcoreMode) {
            double multiplier = Math.max(0.0, 1.0 - (deltaPercent / 100.0));
            price = basePrice * multiplier;
            if (price < absoluteMinPrice) {
                price = absoluteMinPrice;
            }
        } else {
            double multiplier = Math.max(minPriceMultiplier, 1.0 - (deltaPercent / 100.0));
            price = basePrice * multiplier;
        }

        // Anti-Arbitrage Protection: Buyer payout can NEVER exceed shop buy price for the same item!
        double shopPrice = getLowestShopBuyPrice(material);
        if (shopPrice > 0) {
            double maxAllowed = Math.round(shopPrice * maxBuyerPayoutRatio * 10000.0) / 10000.0;
            if (maxAllowed < absoluteMinPrice) {
                maxAllowed = absoluteMinPrice;
            }
            if (price > maxAllowed) {
                price = maxAllowed;
            }
        }

        return Math.round(price * 10000.0) / 10000.0;
    }

    // --- Public Shop API ---

    public double getShopBuyPrice(ShopSection section, ShopItem item) {
        if (!enabled || item.getBuyPrice() <= 0) {
            return item.getBuyPrice();
        }
        String key = (section != null ? section.getId() : "shop") + ":" + item.getId();
        return calculateShopBuyPrice(key, item.getBuyPrice());
    }

    public void recordShopPurchase(ShopSection section, ShopItem item, int amount) {
        if (!enabled || amount <= 0 || item.getBuyPrice() <= 0) {
            return;
        }

        long currentHour = getEpochHour();
        String key = (section != null ? section.getId() : "shop") + ":" + item.getId();

        shopBuckets.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).merge(currentHour, amount, Integer::sum);

        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().recordMarketBucket("SHOP", key, currentHour, amount);
        }

        // Update cached price and persist
        double newPrice = calculateShopBuyPrice(key, item.getBuyPrice());
        MarketItemData data = shopPrices.computeIfAbsent(key, k ->
                new MarketItemData("SHOP", k, newPrice, item.getBuyPrice(), System.currentTimeMillis())
        );
        data.setCurrentPrice(newPrice);
        data.setLastUpdate(System.currentTimeMillis());
        saveAsync(data);
    }

    public String getShopTrendLore(ShopSection section, ShopItem item) {
        if (!enabled || !showTrends || !item.isBuyable()) {
            return null;
        }

        double base = item.getBuyPrice();
        if (base <= 0) {
            return null;
        }

        double current = getShopBuyPrice(section, item);
        double diffPct = ((current - base) / base) * 100.0;

        LanguageManager lm = plugin.getLanguageManager();
        Map<String, String> ph = new HashMap<>();
        ph.put("percent", String.format(Locale.US, "%.1f", Math.abs(diffPct)));

        String trendStr;
        if (diffPct > 0.05) {
            trendStr = lm.getMessage("shop.dynamic_trend_up", ph);
        } else if (diffPct < -0.05) {
            trendStr = lm.getMessage("shop.dynamic_trend_down", ph);
        } else {
            trendStr = lm.getMessage("shop.dynamic_trend_stable");
        }

        Map<String, String> trendPh = new HashMap<>();
        trendPh.put("trend", trendStr);
        return lm.getMessage("shop.dynamic_market_info", trendPh);
    }

    // --- Public Buyer API ---

    public double getBuyerSellPrice(Material material) {
        if (material == null || material == Material.AIR) {
            return 0.0;
        }

        double base = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getBuyerPrice(material) : 0.0;
        if (!enabled || base <= 0) {
            return base;
        }

        return calculateBuyerSellPrice(material, base);
    }

    public void recordBuyerSale(Material material, int amount) {
        if (!enabled || amount <= 0 || material == null || material == Material.AIR) {
            return;
        }

        double base = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getBuyerPrice(material) : 0.0;
        if (base <= 0) {
            return;
        }

        long currentHour = getEpochHour();
        buyerBuckets.computeIfAbsent(material, m -> new ConcurrentHashMap<>()).merge(currentHour, amount, Integer::sum);

        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().recordMarketBucket("BUYER", material.name(), currentHour, amount);
        }

        // Update cached price and persist
        double newPrice = calculateBuyerSellPrice(material, base);
        MarketItemData data = buyerPrices.computeIfAbsent(material, m ->
                new MarketItemData("BUYER", m.name(), newPrice, base, System.currentTimeMillis())
        );
        data.setCurrentPrice(newPrice);
        data.setLastUpdate(System.currentTimeMillis());
        saveAsync(data);
    }

    public String getBuyerTrendLore(Material material) {
        if (!enabled || !showTrends || material == null || material == Material.AIR) {
            return null;
        }

        double base = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getBuyerPrice(material) : 0.0;
        if (base <= 0) {
            return null;
        }

        double current = getBuyerSellPrice(material);
        double diffPct = ((current - base) / base) * 100.0;

        LanguageManager lm = plugin.getLanguageManager();
        Map<String, String> ph = new HashMap<>();
        ph.put("percent", String.format(Locale.US, "%.1f", Math.abs(diffPct)));

        String trendStr;
        if (diffPct > 0.05) {
            trendStr = lm.getMessage("buyer.dynamic_trend_up", ph);
        } else if (diffPct < -0.05) {
            trendStr = lm.getMessage("buyer.dynamic_trend_down", ph);
        } else {
            trendStr = lm.getMessage("buyer.dynamic_trend_stable");
        }

        Map<String, String> trendPh = new HashMap<>();
        trendPh.put("trend", trendStr);
        return lm.getMessage("buyer.dynamic_market_info", trendPh);
    }

    // --- Arbitrage Protection ---

    /**
     * Finds the lowest dynamic buy price for a material across all shop sections.
     * Returns -1 if the material cannot be bought in the shop.
     */
    public double getLowestShopBuyPrice(Material material) {
        if (material == null || material == Material.AIR) {
            return -1.0;
        }
        if (plugin.getConfigManager() == null) {
            return -1.0;
        }

        double lowest = -1.0;
        for (ShopSection section : plugin.getConfigManager().getSections().values()) {
            if (!section.isEnabled()) continue;
            for (ShopItem item : section.getItems()) {
                if (item.getMaterial() == material && item.isBuyable() && item.getBuyPrice() > 0) {
                    double currentPrice = getShopBuyPrice(section, item);
                    if (lowest < 0 || currentPrice < lowest) {
                        lowest = currentPrice;
                    }
                }
            }
        }
        return lowest;
    }

    // --- Rolling Window Expiration Task ---

    /**
     * Periodically checks and purges expired hourly buckets (older than impactDurationHours).
     * When an hourly bucket expires, its price impact is rolled back automatically!
     */
    public void processRollingExpiration() {
        if (!enabled) {
            return;
        }

        long currentHour = getEpochHour();
        long minValidHour = currentHour - impactDurationHours + 1;
        boolean removedAny = false;

        // 1. Purge expired shop buckets
        for (Map.Entry<String, Map<Long, Integer>> entry : shopBuckets.entrySet()) {
            Map<Long, Integer> buckets = entry.getValue();
            if (buckets != null) {
                if (buckets.keySet().removeIf(hour -> hour < minValidHour)) {
                    removedAny = true;
                }
            }
        }

        // 2. Purge expired buyer buckets
        for (Map.Entry<Material, Map<Long, Integer>> entry : buyerBuckets.entrySet()) {
            Map<Long, Integer> buckets = entry.getValue();
            if (buckets != null) {
                if (buckets.keySet().removeIf(hour -> hour < minValidHour)) {
                    removedAny = true;
                }
            }
        }

        // 3. Purge from database if any bucket expired
        if (removedAny && plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().cleanupExpiredMarketBuckets(minValidHour);

            // Re-sync cached prices
            List<MarketItemData> toSave = new ArrayList<>();
            for (MarketItemData data : shopPrices.values()) {
                double newPrice = calculateShopBuyPrice(data.getItemKey(), data.getBasePrice());
                if (Math.abs(newPrice - data.getCurrentPrice()) >= 0.001) {
                    data.setCurrentPrice(newPrice);
                    data.setLastUpdate(System.currentTimeMillis());
                    toSave.add(data);
                }
            }
            for (Map.Entry<Material, MarketItemData> entry : buyerPrices.entrySet()) {
                MarketItemData data = entry.getValue();
                double newPrice = calculateBuyerSellPrice(entry.getKey(), data.getBasePrice());
                if (Math.abs(newPrice - data.getCurrentPrice()) >= 0.001) {
                    data.setCurrentPrice(newPrice);
                    data.setLastUpdate(System.currentTimeMillis());
                    toSave.add(data);
                }
            }
            if (!toSave.isEmpty()) {
                plugin.getDatabaseManager().saveAllDynamicMarketItems(toSave);
            }
        }
    }

    public void saveAll() {
        if (plugin.getDatabaseManager() != null) {
            List<MarketItemData> all = new ArrayList<>(shopPrices.values());
            all.addAll(buyerPrices.values());
            plugin.getDatabaseManager().saveAllDynamicMarketItems(all);
        }
    }

    private void saveAsync(MarketItemData data) {
        TaskScheduler.runAsync(plugin, () -> {
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().saveDynamicMarketItem(data);
            }
        });
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isHardcoreMode() {
        return hardcoreMode;
    }

    public double getAbsoluteMinPrice() {
        return absoluteMinPrice;
    }

    public boolean isShowTrends() {
        return showTrends;
    }

    public double getMaxBuyerPayoutRatio() {
        return maxBuyerPayoutRatio;
    }

    public int getImpactDurationHours() {
        return impactDurationHours;
    }

    public double getPercentPerStack() {
        return percentPerStack;
    }

    // --- Data Model ---

    public static class MarketItemData {
        private final String marketType;
        private final String itemKey;
        private volatile double currentPrice;
        private volatile double basePrice;
        private volatile long lastUpdate;

        public MarketItemData(String marketType, String itemKey, double currentPrice, double basePrice, long lastUpdate) {
            this.marketType = marketType;
            this.itemKey = itemKey;
            this.currentPrice = currentPrice;
            this.basePrice = basePrice;
            this.lastUpdate = lastUpdate;
        }

        public String getMarketType() {
            return marketType;
        }

        public String getItemKey() {
            return itemKey;
        }

        public double getCurrentPrice() {
            return currentPrice;
        }

        public void setCurrentPrice(double currentPrice) {
            this.currentPrice = currentPrice;
        }

        public double getBasePrice() {
            return basePrice;
        }

        public void setBasePrice(double basePrice) {
            this.basePrice = basePrice;
        }

        public long getLastUpdate() {
            return lastUpdate;
        }

        public void setLastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
        }
    }
}
