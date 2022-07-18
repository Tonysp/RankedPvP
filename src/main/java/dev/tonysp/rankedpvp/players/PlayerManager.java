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

package dev.tonysp.rankedpvp.players;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.tonysp.rankedpvp.Manager;
import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.data.Action;
import dev.tonysp.rankedpvp.data.DataPacket;
import dev.tonysp.rankedpvp.game.EventType;
import dev.tonysp.rankedpvp.game.Game;
import dev.tonysp.rankedpvp.game.TwoPlayerGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class PlayerManager extends Manager {

    private final Map<Integer, ArenaPlayer> playersById = new HashMap<>();
    private final Map<String, ArenaPlayer> playersByName = new HashMap<>();
    private final Map<UUID, ArenaPlayer> players = new HashMap<>();
    private final Map<String, Warp> playersToWarp = new HashMap<>();
    private TreeSet<ArenaPlayer> topPlayersCache = new TreeSet<>();

    private boolean ranksEnabled = false;
    private final TreeMap<Integer, Rank> ranks = new TreeMap<>();

    private Sound ACCEPT_MESSAGE_SOUND = null;

    private int topPlayersTask, cooldownTask;

    public PlayerManager (RankedPvP plugin) {
        super(plugin);
    }

    @Override
    public boolean load () {
        playersById.clear();
        playersByName.clear();
        players.clear();
        playersToWarp.clear();
        topPlayersCache.clear();

        RankedPvP plugin = RankedPvP.getInstance();
        loadRanks(plugin.getConfig());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        try {
            ACCEPT_MESSAGE_SOUND = Sound.valueOf(plugin.getConfig().getString("accept-sound"));
        } catch (Exception ignored) {}

        if (plugin.dataPackets().isMaster()) {
            if (!plugin.database().loadPlayers(players))
                return false;

            for (ArenaPlayer arenaPlayer : players.values()) {
                playersById.put(arenaPlayer.getId(), arenaPlayer);
                playersByName.put(arenaPlayer.getName().toLowerCase(), arenaPlayer);
            }
        }

        cooldownTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (EventType eventType : EventType.values()) {
                eventType.decrementCooldowns();
            }
        }, 0, 1200);

        topPlayersTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::refreshTopPlayers, 0, 1200);

        return true;
    }

    @Override
    public void unload () {
        Bukkit.getScheduler().cancelTask(this.cooldownTask);
        Bukkit.getScheduler().cancelTask(this.topPlayersTask);
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

    public TextComponent getPlayerRating (int rating, int gamesPlayed) {
        String value;
        if (ranksEnabled()) {
            value = Rank.fromRating(rating, gamesPlayed).getName();
        } else {
            value = String.valueOf(rating);
        }
        TextReplacementConfig replacement = TextReplacementConfig.builder().match("%RANK%:").replacement(value).build();
        return Messages.RANK.getMessage(replacement);
    }

    public void sendAcceptMessageToPlayer (String playerName, int timeRemaining, boolean share) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null || !player.isOnline()) {
            if (share && plugin.dataPackets().isCrossServerEnabled()) {
                DataPacket.newBuilder()
                        .action(Action.GAME_ACCEPT_REMINDER)
                        .string(playerName)
                        .integer(timeRemaining)
                        .buildPacket()
                        .send();
            }
            return;
        }

        Messages.CLICK_TO_TELEPORT.sendTo(player.getUniqueId());

        final TextComponent acceptMessage = Component
                .text("Prefix")
                .color(TextColor.color(25, 31, 55))
                .append(Component.text("Accept in %TIME%", TextColor.color(0, 31, 55)))
                //.text(Messages.CLICK_TO_TELEPORT.getMessage().replaceAll("%TIME%", Utils.secondStringRemaining(timeRemaining)))
                .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Click!")))
                ;
        player.sendMessage(acceptMessage);
        if (ACCEPT_MESSAGE_SOUND != null) {
            player.playSound(player.getLocation(), ACCEPT_MESSAGE_SOUND, 1.0f, 1.0f);
        }

        plugin.getConfig().set("testserialize", LegacyComponentSerializer.legacyAmpersand().serialize(acceptMessage));
        plugin.saveConfig();
    }

    public void savePlayerLocationAndTeleport (UUID uuid, boolean share) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            if (share && plugin.dataPackets().isCrossServerEnabled()) {
                DataPacket.newBuilder()
                        .action(Action.SAVE_PLAYER_LOCATION_AND_TP)
                        .uuid(uuid)
                        .buildPacket()
                        .send();
            }
            return;
        }

        if (plugin.dataPackets().isMaster()) {
            ArenaPlayer arenaPlayer = getOrCreatePlayer(uuid);
            if (!plugin.games().getPlayersInGame().containsKey(arenaPlayer))
                return;

            Game game = plugin.games().getPlayersInGame().get(arenaPlayer);
            arenaPlayer.setReturnLocation(Warp.fromLocation(player.getLocation()));
            game.teleportToLobby(arenaPlayer);
        } else {
            DataPacket.newBuilder()
                    .addReceiver(plugin.dataPackets().getMasterId())
                    .action(Action.PLAYER_LOCATION)
                    .uuid(uuid)
                    .warp(Warp.fromLocation(player.getLocation()))
                    .buildPacket()
                    .send();
        }
    }

    public void announce (TextComponent message, String except1, String except2, boolean share) {
        RankedPvP.log(message.content());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(except1) || player.getName().equalsIgnoreCase(except2))
                continue;
            sendMessageToPlayer(player.getUniqueId(), message, false);
        }

        if (share && plugin.dataPackets().isCrossServerEnabled()) {
            ArrayList<String> list = new ArrayList<>();
            list.add(except1);
            list.add(except2);
            DataPacket.newBuilder()
                    .action(Action.ANNOUNCE)
                    .string(Messages.getSerializer().serialize(message))
                    .stringList(list)
                    .buildPacket()
                    .send();
        }
    }

    public void announce (TextComponent message, boolean share) {
        announce(message, "", "", share);
    }

    public void sendMessageToPlayer (UUID playerUuid, TextComponent message, boolean share) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
            return;
        }

        if (share && plugin.dataPackets().isCrossServerEnabled()) {
            DataPacket.newBuilder()
                    .action(Action.PLAYER_MESSAGE)
                    .uuid(playerUuid)
                    .string(Messages.getSerializer().serialize(message))
                    .buildPacket()
                    .send();
        }
    }

    public ArenaPlayer getOrCreatePlayer (UUID uuid) {
        if (players.containsKey(uuid)) {
            return players.get(uuid);
        } else {
            return createPlayer(uuid);
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
        players.put(arenaPlayer.getUuid(), arenaPlayer);
        playersByName.put(arenaPlayer.getName().toLowerCase(), arenaPlayer);
    }

    private ArenaPlayer createPlayer (UUID uuid) {
        ArenaPlayer player = new ArenaPlayer(uuid);
        if (plugin.dataPackets().isMaster()) {
            plugin.database().insertPlayer(player);
        }
        playersById.put(player.getId(), player);
        players.put(uuid, player);
        playersByName.put(player.getName().toLowerCase(), player);

        return player;
    }

    public Optional<ArenaPlayer> getPlayerIfExists (String name) {
        if (playersByName.containsKey(name.toLowerCase())) {
            return Optional.of(playersByName.get(name.toLowerCase()));
        } else {
            return Optional.empty();
        }
    }

    public Optional<ArenaPlayer> getPlayerIfExists (UUID uuid) {
        if (players.containsKey(uuid)) {
            return Optional.of(players.get(uuid));
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
        topPlayersCache = (TreeSet<ArenaPlayer>) topPlayersCache.descendingSet();
    }

    public Optional<ArenaPlayer> getPlayerByRank (int rank) {
        Set<ArenaPlayer> players = getTopPlayers();
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

    public void updatePlayerName (Player player) {
        Optional<ArenaPlayer> arenaPlayer = getPlayerIfExists(player.getUniqueId());
        if (arenaPlayer.isEmpty())
            return;

        if (!arenaPlayer.get().getName().equals(player.getName())) {
            playersByName.remove(arenaPlayer.get().getName().toLowerCase());
            playersByName.put(player.getName().toLowerCase(), arenaPlayer.get());
            arenaPlayer.get().setName(player.getName());
            plugin.database().updatePlayer(arenaPlayer.get());
        }
    }

    public void switchServer (Player player, String destinationServer) {
        final ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(destinationServer);
        player.sendPluginMessage(RankedPvP.getInstance(), "BungeeCord", out.toByteArray());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoinEvent (PlayerJoinEvent event) {
        updatePlayerName(event.getPlayer());

        String playerName = event.getPlayer().getName();
        if (playersToWarp.containsKey(playerName)) {
            playersToWarp.get(playerName).warpPlayer(playerName, false);
            playersToWarp.remove(playerName);
        } else {
            Optional<ArenaPlayer> player = getPlayerIfExists(event.getPlayer().getUniqueId());
            if (player.isEmpty() || !plugin.games().getPlayersInGame().containsKey(player.get()))
                return;

            TwoPlayerGame game = (TwoPlayerGame) plugin.games().getPlayersInGame().get(player.get());
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
        Optional<ArenaPlayer> player = getPlayerIfExists(event.getPlayer().getUniqueId());
        if (player.isEmpty() || !plugin.games().getPlayersInGame().containsKey(player.get()))
            return;

        EventType eventType = plugin.games().getPlayersInGame().get(player.get()).getArena().eventType;
        if (eventType.isBackupInventory()) {
            player.get().backupInventory(true);
            player.get().restoreInventory(false);
        }
        if (eventType.isBackupStatusEffects()) {
            player.get().backupStatusEffects(true);
            player.get().restoreStatusEffects(false);
        }
        plugin.games().getSpawn().warpPlayer(player.get().getName(), false);
    }
}


