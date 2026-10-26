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

public class AuctionListing {

    private final long id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long createdAt;
    private final long publishedAt;
    private final String status; // ACTIVE, SOLD, CANCELLED, EXPIRED

    public AuctionListing(long id, UUID sellerUuid, String sellerName, ItemStack item, double price, long createdAt, long publishedAt, String status) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
        this.status = status;
    }

    public AuctionListing(long id, UUID sellerUuid, String sellerName, ItemStack item, double price, long createdAt, String status) {
        this(id, sellerUuid, sellerName, item, price, createdAt, createdAt, status);
    }

    public long getId() {
        return id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item != null ? item.clone() : null;
    }

    public double getPrice() {
        return price;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getPublishedAt() {
        return publishedAt;
    }

    public boolean isPublished() {
        return System.currentTimeMillis() >= publishedAt;
    }

    public long getSecondsUntilPublish() {
        return Math.max(0, (publishedAt - System.currentTimeMillis() + 999) / 1000);
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean isPending() {
        return "ACTIVE".equalsIgnoreCase(status) && !isPublished();
    }
}
