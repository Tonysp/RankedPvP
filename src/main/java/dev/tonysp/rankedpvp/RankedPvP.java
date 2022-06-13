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

import dev.tonysp.rankedpvp.arenas.ArenaManager;
import dev.tonysp.rankedpvp.commands.PlayerCommandPreprocessListener;
import dev.tonysp.rankedpvp.commands.PvPCommand;
import dev.tonysp.rankedpvp.data.DataPacketManager;
import dev.tonysp.rankedpvp.data.DatabaseManager;
import dev.tonysp.rankedpvp.game.EventType;
import dev.tonysp.rankedpvp.game.GameManager;
import dev.tonysp.rankedpvp.players.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;

public class RankedPvP extends JavaPlugin {

    private final PlayerManager playerManager = new PlayerManager(this);
    private final GameManager gameManager = new GameManager(this);
    private final ArenaManager arenaManager = new ArenaManager(this);
    private final DataPacketManager dataPacketManager = new DataPacketManager(this);
    private final DatabaseManager databaseManager = new DatabaseManager(this);

    public static RankedPvP getInstance () {
        return getPlugin(RankedPvP.class);
    }

    @Override
    public void onEnable () {
        enable();
    }

    @Override
    public void onDisable () {
        disable();
    }

    public String enable () {
        loadConfig();
        String failed = ChatColor.RED + "Plugin failed to enable, check console!";

        PvPCommand pvpCommand = new PvPCommand(this);
        Objects.requireNonNull(getCommand("rankedpvp")).setExecutor(pvpCommand);
        Objects.requireNonNull(getCommand("pvp")).setExecutor(pvpCommand);

        if (!database().load())
            return failed + " (database)";

        database().initializeTables();

        Messages.loadFromConfig(getConfig());
        EventType.loadFromConfig(getConfig());
        if (!arenas().load())
            return failed + " (arenas)";
        if (!players().load())
            return failed + " (players)";
        if (!games().load())
            return failed + " (games)";
        if (!dataPackets().load())
            return failed + " (data packets)";

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        new PlayerCommandPreprocessListener();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new Placeholders(this).register();
            log("PlaceholderAPI integration enabled.");
        } else {
            log("PlaceholderAPI integration disabled.");
        }
        dataPacketManager.shareServerOnline();

        return ChatColor.GREEN + "Plugin enabled!";
    }

    public String disable () {
        gameManager.endAllGames();
        dataPacketManager.unload();

        players().unload();
        games().unload();
        arenas().unload();
        database().unload();

        return ChatColor.GREEN + "Plugin disabled!";
    }

    private void loadConfig () {
        if (!(new File(getDataFolder() + File.separator + "config.yml").exists())) {
            saveDefaultConfig();
        }

        try {
            new YamlConfiguration().load(new File(getDataFolder() + File.separator + "config.yml"));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "There was a problem loading the config. More details bellow.");
            getLogger().log(Level.SEVERE, "-----------------------------------------------");
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

    public static void logDebug (String text) {
        if (getInstance().getConfig().getBoolean("debug", false))
            Bukkit.getLogger().log(Level.INFO, "[RankedPvP Debug] " + text);
    }

    public PlayerManager players () {
        return playerManager;
    }

    public GameManager games () {
        return gameManager;
    }

    public ArenaManager arenas () {
        return arenaManager;
    }

    public DataPacketManager dataPackets () {
        return dataPacketManager;
    }

    public DatabaseManager database () {
        return databaseManager;
    }
}
