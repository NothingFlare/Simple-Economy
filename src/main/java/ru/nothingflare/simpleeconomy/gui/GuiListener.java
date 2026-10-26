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

package ru.nothingflare.simpleeconomy.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GuiListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof GuiHolder)) {
            return;
        }

        GuiHolder holder = (GuiHolder) inv.getHolder();
        int topSize = inv.getSize();
        int rawSlot = event.getRawSlot();
        ClickType click = event.getClick();

        // 1. Prevent hotbar number swap & offhand swap into locked slots
        if (click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND) {
            if (rawSlot < topSize) {
                if (!holder.isAllowUpperInventoryPlacement() ||
                        (holder.getMaxPlaceableSlot() >= 0 && rawSlot > holder.getMaxPlaceableSlot()) ||
                        holder.hasAction(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // 2. Prevent double-click collection from locked upper inventory
        if (click == ClickType.DOUBLE_CLICK) {
            if (!holder.isAllowUpperInventoryPlacement()) {
                event.setCancelled(true);
                return;
            }
        }

        // 3. Handle player inventory clicks (bottom inventory)
        if (rawSlot >= topSize) {
            if (!holder.isAllowPlayerInventoryClick()) {
                event.setCancelled(true);
                return;
            }

            // If player is shift-clicking into top inventory
            if (event.isShiftClick()) {
                if (!holder.isAllowUpperInventoryPlacement()) {
                    event.setCancelled(true);
                    return;
                }

                // If upper inventory has restricted placeable slots (like BuyerGui slots 0-44)
                if (holder.getMaxPlaceableSlot() >= 0) {
                    event.setCancelled(true);
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                        handleSafeShiftClick(inv, clickedItem, holder.getMaxPlaceableSlot());
                        if (event.getWhoClicked() instanceof Player) {
                            ((Player) event.getWhoClicked()).updateInventory();
                        }
                    }
                    return;
                }
            }
            return;
        }

        // 4. Handle clicks inside the custom GUI (top inventory)
        if (rawSlot >= 0) {
            // Check if slot is beyond placeable range or has an action
            boolean isPlaceable = holder.isAllowUpperInventoryPlacement() &&
                    (holder.getMaxPlaceableSlot() < 0 || rawSlot <= holder.getMaxPlaceableSlot()) &&
                    !holder.hasAction(rawSlot);

            if (!isPlaceable) {
                event.setCancelled(true);
            }

            holder.handleClick(event);
        }
    }

    private void handleSafeShiftClick(Inventory inv, ItemStack item, int maxSlot) {
        int remaining = item.getAmount();
        int maxStack = item.getMaxStackSize();

        // Pass 1: Stacking into existing similar items
        for (int i = 0; i <= maxSlot && remaining > 0; i++) {
            ItemStack slotItem = inv.getItem(i);
            if (slotItem != null && slotItem.isSimilar(item)) {
                int space = maxStack - slotItem.getAmount();
                if (space > 0) {
                    int toAdd = Math.min(space, remaining);
                    slotItem.setAmount(slotItem.getAmount() + toAdd);
                    remaining -= toAdd;
                }
            }
        }

        // Pass 2: Inserting into empty slots
        for (int i = 0; i <= maxSlot && remaining > 0; i++) {
            ItemStack slotItem = inv.getItem(i);
            if (slotItem == null || slotItem.getType() == Material.AIR) {
                int toAdd = Math.min(maxStack, remaining);
                ItemStack newItem = item.clone();
                newItem.setAmount(toAdd);
                inv.setItem(i, newItem);
                remaining -= toAdd;
            }
        }

        if (remaining <= 0) {
            item.setAmount(0);
            item.setType(Material.AIR);
        } else {
            item.setAmount(remaining);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof GuiHolder)) {
            return;
        }

        GuiHolder holder = (GuiHolder) inv.getHolder();
        int topSize = inv.getSize();

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                // If dragging into top inventory
                if (!holder.isAllowUpperInventoryPlacement() ||
                        (holder.getMaxPlaceableSlot() >= 0 && rawSlot > holder.getMaxPlaceableSlot()) ||
                        holder.hasAction(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        holder.handleDrag(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof GuiHolder) {
            GuiHolder holder = (GuiHolder) inv.getHolder();
            holder.handleClose(event);
        }
    }
}
