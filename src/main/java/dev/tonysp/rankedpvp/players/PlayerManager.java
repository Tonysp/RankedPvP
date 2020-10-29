package dev.tonysp.rankedpvp.players;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.Utils;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.data.Action;
import dev.tonysp.rankedpvp.data.DataPacket;
import dev.tonysp.rankedpvp.data.Database;
import dev.tonysp.rankedpvp.game.GameManager;
import dev.tonysp.rankedpvp.game.TwoPlayerGame;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class PlayerManager implements Listener {

    private static PlayerManager instance;

    private Map<Integer, ArenaPlayer> playersById = new HashMap<>();
    private Map<String, ArenaPlayer> players = new HashMap<>();
    private Map<ArenaPlayer, Integer> cooldowns = new HashMap<>();
    private Map<String, Warp> playersToWarp = new HashMap<>();

    private boolean ranksEnabled = false;
    private TreeMap<Integer, Rank> ranks = new TreeMap<>();

    private final int COOLDOWN = 0;
    private Sound ACCEPT_MESSAGE_SOUND = null;

    public static PlayerManager getInstance () {
        return instance;
    }

    public static void initialize (RankedPvP plugin) {
        instance = new PlayerManager();
        instance.loadRanks(plugin.getConfig());
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);

        try {
            instance.ACCEPT_MESSAGE_SOUND = Sound.valueOf(plugin.getConfig().getString("accept-sound"));
        } catch (Exception ignored) {}

        if (RankedPvP.IS_MASTER) {
            instance.players = Database.loadPlayers();
            for (ArenaPlayer arenaPlayer : instance.players.values()) {
                instance.playersById.put(arenaPlayer.getId(), arenaPlayer);
            }
        }

        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            instance.cooldowns.entrySet().forEach(e -> e.setValue(e.getValue() - 1));
        }, 1200L, 1200L);
    }

    public void loadRanks (FileConfiguration config) {
        ranksEnabled = config.getBoolean("ranks.enabled", false);
        if (ranksEnabled()) {
            RankedPvP.log("Ranks enabled!");
        } else {
            RankedPvP.log("Ranks disabled!");
            return;
        }

        Rank.UNRANKED = Rank.loadFromConfig(config.getConfigurationSection("ranks.unranked"));
        if (Rank.UNRANKED != null) {
            Rank.GAMES_TO_LOSE_UNRANKED = config.getInt("ranks.unranked.games-required-to-lose-this-rank");
        }

        int currentRank = 0;
        ConfigurationSection rankConfig = config.getConfigurationSection("ranks.rank0");
        while (rankConfig != null) {
            Rank rank = Rank.loadFromConfig(rankConfig);
            if (rank == null)
                break;
            ranks.put(rank.getKey(), rank);
            rankConfig = config.getConfigurationSection("ranks.rank" + ++currentRank);
        }
    }

    public String getPlayerRating (int rating, int gamesPlayed) {
        if (ranksEnabled()) {
            Rank rank = Rank.fromRating(rating, gamesPlayed);
            return Messages.RANK.getMessage().replaceAll("%RANK%", rank.getName());
        } else {
            return Messages.RANK.getMessage().replaceAll("%RANK%", String.valueOf(rating));
        }
    }

    public void sendAcceptMessageToPlayer (String playerName, int timeRemaining, boolean share) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null || !player.isOnline()) {
            if (share) {
                DataPacket.newBuilder()
                        .action(Action.GAME_ACCEPT_REMINDER)
                        .string(playerName)
                        .integer(timeRemaining)
                        .buildPacket()
                        .send();
            }
            return;
        }

        BaseComponent[] components = TextComponent.fromLegacyText(Messages.CLICK_TO_TELEPORT.getMessage().replaceAll("%TIME%", Utils.secondStringRemaining(timeRemaining)));
        for (BaseComponent component : components) {
            component.setClickEvent( new ClickEvent( ClickEvent.Action.RUN_COMMAND, "/pvp accept" ) );
            component.setHoverEvent( new HoverEvent( HoverEvent.Action.SHOW_TEXT, new Text( "Click!" ) ) );
        }
        player.spigot().sendMessage(components);
        if (ACCEPT_MESSAGE_SOUND != null) {
            player.playSound(player.getLocation(), ACCEPT_MESSAGE_SOUND, 1.0f, 1.0f);
        }
    }

    public void savePlayerLocationAndTeleport (String playerName, boolean share) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null || !player.isOnline()) {
            if (share) {
                DataPacket.newBuilder()
                        .action(Action.SAVE_PLAYER_LOCATION_AND_TP)
                        .string(playerName)
                        .buildPacket()
                        .send();
            }
            return;
        }

        if (RankedPvP.IS_MASTER) {
            ArenaPlayer arenaPlayer = getOrCreatePlayer(playerName);
            if (!GameManager.getInstance().getInProgress().containsKey(arenaPlayer))
                return;

            TwoPlayerGame game = (TwoPlayerGame) GameManager.getInstance().getInProgress().get(arenaPlayer);
            if (game.playerOne.equals(arenaPlayer)) {
                game.oneBackLocation = Warp.fromLocation(player.getLocation());
                game.teleportPlayerOneToLobby();
            } else {
                game.twoBackLocation = Warp.fromLocation(player.getLocation());
                game.teleportPlayerTwoToLobby();
            }
        } else {
            DataPacket.newBuilder()
                    .addReceiver(RankedPvP.MASTER_ID)
                    .action(Action.PLAYER_LOCATION)
                    .string(playerName)
                    .warp(Warp.fromLocation(player.getLocation()))
                    .buildPacket()
                    .send();
        }
    }

    public void announce (String message, String except1, String except2, boolean share) {
        RankedPvP.log(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(except1) || player.getName().equalsIgnoreCase(except2))
                continue;
            sendMessageToPlayer(player.getName(), message, false);
        }

        if (share) {
            ArrayList<String> list = new ArrayList<>();
            list.add(except1);
            list.add(except2);
            DataPacket.newBuilder()
                    .action(Action.ANNOUNCE)
                    .string(message)
                    .stringList(list)
                    .buildPacket()
                    .send();
        }
    }

    public void announce (String message, boolean share) {
        announce(message, "", "", share);
    }

    public void sendMessageToPlayer (String playerName, String message, boolean share) {
        Player player = Bukkit.getPlayer(playerName);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
            return;
        }

        if (share) {
            DataPacket.newBuilder()
                    .action(Action.PLAYER_MESSAGE)
                    .string(playerName)
                    .string2(message)
                    .buildPacket()
                    .send();
        }
    }

    public ArenaPlayer getOrCreatePlayer (String name) {
        String nameLower = name.toLowerCase();
        if (players.containsKey(nameLower)) {
            return players.get(nameLower);
        } else {
            return createPlayer(name);
        }
    }

    public Collection<ArenaPlayer> getPlayers () {
        return players.values();
    }

    public TreeMap<Integer, Rank> getRanks () {
        return ranks;
    }

    public void shareData (String serverId) {
        for (ArenaPlayer player : players.values()) {
            sharePlayer(player, serverId);
        }
    }

    public void sharePlayer (ArenaPlayer player, String serverId) {
        DataPacket.Builder builder = DataPacket.newBuilder()
                .action(Action.PLAYER_DATA)
                .player(player);
        if (!serverId.isEmpty()) {
            builder.addReceiver(serverId);
        }
        builder.buildPacket().send();
    }

    public void processData (DataPacket data) {
        ArenaPlayer arenaPlayer = data.getPlayer();
        instance.playersById.put(arenaPlayer.getId(), arenaPlayer);
        instance.players.put(arenaPlayer.getName().toLowerCase(), arenaPlayer);
    }

    private ArenaPlayer createPlayer (String name) {
        ArenaPlayer player = new ArenaPlayer(name);
        if (RankedPvP.IS_MASTER) {
            Database.insertPlayer(player);
        }
        String nameLower = name.toLowerCase();
        playersById.put(player.getId(), player);
        players.put(nameLower, player);

        return player;
    }

    public Optional<ArenaPlayer> getPlayerIfExists (String name) {
        if (players.containsKey(name.toLowerCase())) {
            return Optional.of(players.get(name.toLowerCase()));
        } else {
            return Optional.empty();
        }
    }

    public Optional<ArenaPlayer> getPlayerIfExists (int id) {
        if (playersById.containsKey(id)) {
            return Optional.of(playersById.get(id));
        } else {
            return Optional.empty();
        }
    }

    public TreeSet<ArenaPlayer> getTopPlayers () {
        TreeSet<ArenaPlayer> set = new TreeSet<>(players.values());
        if (ranksEnabled() && Rank.UNRANKED != null) {
            set.removeIf(player -> player.getMatches() < Rank.GAMES_TO_LOSE_UNRANKED);
        }
        return (TreeSet<ArenaPlayer>)set.descendingSet();
    }

    public Optional<ArenaPlayer> getPlayerByRank (int rank) {
        TreeSet<ArenaPlayer> players = PlayerManager.getInstance().getTopPlayers();
        Iterator<ArenaPlayer> it = players.iterator();
        int i = 0;
        ArenaPlayer current = null;
        while(it.hasNext() && i <= rank) {
            current = it.next();
            i++;
        }

        if (i == rank + 1) {
            return Optional.of(current);
        } else {
            return Optional.empty();
        }
    }

    public boolean isOnCooldown (ArenaPlayer player) {
        return cooldowns.containsKey(player) && cooldowns.get(player) > 0;
    }

    public int getCooldown (ArenaPlayer player) {
        return cooldowns.get(player);
    }

    public void applyCooldown (ArenaPlayer player) {
        cooldowns.put(player, COOLDOWN);
    }

    public void addPlayerToWarp (String playerName, Warp warp) {
        playersToWarp.put(playerName, warp);
    }

    public boolean ranksEnabled () {
        return ranksEnabled;
    }

    public void switchServer (Player player, String destinationServer) {
        final ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(destinationServer);
        player.sendPluginMessage(RankedPvP.getInstance(), "BungeeCord", out.toByteArray());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoinEvent (PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName();
        if (playersToWarp.containsKey(playerName)) {
            playersToWarp.get(playerName).warpPlayer(playerName, false);
            playersToWarp.remove(playerName);
        } else {
            Optional<ArenaPlayer> player = PlayerManager.getInstance().getPlayerIfExists(event.getPlayer().getName());
            if (!player.isPresent() || !GameManager.getInstance().getInProgress().containsKey(player.get()))
                return;

            player.get().backupInventory();
            player.get().restoreArenaEquip();

            TwoPlayerGame game = (TwoPlayerGame) GameManager.getInstance().getInProgress().get(player.get());
            if (!game.getArena().isInArena(player.get())) {
                if (game.playerOne.equals(player.get())) {
                    game.teleportPlayerOneToLobby();
                } else if (game.playerTwo.equals(player.get())) {
                    game.teleportPlayerTwoToLobby();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuitEvent (PlayerQuitEvent event) {
        Optional<ArenaPlayer> player = PlayerManager.getInstance().getPlayerIfExists(event.getPlayer().getName());
        if (!player.isPresent() || !GameManager.getInstance().getInProgress().containsKey(player.get()))
            return;

        player.get().backupArenaEquip();
        player.get().restoreInventory();
        GameManager.getInstance().getSpawn().warpPlayer(player.get().getName(), false);
    }
}


