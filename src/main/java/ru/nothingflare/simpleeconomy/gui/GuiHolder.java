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

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class GuiHolder implements InventoryHolder {

    private Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> slotActions = new HashMap<>();
    private Consumer<InventoryClickEvent> defaultClickAction;
    private Consumer<InventoryCloseEvent> closeAction;
    private Consumer<InventoryDragEvent> dragAction;

    private boolean allowPlayerInventoryClick = false;
    private boolean allowUpperInventoryPlacement = false;
    private int maxPlaceableSlot = -1; // e.g. 44 for BuyerGui

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setAction(int slot, Consumer<InventoryClickEvent> action) {
        slotActions.put(slot, action);
    }

    public void removeAction(int slot) {
        slotActions.remove(slot);
    }

    public boolean hasAction(int slot) {
        return slotActions.containsKey(slot);
    }

    public void setDefaultClickAction(Consumer<InventoryClickEvent> action) {
        this.defaultClickAction = action;
    }

    public void setCloseAction(Consumer<InventoryCloseEvent> action) {
        this.closeAction = action;
    }

    public void setDragAction(Consumer<InventoryDragEvent> action) {
        this.dragAction = action;
    }

    public void setAllowPlayerInventoryClick(boolean allow) {
        this.allowPlayerInventoryClick = allow;
    }

    public boolean isAllowPlayerInventoryClick() {
        return allowPlayerInventoryClick;
    }

    public void setAllowUpperInventoryPlacement(boolean allow) {
        this.allowUpperInventoryPlacement = allow;
    }

    public boolean isAllowUpperInventoryPlacement() {
        return allowUpperInventoryPlacement;
    }

    public void setMaxPlaceableSlot(int maxSlot) {
        this.maxPlaceableSlot = maxSlot;
    }

    public int getMaxPlaceableSlot() {
        return maxPlaceableSlot;
    }

    public void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = slotActions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        } else if (defaultClickAction != null) {
            defaultClickAction.accept(event);
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        if (closeAction != null) {
            closeAction.accept(event);
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        if (dragAction != null) {
            dragAction.accept(event);
        }
    }
}
