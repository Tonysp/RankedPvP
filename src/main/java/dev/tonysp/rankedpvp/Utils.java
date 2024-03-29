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

package dev.tonysp.rankedpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public static String formatString (String text){
        Pattern pattern = Pattern.compile("#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String hexCode = text.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace('#', 'x');

            char[] ch = replaceSharp.toCharArray();
            StringBuilder builder = new StringBuilder();
            for (char c : ch) {
                builder.append("&").append(c);
            }

            text = text.replace(hexCode, builder.toString());
            matcher = pattern.matcher(text);
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static TextComponent remainingString (int amount) {
        if (amount >= 5 || amount == 0) {
            return Messages.FIVE_OR_MORE_REMAINING.getMessage();
        } else if (amount >= 2) {
            return Messages.TWO_TO_FOUR_REMAINING.getMessage();
        } else {
            return Messages.ONE_REMAINING.getMessage();
        }
    }

    public static TextComponent secondString (int seconds) {
        if (seconds >= 5 || seconds == 0) {
            return Component.text(seconds).append(Component.text(" ")).append(Messages.FIVE_OR_MORE_SECONDS.getMessage());
        } else if (seconds >= 2) {
            return Component.text(seconds).append(Component.text(" ")).append(Messages.TWO_TO_FOUR_SECONDS.getMessage());
        } else {
            return Component.text(seconds).append(Component.text(" ")).append(Messages.ONE_SECOND.getMessage());
        }
    }

    public static TextComponent minutesString (int minutes) {
        if (minutes >= 5 || minutes == 0) {
            return Component.text(minutes).append(Component.text(" ")).append(Messages.FIVE_OR_MORE_MINUTES.getMessage());
        } else if (minutes >= 2) {
            return Component.text(minutes).append(Component.text(" ")).append(Messages.TWO_TO_FOUR_MINUTES.getMessage());
        } else {
            return Component.text(minutes).append(Component.text(" ")).append(Messages.ONE_MINUTE.getMessage());
        }
    }

    public static TextComponent playersString (int amount) {
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
            World world;

            float yaw = 0, pitch = 0;
            if (parts.length >= 6) {
                yaw = Float.parseFloat(parts[3]);
                pitch = Float.parseFloat(parts[4]);
                world = Bukkit.getWorld(parts[5]);
            } else {
                world = Bukkit.getWorld(parts[3]);
            }

            if (world == null) {
                return Optional.empty();
            }

            location = new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), yaw, pitch);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }

        return Optional.of(location);
    }
}
