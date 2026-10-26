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

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class SoundUtil {

    private static boolean enabled = true;

    private SoundUtil() {}

    public static void setEnabled(boolean soundEnabled) {
        enabled = soundEnabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Soft, atmospheric menu opening sound.
     */
    public static void playOpen(Player player) {
        if (!enabled || player == null) return;
        playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 0.45f, 1.4f);
    }

    /**
     * Crisp, pleasant click sound for GUI buttons.
     */
    public static void playClick(Player player) {
        if (!enabled || player == null) return;
        playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    /**
     * Natural book page flip sound when navigating pages.
     */
    public static void playPageTurn(Player player) {
        if (!enabled || player == null) return;
        playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.15f);
    }

    /**
     * Shimmering chime refresh for auction house.
     */
    public static void playRefresh(Player player) {
        if (!enabled || player == null) return;
        playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.6f);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_BELL, 0.4f, 1.9f);
    }

    /**
     * Subtle, tactile click for quantity adjustments (+1, -1, +10, -10).
     */
    public static void playAdjust(Player player, boolean increase) {
        if (!enabled || player == null) return;
        float pitch = increase ? 1.55f : 1.1f;
        playSound(player, Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, pitch);
    }

    /**
     * Quick snap tone when choosing preset amounts (1 or 64).
     */
    public static void playQuantityPreset(Player player) {
        if (!enabled || player == null) return;
        playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.5f);
        playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
    }

    /**
     * Harmonious triple chord on successful purchase.
     */
    public static void playSuccess(Player player) {
        if (!enabled || player == null) return;
        playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.8f);
        playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.35f, 1.9f);
    }

    /**
     * Shimmering crystal coins sound when selling items in /buyer.
     */
    public static void playCoins(Player player) {
        if (!enabled || player == null) return;
        playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.25f);
        playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.5f);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.85f);
    }

    /**
     * Bright bell chime on listing an item on auction.
     */
    public static void playListingCreated(Player player) {
        if (!enabled || player == null) return;
        playSound(player, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.45f);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.8f);
    }

    /**
     * Clean item claim or collection sound from ender chest storage.
     */
    public static void playClaim(Player player) {
        if (!enabled || player == null) return;
        playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.35f);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_BELL, 0.4f, 1.7f);
    }

    /**
     * Gentle bass return when cancelling a listing.
     */
    public static void playCancel(Player player) {
        if (!enabled || player == null) return;
        playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f);
    }

    /**
     * Gentle bass error sound on insufficient funds or invalid action.
     */
    public static void playError(Player player) {
        if (!enabled || player == null) return;
        playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.55f);
        playSound(player, Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
    }

    private static void playSound(Player player, Sound sound, float volume, float pitch) {
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Throwable ignored) {}
    }
}
