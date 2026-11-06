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
    private double changePercentPerItem = 0.2;
    private double minPriceMultiplier = 0.5;
    private double maxPriceMultiplier = 3.0;
    private int decayIntervalSeconds = 300;
    private double decayPercent = 1.5;
    private boolean showTrends = true;
    private double maxBuyerPayoutRatio = 0.75;

    private final Map<String, MarketItemData> shopPrices = new ConcurrentHashMap<>();
    private final Map<Material, MarketItemData> buyerPrices = new ConcurrentHashMap<>();

    private boolean taskStarted = false;

    public DynamicMarketManager(SimpleEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("dynamic_market.enabled", true);
        this.hardcoreMode = config.getBoolean("dynamic_market.hardcore_mode",
                config.getBoolean("dynamic_market.unlimited_limits", true));
        this.absoluteMinPrice = Math.max(0.001, config.getDouble("dynamic_market.absolute_min_price", 0.01));
        this.changePercentPerItem = config.getDouble("dynamic_market.change_percent_per_item", 0.2);
        this.minPriceMultiplier = config.getDouble("dynamic_market.min_price_multiplier", 0.5);
        this.maxPriceMultiplier = config.getDouble("dynamic_market.max_price_multiplier", 3.0);
        this.decayIntervalSeconds = Math.max(10, config.getInt("dynamic_market.decay_interval_seconds", 300));
        this.decayPercent = config.getDouble("dynamic_market.decay_percent", 1.5);
        this.showTrends = config.getBoolean("dynamic_market.show_trends", true);
        this.maxBuyerPayoutRatio = Math.max(0.01, Math.min(1.0, config.getDouble("dynamic_market.max_buyer_payout_ratio", 0.75)));

        // 1. Load prices from database
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().loadDynamicMarket(shopPrices, buyerPrices);
        }

        // 2. Pre-populate all configured shop items into the market if not present
        if (plugin.getConfigManager() != null) {
            for (ShopSection sec : plugin.getConfigManager().getSections().values()) {
                if (!sec.isEnabled()) continue;
                for (ShopItem it : sec.getItems()) {
                    if (it.getBuyPrice() > 0) {
                        String key = sec.getId() + ":" + it.getId();
                        shopPrices.putIfAbsent(key, new MarketItemData("SHOP", key, it.getBuyPrice(), it.getBuyPrice(), System.currentTimeMillis()));
                    }
                }
            }
        }

        // 3. Pre-populate all configured buyer items into the market if not present
        if (plugin.getDatabaseManager() != null) {
            for (Map.Entry<Material, Double> entry : plugin.getDatabaseManager().getAllBuyerPrices().entrySet()) {
                if (entry.getValue() > 0) {
                    buyerPrices.putIfAbsent(entry.getKey(), new MarketItemData("BUYER", entry.getKey().name(), entry.getValue(), entry.getValue(), System.currentTimeMillis()));
                }
            }
        }

        // 4. Start periodic market decay timer if not already running
        // Runs every 10 seconds (200 ticks); dynamically evaluates decayIntervalSeconds on the fly!
        if (!taskStarted) {
            taskStarted = true;
            TaskScheduler.runTimerAsync(plugin, this::processDecay, 200L, 200L);
        }
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
                    double currentPrice = getRawShopBuyPrice(section, item);
                    if (lowest < 0 || currentPrice < lowest) {
                        lowest = currentPrice;
                    }
                }
            }
        }
        return lowest;
    }

    // --- Shop Dynamic Pricing ---

    private double getRawShopBuyPrice(ShopSection section, ShopItem item) {
        if (!enabled || item.getBuyPrice() <= 0) {
            return item.getBuyPrice();
        }

        String key = (section != null ? section.getId() : "shop") + ":" + item.getId();
        double base = item.getBuyPrice();

        MarketItemData data = shopPrices.computeIfAbsent(key, k ->
                new MarketItemData("SHOP", k, base, base, System.currentTimeMillis())
        );

        if (Math.abs(data.getBasePrice() - base) > 0.0001) {
            double oldBase = data.getBasePrice();
            if (oldBase > 0) {
                double ratio = base / oldBase;
                data.setCurrentPrice(Math.round(data.getCurrentPrice() * ratio * 100.0) / 100.0);
            } else {
                data.setCurrentPrice(base);
            }
            data.setBasePrice(base);
            saveAsync(data);
        }

        double price = data.getCurrentPrice();
        if (!hardcoreMode) {
            double minCap = base * minPriceMultiplier;
            double maxCap = base * maxPriceMultiplier;
            if (price < minCap) price = minCap;
            if (price > maxCap) price = maxCap;
        } else {
            if (price < absoluteMinPrice) price = absoluteMinPrice;
        }

        return Math.round(price * 100.0) / 100.0;
    }

    public double getShopBuyPrice(ShopSection section, ShopItem item) {
        return getRawShopBuyPrice(section, item);
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

        if (!hardcoreMode) {
            double maxCap = base * maxPriceMultiplier;
            if (newPrice > maxCap) {
                newPrice = maxCap;
            }
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
            double oldBase = data.getBasePrice();
            if (oldBase > 0) {
                double ratio = base / oldBase;
                data.setCurrentPrice(Math.round(data.getCurrentPrice() * ratio * 100.0) / 100.0);
            } else {
                data.setCurrentPrice(base);
            }
            data.setBasePrice(base);
            saveAsync(data);
        }

        double price = data.getCurrentPrice();
        if (!hardcoreMode) {
            double minFloor = base * minPriceMultiplier;
            double maxCap = base * maxPriceMultiplier;
            if (price < minFloor) price = minFloor;
            if (price > maxCap) price = maxCap;
        } else {
            if (price < absoluteMinPrice) price = absoluteMinPrice;
        }

        // Anti-Arbitrage Protection: Buyer payout can NEVER exceed shop buy price for the same item!
        // Guarantees at least a (1.0 - maxBuyerPayoutRatio) spread margin so players can never loop money
        double shopPrice = getLowestShopBuyPrice(material);
        if (shopPrice > 0) {
            double maxAllowed = Math.round(shopPrice * maxBuyerPayoutRatio * 100.0) / 100.0;
            if (maxAllowed < absoluteMinPrice) {
                maxAllowed = absoluteMinPrice;
            }
            if (price > maxAllowed) {
                price = maxAllowed;
            }
        }

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
        // Hyperbolic AMM curve: supply saturation smoothly reduces price without risk of negative or zero multiplier
        double multiplier = 1.0 / (1.0 + ((changePercentPerItem * amount) / 100.0));
        double newPrice = current * multiplier;

        if (!hardcoreMode) {
            double minFloor = base * minPriceMultiplier;
            if (newPrice < minFloor) {
                newPrice = minFloor;
            }
        } else {
            if (newPrice < absoluteMinPrice) {
                newPrice = absoluteMinPrice;
            }
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
        List<MarketItemData> toSave = new ArrayList<>();

        // 1. Process Shop Prices:
        // Stale stock: prices cool down toward baseline or clearance floor
        for (MarketItemData data : shopPrices.values()) {
            if ((now - data.getLastUpdate()) >= thresholdMillis) {
                double current = data.getCurrentPrice();
                double base = data.getBasePrice();
                if (base <= 0) continue;

                double targetFloor = hardcoreMode ? absoluteMinPrice : (base * minPriceMultiplier);
                if (current <= targetFloor) {
                    continue;
                }

                // Minimum step 0.01 prevents sub-cent rounding freeze
                double step = Math.max(0.01, current * (decayPercent / 100.0));
                double newPrice = current - step;
                if (newPrice < targetFloor) {
                    newPrice = targetFloor;
                }
                newPrice = Math.round(newPrice * 100.0) / 100.0;

                if (Math.abs(newPrice - current) >= 0.001) {
                    data.setCurrentPrice(newPrice);
                    data.setLastUpdate(now);
                    toSave.add(data);
                }
            }
        }

        // 2. Process Buyer Prices:
        // Idle buyer items: market surplus clears, prices recover toward base price (equilibrium)
        for (MarketItemData data : buyerPrices.values()) {
            if ((now - data.getLastUpdate()) >= thresholdMillis) {
                double current = data.getCurrentPrice();
                double base = data.getBasePrice();
                if (base <= 0) continue;

                double newPrice;
                if (current < base) {
                    // Recovering from saturation up toward base equilibrium price
                    double step = Math.max(0.01, current * (decayPercent / 100.0));
                    newPrice = Math.min(base, current + step);
                } else if (current > base) {
                    // Cooling down toward base equilibrium price
                    double step = Math.max(0.01, current * (decayPercent / 100.0));
                    newPrice = Math.max(base, current - step);
                } else {
                    continue;
                }

                newPrice = Math.round(newPrice * 100.0) / 100.0;
                if (Math.abs(newPrice - current) >= 0.001) {
                    data.setCurrentPrice(newPrice);
                    data.setLastUpdate(now);
                    toSave.add(data);
                }
            }
        }

        // Batch save all updated items to prevent HikariCP connection pool exhaustion
        if (!toSave.isEmpty() && plugin.getDatabaseManager() != null) {
            TaskScheduler.runAsync(plugin, () -> plugin.getDatabaseManager().saveAllDynamicMarketItems(toSave));
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
