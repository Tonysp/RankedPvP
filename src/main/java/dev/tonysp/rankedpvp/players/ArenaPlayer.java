/*
 *
 *  * This file is part of RankedPvP, licensed under the MIT License.
 *  *
 *  *  Copyright (c) 2020 Antonín Sůva
 *  *
 *  *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  *  of this software and associated documentation files (the "Software"), to deal
 *  *  in the Software without restriction, including without limitation the rights
 *  *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  *  copies of the Software, and to permit persons to whom the Software is
 *  *  furnished to do so, subject to the following conditions:
 *  *
 *  *  The above copyright notice and this permission notice shall be included in all
 *  *  copies or substantial portions of the Software.
 *  *
 *  *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  *  SOFTWARE.
 *
 */

package dev.tonysp.rankedpvp.players;

import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.game.MatchResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class ArenaPlayer extends EntityWithRating implements Comparable<EntityWithRating>, Serializable {

    private String name;
    private UUID uuid;
    private transient ItemStack[] inventoryBackup, arenaInventoryBackup;
    private transient Collection<PotionEffect> statusEffectBackup, arenaStatusEffectBackup;
    private int timeInQueue = 0;
    private final List<MatchResult> matchHistory = new ArrayList<>();
    private double visibleRatingCoefficient = 1;

    public ArenaPlayer (UUID uuid) {
        super();
        this.uuid = uuid;
        this.name = Bukkit.getOfflinePlayer(uuid).getName();
        recalculateVisibleRatingCoefficient();
    }

    public ArenaPlayer (UUID uuid, double rating, double deviation) {
        super(rating, deviation);
        this.uuid = uuid;
        this.name = Bukkit.getOfflinePlayer(uuid).getName();
        recalculateVisibleRatingCoefficient();
    }

    public String getName () {
        return name;
    }

    public void setName (String name) {
        this.name = name;
    }

    public UUID getUuid () {
        return uuid;
    }

    public String getNameWithRating () {
        return name + " " + getRatingColored();
    }

    public String getRatingColored () {
        return PlayerManager.getInstance().getPlayerRating(getRatingVisible(), getMatches());
    }

    public void recalculateVisibleRatingCoefficient () {
        if (matchHistory.size() == 0) {
            return;
        }
        double averageQuality = matchHistory.stream().mapToDouble(MatchResult::calculateMatchQuality).sum() / matchHistory.size();
        double coefficient = Math.pow(averageQuality, 2.0) * (6.0 + 2.0/3.0);
        if (coefficient > 1) {
            coefficient = 1;
        }

        //RankedPvP.log(name + " amq: " + averageQuality + ", coeff: " + coefficient + ", total: " + (int) (getRating() * coefficient) + ", size: " + matchHistory.size());
        visibleRatingCoefficient = coefficient;
    }

    @Override
    public int getRatingVisible () {
        return (int) (getRating() * visibleRatingCoefficient);
    }

    public void backupInventory (boolean arena) {
        Player player = Bukkit.getPlayer(getName());
        if (player == null || !player.isOnline())
            return;

        if (arena) {
            arenaInventoryBackup = player.getInventory().getContents();
        } else {
            inventoryBackup = player.getInventory().getContents();
        }
        player.getInventory().clear();
    }

    public void backupStatusEffects (boolean arena) {
        Player player = Bukkit.getPlayer(getName());
        if (player == null || !player.isOnline())
            return;

        if (arena) {
            arenaStatusEffectBackup = player.getActivePotionEffects();
            arenaStatusEffectBackup.forEach(potionEffect -> player.removePotionEffect(potionEffect.getType()));
        } else {
            statusEffectBackup = player.getActivePotionEffects();
            statusEffectBackup.forEach(potionEffect -> player.removePotionEffect(potionEffect.getType()));
        }
    }

    public void restoreInventory (boolean arena) {
        Player player = Bukkit.getPlayer(getName());
        if (player == null || !player.isOnline())
            return;

        if (arena && arenaInventoryBackup != null) {
            player.getInventory().clear();
            player.getInventory().setContents(arenaInventoryBackup);
            arenaInventoryBackup = null;
        } else if (!arena && inventoryBackup != null) {
            player.getInventory().clear();
            player.getInventory().setContents(inventoryBackup);
            inventoryBackup = null;
        }
    }

    public void restoreStatusEffects (boolean arena) {
        Player player = Bukkit.getPlayer(getName());
        if (player == null || !player.isOnline())
            return;

        if (arena && arenaStatusEffectBackup != null) {
            for (PotionEffectType potionEffectType : PotionEffectType.values()) {
                player.removePotionEffect(potionEffectType);
            }
            player.addPotionEffects(arenaStatusEffectBackup);
            arenaStatusEffectBackup = null;
        } else if (!arena && statusEffectBackup != null) {
            for (PotionEffectType potionEffectType : PotionEffectType.values()) {
                player.removePotionEffect(potionEffectType);
            }
            player.addPotionEffects(statusEffectBackup);
            statusEffectBackup = null;
        }
    }

    public void incrementTimeInQueue () {
        timeInQueue++;
    }

    public int getTimeInQueue () {
        return this.timeInQueue;
    }

    public void resetTimeInQueue () {
        this.timeInQueue = 0;
    }

    public void addMatchToHistory (MatchResult matchResult) {
        matchHistory.add(matchResult);
        if (matchResult.winnerTeam == getId()) {
            wins ++;
        } else if (matchResult.isDraw()) {
            draws ++;
        } else {
            losses ++;
        }

        recalculateVisibleRatingCoefficient();
    }

    public String getNameWithRatingAndChange (double change) {
        if (PlayerManager.getInstance().ranksEnabled()) {
            return getNameWithRating() + ChatColor.RESET;
        }

        Messages message = Messages.RATING_DIFF_POSITIVE;
        if (change < 0) {
            message = Messages.RATING_DIFF_NEGATIVE;
            change *= -1.0;
        }
        String changeString;
        if (change > 2) {
            changeString = String.valueOf((int) Math.round(change));
        } else if (change <= 0.1) {
            changeString = "0.1";
        } else {
            DecimalFormat df = new DecimalFormat("0.#");
            changeString = df.format(change);
        }

        return getNameWithRating() + message.getMessage().replaceAll("%AMOUNT%", changeString) + ChatColor.RESET;
    }

    public void heal () {
        Player player = Bukkit.getPlayer(getName());
        if (player == null)
            return;
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute != null)
            player.setHealth(attribute.getDefaultValue());
    }
}

