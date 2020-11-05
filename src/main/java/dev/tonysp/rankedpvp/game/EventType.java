package dev.tonysp.rankedpvp.game;

import dev.tonysp.rankedpvp.players.ArenaPlayer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public enum EventType {
    ONE_VS_ONE,
    ;

    private Map<ArenaPlayer, Integer> cooldowns = new HashMap<>();

    private int cooldown = 0;
    private boolean backupInventory = false;
    private List<String> startCommands = new ArrayList<>();

    public static void loadFromConfig (FileConfiguration config) {
        ConfigurationSection eventSettings = config.getConfigurationSection("event-settings");
        if (eventSettings == null)
            return;

        for (String eventTypeString : eventSettings.getKeys(false)) {
            Optional<EventType> eventType = EventType.fromString(eventTypeString);
            if (!eventType.isPresent())
                continue;

            String keyPrefix = "event-settings." + eventTypeString + ".";
            eventType.get().cooldown = config.getInt(keyPrefix + "cooldown", 0);
            eventType.get().backupInventory = config.getBoolean(keyPrefix + "backup-inventory", false);
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
