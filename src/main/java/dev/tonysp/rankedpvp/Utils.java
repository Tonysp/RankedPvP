package dev.tonysp.rankedpvp;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern hexColorPattern = Pattern.compile("(\\{#[a-fA-F0-9]{6}\\})");

    public static String formatString(String text){
        Matcher hexColorMatcher = hexColorPattern.matcher(text);
        while (hexColorMatcher.find()) {
            text = text.replace(hexColorMatcher.group(1), "" + ChatColor.of(hexColorMatcher.group(1).replace("{", "").replace("}", "")));
        }

        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String secondStringRemaining (int seconds) {
        if (seconds >= 5 || seconds == 0) {
            return Messages.FIVE_OR_MORE_REMAINING.getMessage() + " " + secondString(seconds) + ".";
        } else if (seconds >= 2) {
            return Messages.TWO_TO_FOUR_REMAINING.getMessage() + " " + secondString(seconds) + ".";
        } else {
            return Messages.ONE_REMAINING.getMessage() + " " + secondString(seconds) + ".";
        }
    }

    public static String secondString (int seconds) {
        if (seconds >= 5 || seconds == 0) {
            return seconds + " " + Messages.FIVE_OR_MORE_SECONDS.getMessage();
        } else if (seconds >= 2) {
            return seconds + " " + Messages.TWO_TO_FOUR_SECONDS.getMessage();
        } else {
            return seconds + " " + Messages.ONE_SECOND.getMessage();
        }
    }

    public static String minutesString (int minutes) {
        if (minutes >= 5 || minutes == 0) {
            return minutes + " " + Messages.FIVE_OR_MORE_MINUTES.getMessage();
        } else if (minutes >= 2) {
            return minutes + " " + Messages.TWO_TO_FOUR_MINUTES.getMessage();
        } else {
            return minutes + " " + Messages.ONE_MINUTE.getMessage();
        }
    }

    public static String playersString (int amount) {
        if (amount >= 5 || amount == 0) {
            return Messages.FIVE_OR_MORE_PLAYERS.getMessage();
        } else if (amount >= 2) {
            return Messages.TWO_TO_FOUR_PLAYERS.getMessage();
        } else {
            return Messages.ONE_PLAYER.getMessage();
        }
    }

    public static Timestamp getCurrentTimeStamp() {
        java.util.Date today = new java.util.Date();
        return new java.sql.Timestamp(today.getTime());
    }

    public static Optional<Location> teleportLocationFromString (String locationString) {
        Location location;
        try {
            String[] parts = locationString.split(",");
            World world = Bukkit.getWorld(parts[5]);
            if (world == null) {
                return Optional.empty();
            }
            location = new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Float.parseFloat(parts[3]), Float.parseFloat(parts[4]));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }

        return Optional.of(location);
    }
}
