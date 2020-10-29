package dev.tonysp.rankedpvp.game;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum EventType {
    ONE_VS_ONE,
    ;

    private boolean backupInventory = false;
    private List<String> startCommands = new ArrayList<>();

    public static void loadSettings (FileConfiguration config) {
        ConfigurationSection eventSettings = config.getConfigurationSection("event-settings");
        if (eventSettings == null)
            return;

        for (String eventTypeString : eventSettings.getKeys(false)) {
            Optional<EventType> eventType = EventType.fromString(eventTypeString);
            if (!eventType.isPresent())
                continue;

            eventType.get().backupInventory = config.getBoolean("event-settings." + eventTypeString + ".backup-inventory", false);
            eventType.get().startCommands = config.getStringList("event-settings." + eventTypeString + ".start-commands");
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
}
