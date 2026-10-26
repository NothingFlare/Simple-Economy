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

package ru.nothingflare.simpleeconomy.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import ru.nothingflare.simpleeconomy.SimpleEconomyPlugin;

public class EconomyHook {

    private final SimpleEconomyPlugin plugin;
    private Economy economy;

    public EconomyHook(SimpleEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault not found! SimpleEconomy requires Vault for economy functions.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().severe("No Vault economy provider registered! Please install an economy plugin (e.g. EssentialsX).");
            return false;
        }

        this.economy = rsp.getProvider();
        plugin.getLogger().info("Successfully hooked into Vault Economy: " + economy.getName());
        return true;
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (economy == null || player == null || amount <= 0) {
            return false;
        }
        return economy.has(player, amount);
    }

    public double getBalance(OfflinePlayer player) {
        if (economy == null || player == null) {
            return 0.0;
        }
        return economy.getBalance(player);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null || player == null || amount <= 0) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null || player == null || amount <= 0) {
            return false;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    public String format(double amount) {
        if (economy != null) {
            return economy.format(amount);
        }
        return String.format("%.2f", amount);
    }
}
