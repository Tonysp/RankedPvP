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

package dev.tonysp.rankedpvp;

import dev.tonysp.rankedpvp.arenas.ArenaManager;
import dev.tonysp.rankedpvp.commands.PlayerCommandPreprocessListener;
import dev.tonysp.rankedpvp.commands.PvPCommand;
import dev.tonysp.rankedpvp.data.DataPacketProcessor;
import dev.tonysp.rankedpvp.data.Database;
import dev.tonysp.rankedpvp.game.EventType;
import dev.tonysp.rankedpvp.game.GameManager;
import dev.tonysp.rankedpvp.players.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

public class RankedPvP extends JavaPlugin {

    public static boolean IS_MASTER = true;
    public static final String PLUGIN_ID = "rankedpvp";
    public static String MASTER_ID;
    public static Set<String> otherServers = new HashSet<>();

    public static RankedPvP getInstance () {
        return getPlugin(RankedPvP.class);
    }

    @Override
    public void onEnable () {
        loadConfig();

        Database.getInstance().initializeTables();

        Messages.loadFromConfig(getConfig());
        EventType.loadFromConfig(getConfig());
        ArenaManager.getInstance();
        PlayerManager.getInstance();
        GameManager.getInstance();

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        new PlayerCommandPreprocessListener();

        Objects.requireNonNull(getCommand("rankedpvp")).setExecutor(new PvPCommand());

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new Placeholders(this).register();
            log("PlaceholderAPI integration enabled.");
        } else {
            log("PlaceholderAPI integration disabled.");
        }
        DataPacketProcessor.getInstance().shareServerOnline();
        Database.getInstance();
    }

    @Override
    public void onDisable () {
        GameManager.getInstance().endAllGames();
        DataPacketProcessor.getInstance().onDisable();
    }

    private void loadConfig () {
        if (!(new File(getDataFolder() + File.separator + "config.yml").exists())) {
            saveDefaultConfig();
        }

        try {
            new YamlConfiguration().load(new File(getDataFolder() + File.separator + "config.yml"));
        } catch (Exception e) {
            System.out.println("There was a problem loading the config. More details bellow.");
            System.out.println("-----------------------------------------------");
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        reloadConfig();
    }

    public static void log (String text) {
        Bukkit.getLogger().log(Level.INFO, "[RankedPvP] " + text);
    }

    public static void logWarning (String text) {
        Bukkit.getLogger().log(Level.WARNING, "[RankedPvP] " + text);
    }

    public void addServer (String serverId) {
        otherServers.add(serverId);
    }
}
