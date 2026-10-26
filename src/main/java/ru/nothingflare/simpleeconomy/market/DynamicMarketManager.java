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
    private double changePercentPerItem = 0.1;
    private double minPriceMultiplier = 0.5;
    private double maxPriceMultiplier = 3.0;
    private int decayIntervalSeconds = 300;
    private double decayPercent = 1.5;
    private boolean showTrends = true;

    private final Map<String, MarketItemData> shopPrices = new ConcurrentHashMap<>();
    private final Map<Material, MarketItemData> buyerPrices = new ConcurrentHashMap<>();

    private boolean taskStarted = false;

    public DynamicMarketManager(SimpleEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("dynamic_market.enabled", true);
        this.changePercentPerItem = config.getDouble("dynamic_market.change_percent_per_item", 0.1);
        this.minPriceMultiplier = config.getDouble("dynamic_market.min_price_multiplier", 0.5);
        this.maxPriceMultiplier = config.getDouble("dynamic_market.max_price_multiplier", 3.0);
        this.decayIntervalSeconds = Math.max(10, config.getInt("dynamic_market.decay_interval_seconds", 300));
        this.decayPercent = config.getDouble("dynamic_market.decay_percent", 1.5);
        this.showTrends = config.getBoolean("dynamic_market.show_trends", true);

        // Load prices from database
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().loadDynamicMarket(shopPrices, buyerPrices);
        }

        // Start periodic market decay timer if not already running
        if (!taskStarted) {
            taskStarted = true;
            long periodTicks = 20L * decayIntervalSeconds;
            TaskScheduler.runTimerAsync(plugin, this::processDecay, periodTicks, periodTicks);
        }
    }

    // --- Shop Dynamic Pricing ---

    public double getShopBuyPrice(ShopSection section, ShopItem item) {
        if (!enabled || item.getBuyPrice() <= 0) {
            return item.getBuyPrice();
        }

        String key = (section != null ? section.getId() : "shop") + ":" + item.getId();
        double base = item.getBuyPrice();

        MarketItemData data = shopPrices.computeIfAbsent(key, k ->
                new MarketItemData("SHOP", k, base, base, System.currentTimeMillis())
        );

        // Synchronize base price if shop config changed
        if (Math.abs(data.getBasePrice() - base) > 0.0001) {
            data.setBasePrice(base);
        }

        double price = data.getCurrentPrice();
        double minCap = base * minPriceMultiplier;
        double maxCap = base * maxPriceMultiplier;
        if (price < minCap) price = minCap;
        if (price > maxCap) price = maxCap;

        return Math.round(price * 100.0) / 100.0;
    }

    public void recordShopPurchase(ShopSection section, ShopItem item, int amount) {
        if (!enabled || amount <= 0 || item.getBuyPrice() <= 0) {
            return;
        }

        String key = (section != null ? section.getId() : "shop") + ":" + item.getId();
        double base = item.getBuyPrice();

        MarketItemData data = shopPrices.computeIfAbsent(key, k ->
                new MarketItemData("SHOP", k, base, base, System.currentTimeMillis())
        );

        double current = data.getCurrentPrice();
        double multiplier = 1.0 + ((changePercentPerItem * amount) / 100.0);
        double newPrice = current * multiplier;

        double maxCap = base * maxPriceMultiplier;
        if (newPrice > maxCap) {
            newPrice = maxCap;
        }

        newPrice = Math.round(newPrice * 100.0) / 100.0;
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
        if (diffPct > 0.5) {
            trendStr = lm.getMessage("shop.dynamic_trend_up", ph);
        } else if (diffPct < -0.5) {
            trendStr = lm.getMessage("shop.dynamic_trend_down", ph);
        } else {
            trendStr = lm.getMessage("shop.dynamic_trend_stable");
        }

        Map<String, String> trendPh = new HashMap<>();
        trendPh.put("trend", trendStr);
        return lm.getMessage("shop.dynamic_market_info", trendPh);
    }

    // --- Buyer Dynamic Pricing ---

    public double getBuyerSellPrice(Material material) {
        if (material == null || material == Material.AIR) {
            return 0.0;
        }

        double base = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getBuyerPrice(material) : 0.0;
        if (!enabled || base <= 0) {
            return base;
        }

        MarketItemData data = buyerPrices.computeIfAbsent(material, m ->
                new MarketItemData("BUYER", m.name(), base, base, System.currentTimeMillis())
        );

        if (Math.abs(data.getBasePrice() - base) > 0.0001) {
            data.setBasePrice(base);
        }

        double price = data.getCurrentPrice();
        double minFloor = base * minPriceMultiplier;
        double maxCap = base * maxPriceMultiplier;
        if (price < minFloor) price = minFloor;
        if (price > maxCap) price = maxCap;

        return Math.round(price * 100.0) / 100.0;
    }

    public void recordBuyerSale(Material material, int amount) {
        if (!enabled || amount <= 0 || material == null || material == Material.AIR) {
            return;
        }

        double base = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getBuyerPrice(material) : 0.0;
        if (base <= 0) {
            return;
        }

        MarketItemData data = buyerPrices.computeIfAbsent(material, m ->
                new MarketItemData("BUYER", m.name(), base, base, System.currentTimeMillis())
        );

        double current = data.getCurrentPrice();
        // Market gets saturated -> scrap payout price drops
        double multiplier = 1.0 - ((changePercentPerItem * amount) / 100.0);
        double newPrice = current * multiplier;

        double minFloor = base * minPriceMultiplier;
        if (newPrice < minFloor) {
            newPrice = minFloor;
        }

        newPrice = Math.round(newPrice * 100.0) / 100.0;
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
        if (diffPct > 0.5) {
            trendStr = lm.getMessage("buyer.dynamic_trend_up", ph);
        } else if (diffPct < -0.5) {
            trendStr = lm.getMessage("buyer.dynamic_trend_down", ph);
        } else {
            trendStr = lm.getMessage("buyer.dynamic_trend_stable");
        }

        Map<String, String> trendPh = new HashMap<>();
        trendPh.put("trend", trendStr);
        return lm.getMessage("buyer.dynamic_market_info", trendPh);
    }

    // --- Market Decay & Balancing ---

    public void processDecay() {
        if (!enabled) {
            return;
        }

        long now = System.currentTimeMillis();
        long thresholdMillis = (long) decayIntervalSeconds * 1000L;

        // 1. Process Shop Prices:
        // Items that sit unbought drop in price towards clearance discount (minPriceMultiplier * base)
        for (MarketItemData data : shopPrices.values()) {
            if ((now - data.getLastUpdate()) >= thresholdMillis) {
                double current = data.getCurrentPrice();
                double base = data.getBasePrice();
                if (base <= 0) continue;

                double minPrice = base * minPriceMultiplier;
                double newPrice = current * (1.0 - (decayPercent / 100.0));
                if (newPrice < minPrice) {
                    newPrice = minPrice;
                }
                newPrice = Math.round(newPrice * 100.0) / 100.0;

                if (Math.abs(newPrice - current) >= 0.01) {
                    data.setCurrentPrice(newPrice);
                    data.setLastUpdate(now);
                    saveAsync(data);
                }
            }
        }

        // 2. Process Buyer Prices:
        // Items that haven't been sold for a while become scarce -> buyer raises payout price
        for (MarketItemData data : buyerPrices.values()) {
            if ((now - data.getLastUpdate()) >= thresholdMillis) {
                double current = data.getCurrentPrice();
                double base = data.getBasePrice();
                if (base <= 0) continue;

                double maxPrice = base * maxPriceMultiplier;
                double newPrice = current * (1.0 + (decayPercent / 100.0));
                if (newPrice > maxPrice) {
                    newPrice = maxPrice;
                }
                newPrice = Math.round(newPrice * 100.0) / 100.0;

                if (Math.abs(newPrice - current) >= 0.01) {
                    data.setCurrentPrice(newPrice);
                    data.setLastUpdate(now);
                    saveAsync(data);
                }
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

    public boolean isShowTrends() {
        return showTrends;
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
