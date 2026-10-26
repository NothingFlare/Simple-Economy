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

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0.##");

    private static String currencySymbol = "$";
    private static String currencyFormat = "%amount%%symbol%";

    private MessageUtil() {}

    public static void setCurrencyConfig(String symbol, String format) {
        if (symbol != null) {
            currencySymbol = symbol;
        }
        if (format != null) {
            currencyFormat = format;
        }
    }

    public static String getCurrencySymbol() {
        return currencySymbol;
    }

    public static String getCurrencyFormat() {
        return currencyFormat;
    }

    public static String color(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hexCode = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hexCode).toString());
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static List<String> color(List<String> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        List<String> colored = new ArrayList<>(list.size());
        for (String line : list) {
            colored.add(color(line));
        }
        return colored;
    }

    public static String replace(String message, Map<String, String> placeholders) {
        if (message == null) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return message;
        }
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    public static List<String> replace(List<String> list, Map<String, String> placeholders) {
        if (list == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(list.size());
        for (String line : list) {
            result.add(replace(line, placeholders));
        }
        return result;
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isEmpty()) {
            sender.sendMessage(color(message));
        }
    }

    public static String formatCurrency(double amount) {
        String numStr = CURRENCY_FORMAT.format(amount);
        return currencyFormat
                .replace("%amount%", numStr)
                .replace("%symbol%", currencySymbol);
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        }
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }
}
