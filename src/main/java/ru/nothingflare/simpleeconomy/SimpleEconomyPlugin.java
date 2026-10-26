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

package ru.nothingflare.simpleeconomy;

import org.bukkit.plugin.java.JavaPlugin;
import ru.nothingflare.simpleeconomy.commands.AhCommand;
import ru.nothingflare.simpleeconomy.commands.BuyerCommand;
import ru.nothingflare.simpleeconomy.commands.ShopCommand;
import ru.nothingflare.simpleeconomy.commands.SimpleEconomyCommand;
import ru.nothingflare.simpleeconomy.config.ConfigManager;
import ru.nothingflare.simpleeconomy.config.LanguageManager;
import ru.nothingflare.simpleeconomy.database.DatabaseManager;
import ru.nothingflare.simpleeconomy.economy.EconomyHook;
import ru.nothingflare.simpleeconomy.gui.GuiListener;
import ru.nothingflare.simpleeconomy.market.DynamicMarketManager;
import ru.nothingflare.simpleeconomy.scheduler.TaskScheduler;
import ru.nothingflare.simpleeconomy.util.SoundUtil;

public class SimpleEconomyPlugin extends JavaPlugin {

    private static SimpleEconomyPlugin instance;

    private ConfigManager configManager;
    private LanguageManager languageManager;
    private DatabaseManager databaseManager;
    private EconomyHook economyHook;
    private DynamicMarketManager dynamicMarketManager;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Initialize Economy (Vault)
        this.economyHook = new EconomyHook(this);
        if (!economyHook.setup()) {
            getLogger().severe("Disabling SimpleEconomy: Vault or Economy plugin is missing!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Load Config & Languages
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        this.configManager.load();
        this.languageManager.load();

        // 3. Initialize Database
        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.init()) {
            getLogger().severe("Disabling SimpleEconomy: Could not initialize database!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Reload buyer prices from DB cache
        this.configManager.load();

        // 4. Initialize Dynamic Market
        this.dynamicMarketManager = new DynamicMarketManager(this);
        this.dynamicMarketManager.load();

        // Configure GUI sounds
        SoundUtil.setEnabled(getConfig().getBoolean("sounds.enabled", true));

        // 4. Register GUI Events
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        // 5. Register Commands and TabCompleters
        if (getCommand("ah") != null) {
            AhCommand ahCommand = new AhCommand(this);
            getCommand("ah").setExecutor(ahCommand);
            getCommand("ah").setTabCompleter(ahCommand);
        }
        if (getCommand("buyer") != null) {
            getCommand("buyer").setExecutor(new BuyerCommand(this));
        }
        if (getCommand("shop") != null) {
            getCommand("shop").setExecutor(new ShopCommand(this));
        }
        if (getCommand("simpleeconomy") != null) {
            SimpleEconomyCommand seCommand = new SimpleEconomyCommand(this);
            getCommand("simpleeconomy").setExecutor(seCommand);
            getCommand("simpleeconomy").setTabCompleter(seCommand);
        }

        // 6. Schedule Auction Expiration Check (Runs every 60 seconds async)
        long expireCheckIntervalTicks = 20L * 60L;
        TaskScheduler.runTimerAsync(this, () -> {
            if (configManager.isAuctionEnabled() && databaseManager != null) {
                long maxAgeMillis = (long) configManager.getExpireHours() * 3600000L;
                databaseManager.processExpiredListings(maxAgeMillis);
            }
        }, 100L, expireCheckIntervalTicks);

        // 7. Print Console Banner
        printBanner();
    }

    private void printBanner() {
        String platform = "Spigot";
        if (TaskScheduler.isFolia()) {
            platform = "Folia";
        } else {
            try {
                Class.forName("com.destroystokyo.paper.PaperConfig");
                platform = "Paper";
            } catch (ClassNotFoundException e) {
                try {
                    Class.forName("io.papermc.paper.configuration.Configuration");
                    platform = "Paper";
                } catch (ClassNotFoundException ignored) {}
            }
        }

        String curLang = languageManager != null ? languageManager.getCurrentLang() : "ru";

        getLogger().info("Loaded language: " + curLang + " (" + curLang + ".yml)");
        getLogger().info("");
        getLogger().info("  ______  _                _        ______                                          ");
        getLogger().info(" / _____|(_)              | |      |  ____|                                         ");
        getLogger().info("| (_____  _  ____   ____  | | ____ | |__     ___  ___    ____   ___    ____   _   _ ");
        getLogger().info(" \\_____ \\| ||    \\ |  _ \\ | |/ _  )|  __|   / __)/ _ \\  |  _ \\ / _ \\  |    \\ | | | |");
        getLogger().info(" _____) )| || | | || |_) )| |( (/  | |____ ( (__ | |_| || | | || |_| || | | || |_| |");
        getLogger().info("(______/ |_||_|_|_|| ||_/ |_|\\____)|______| \\___) \\___/ |_| |_| \\___/ |_|_|_| \\__  |");
        getLogger().info("                   |_|                                                        (____/");
        getLogger().info("");
        getLogger().info("Привет, мир!");
        getLogger().info("Наш Telegram канал!");
        getLogger().info("t.me/lighthouse_top");
        getLogger().info("Версия Simple Economy: " + getDescription().getVersion());
        getLogger().info("Язык: " + curLang.toUpperCase());
        getLogger().info("Simple Economy запущен на " + platform);
        getLogger().info("Simple Economy успешно включен!");
        getLogger().info("");
    }

    @Override
    public void onDisable() {
        if (dynamicMarketManager != null) {
            dynamicMarketManager.saveAll();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("Simple Economy has been safely disabled.");
    }

    public static SimpleEconomyPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EconomyHook getEconomyHook() {
        return economyHook;
    }

    public DynamicMarketManager getDynamicMarketManager() {
        return dynamicMarketManager;
    }
}
