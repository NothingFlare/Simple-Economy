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

package ru.nothingflare.simpleeconomy.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class TaskScheduler {

    private static final boolean IS_FOLIA;
    private static Method getAsyncSchedulerMethod;
    private static Method runNowMethod;
    private static Method runAtFixedRateMethod;

    private static Method getGlobalRegionSchedulerMethod;
    private static Method runGlobalMethod;

    private static Method getEntitySchedulerMethod;
    private static Method runEntityMethod;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;

        if (IS_FOLIA) {
            try {
                getAsyncSchedulerMethod = Bukkit.class.getMethod("getAsyncScheduler");
                Class<?> asyncSchedulerClass = getAsyncSchedulerMethod.getReturnType();
                runNowMethod = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);
                runAtFixedRateMethod = asyncSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

                getGlobalRegionSchedulerMethod = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Class<?> globalSchedulerClass = getGlobalRegionSchedulerMethod.getReturnType();
                runGlobalMethod = globalSchedulerClass.getMethod("run", Plugin.class, Consumer.class);

                getEntitySchedulerMethod = Entity.class.getMethod("getScheduler");
                Class<?> entitySchedulerClass = getEntitySchedulerMethod.getReturnType();
                runEntityMethod = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            } catch (Throwable t) {
                // Ignore fallback to standard handling if reflection differs
            }
        }
    }

    private TaskScheduler() {}

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static void runAsync(Plugin plugin, Runnable runnable) {
        if (IS_FOLIA && getAsyncSchedulerMethod != null && runNowMethod != null) {
            try {
                Object asyncScheduler = getAsyncSchedulerMethod.invoke(null);
                Consumer<Object> consumer = task -> runnable.run();
                runNowMethod.invoke(asyncScheduler, plugin, consumer);
                return;
            } catch (Throwable ignored) {}
            new Thread(runnable, "SimpleEconomy-AsyncFallback").start();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    public static void runSync(Plugin plugin, Runnable runnable) {
        if (IS_FOLIA && getGlobalRegionSchedulerMethod != null && runGlobalMethod != null) {
            try {
                Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(null);
                Consumer<Object> consumer = task -> runnable.run();
                runGlobalMethod.invoke(globalScheduler, plugin, consumer);
                return;
            } catch (Throwable ignored) {}
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runEntity(Plugin plugin, Entity entity, Runnable runnable) {
        if (entity == null) {
            runSync(plugin, runnable);
            return;
        }
        if (IS_FOLIA && getEntitySchedulerMethod != null && runEntityMethod != null) {
            try {
                Object entityScheduler = getEntitySchedulerMethod.invoke(entity);
                Consumer<Object> consumer = task -> runnable.run();
                runEntityMethod.invoke(entityScheduler, plugin, consumer, null);
                return;
            } catch (Throwable ignored) {}
            runSync(plugin, runnable);
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runTimerAsync(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        if (IS_FOLIA && getAsyncSchedulerMethod != null && runAtFixedRateMethod != null) {
            try {
                Object asyncScheduler = getAsyncSchedulerMethod.invoke(null);
                Consumer<Object> consumer = task -> runnable.run();
                long delayMillis = delayTicks * 50L;
                long periodMillis = periodTicks * 50L;
                runAtFixedRateMethod.invoke(asyncScheduler, plugin, consumer, delayMillis, periodMillis, TimeUnit.MILLISECONDS);
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to schedule async timer on Folia: " + t.getMessage());
            }
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
        }
    }
}
