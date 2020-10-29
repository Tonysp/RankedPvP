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
import java.util.Set;
import java.util.logging.Level;

public class RankedPvP extends JavaPlugin {

    public static boolean IS_MASTER = false;
    public static final String PLUGIN_ID = "rankedpvp";
    public static String MASTER_ID;
    public static Set<String> otherServers = new HashSet<>();

    public static RankedPvP getInstance () {
        return getPlugin(RankedPvP.class);
    }

    @Override
    public void onEnable () {
        loadConfig();

        Database.initializeTables();

        Messages.loadFromConfig(getConfig());
        EventType.loadSettings(getConfig());
        ArenaManager.initialize(getInstance(), getConfig());
        PlayerManager.initialize(getInstance());
        GameManager.initialize(getInstance());

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        new PlayerCommandPreprocessListener();
        this.getCommand("pvp").setExecutor(new PvPCommand());

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new Placeholders(this).register();
            log("PlaceholderAPI integration enabled.");
        } else {
            log("PlaceholderAPI integration disabled.");
        }
        DataPacketProcessor.getInstance();
    }

    @Override
    public void onDisable () {
        GameManager.getInstance().endAllGames();
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

        IS_MASTER = getConfig().getBoolean("master");
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
