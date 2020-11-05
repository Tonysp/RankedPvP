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

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.Utils;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.data.Action;
import dev.tonysp.rankedpvp.data.DataPacket;
import dev.tonysp.rankedpvp.data.DataPacketProcessor;
import dev.tonysp.rankedpvp.data.Database;
import dev.tonysp.rankedpvp.game.EventType;
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
    private Map<String, Warp> playersToWarp = new HashMap<>();
    private TreeSet<ArenaPlayer> topPlayersCache = new TreeSet<>();

    private boolean ranksEnabled = false;
    private TreeMap<Integer, Rank> ranks = new TreeMap<>();

    private Sound ACCEPT_MESSAGE_SOUND = null;

    public static PlayerManager getInstance () {
        if (instance == null) {
            instance = new PlayerManager();
        }
        return instance;
    }

    private PlayerManager () {
        RankedPvP plugin = RankedPvP.getInstance();
        loadRanks(plugin.getConfig());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        try {
            ACCEPT_MESSAGE_SOUND = Sound.valueOf(plugin.getConfig().getString("accept-sound"));
        } catch (Exception ignored) {}

        if (RankedPvP.IS_MASTER) {
            players = Database.getInstance().loadPlayers();
            for (ArenaPlayer arenaPlayer : players.values()) {
                playersById.put(arenaPlayer.getId(), arenaPlayer);
            }
        }

        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (EventType eventType : EventType.values()) {
                eventType.decrementCooldowns();
            }
        }, 0, 1200);

        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::refreshTopPlayers, 0, 1200);
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
            if (share && DataPacketProcessor.getInstance().isCrossServerEnabled()) {
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
            if (share && DataPacketProcessor.getInstance().isCrossServerEnabled()) {
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

        if (share && DataPacketProcessor.getInstance().isCrossServerEnabled()) {
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

        if (share && DataPacketProcessor.getInstance().isCrossServerEnabled()) {
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
        playersById.put(arenaPlayer.getId(), arenaPlayer);
        players.put(arenaPlayer.getName().toLowerCase(), arenaPlayer);
    }

    private ArenaPlayer createPlayer (String name) {
        ArenaPlayer player = new ArenaPlayer(name);
        if (RankedPvP.IS_MASTER) {
            Database.getInstance().insertPlayer(player);
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
        return topPlayersCache;
    }

    private void refreshTopPlayers () {
        topPlayersCache = new TreeSet<>(players.values());
        if (ranksEnabled() && Rank.UNRANKED != null) {
            topPlayersCache.removeIf(player -> player.getMatches() < Rank.GAMES_TO_LOSE_UNRANKED);
        }
        topPlayersCache = (TreeSet<ArenaPlayer>)topPlayersCache.descendingSet();
    }

    public Optional<ArenaPlayer> getPlayerByRank (int rank) {
        TreeSet<ArenaPlayer> players = PlayerManager.getInstance().getTopPlayers();
        Iterator<ArenaPlayer> it = players.iterator();
        int i = 0;
        ArenaPlayer current = null;
        while(it.hasNext() && i < rank) {
            current = it.next();
            i++;
        }

        if (i == rank && current != null) {
            return Optional.of(current);
        } else {
            return Optional.empty();
        }
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

            TwoPlayerGame game = (TwoPlayerGame) GameManager.getInstance().getInProgress().get(player.get());
            if (game.getArena().eventType.isBackupInventory()) {
                player.get().backupInventory(false);
                player.get().restoreInventory(true);
            }
            if (game.getArena().eventType.isBackupStatusEffects()) {
                player.get().backupStatusEffects(false);
                player.get().restoreStatusEffects(true);
            }
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

        EventType eventType = GameManager.getInstance().getInProgress().get(player.get()).getArena().eventType;
        if (eventType.isBackupInventory()) {
            player.get().backupInventory(true);
            player.get().restoreInventory(false);
        }
        if (eventType.isBackupStatusEffects()) {
            player.get().backupStatusEffects(true);
            player.get().restoreStatusEffects(false);
        }
        GameManager.getInstance().getSpawn().warpPlayer(player.get().getName(), false);
    }
}


