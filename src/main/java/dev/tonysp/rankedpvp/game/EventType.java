/*
 *
 *  * This file is part of RankedPvP, licensed under the MIT License.
 *  *
 *  *  Copyright (c) 2022 Antonín Sůva
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

package dev.tonysp.rankedpvp.game;

import dev.tonysp.rankedpvp.players.ArenaPlayer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public enum EventType {
    ONE_VS_ONE,
    ;

    private final Map<ArenaPlayer, Integer> cooldowns = new HashMap<>();

    private int cooldown = 0;
    private boolean backupInventory = false;
    private boolean backupStatusEffects = false;
    private List<String> startCommands = new ArrayList<>();

    public static void loadFromConfig (FileConfiguration config) {
        ConfigurationSection eventSettings = config.getConfigurationSection("event-settings");
        if (eventSettings == null)
            return;

        for (String eventTypeString : eventSettings.getKeys(false)) {
            Optional<EventType> eventType = EventType.fromString(eventTypeString);
            if (eventType.isEmpty())
                continue;

            String keyPrefix = "event-settings." + eventTypeString + ".";
            eventType.get().cooldown = config.getInt(keyPrefix + "cooldown", 0);
            eventType.get().backupInventory = config.getBoolean(keyPrefix + "backup-inventory", false);
            eventType.get().backupStatusEffects = config.getBoolean(keyPrefix + "backup-status-effects", false);
            eventType.get().startCommands = config.getStringList(keyPrefix + "start-commands");
        }
    }

    public static Optional<EventType> fromString (String eventName) {
        if (eventName.equalsIgnoreCase("1v1")
                || eventName.equalsIgnoreCase("1vs1")) {
            return Optional.of(ONE_VS_ONE);
        }

        return Optional.empty();
    }

    public String getNiceName () {
        if (this == ONE_VS_ONE) {
            return "1v1";
        } else {
            return "";
        }
    }

    public void runStartCommands (String playerName) {
        for (String command : startCommands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replaceAll("%PLAYER%", playerName));
        }
    }

    public boolean isBackupInventory () {
        return backupInventory;
    }

    public boolean isBackupStatusEffects () {
        return backupStatusEffects;
    }

    public void decrementCooldowns () {
        cooldowns.entrySet().forEach(entry -> entry.setValue(entry.getValue() - 1));
    }

    public boolean isOnCooldown (ArenaPlayer player) {
        return cooldowns.containsKey(player) && cooldowns.get(player) > 0;
    }

    public int getCooldown (ArenaPlayer player) {
        return cooldowns.get(player);
    }

    public void applyCooldown (ArenaPlayer player) {
        cooldowns.put(player, cooldown);
    }
}
