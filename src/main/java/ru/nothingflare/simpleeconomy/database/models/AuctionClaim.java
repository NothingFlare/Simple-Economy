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

package ru.nothingflare.simpleeconomy.database.models;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionClaim {

    private final long id;
    private final UUID playerUuid;
    private final ItemStack item;
    private final String reason; // BOUGHT, CANCELLED, EXPIRED
    private final long createdAt;

    public AuctionClaim(long id, UUID playerUuid, ItemStack item, String reason, long createdAt) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.item = item;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public ItemStack getItem() {
        return item != null ? item.clone() : null;
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
