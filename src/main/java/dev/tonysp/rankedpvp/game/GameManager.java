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

package dev.tonysp.rankedpvp.game;

import dev.tonysp.rankedpvp.Manager;
import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.Utils;
import dev.tonysp.rankedpvp.arenas.Arena;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.data.Action;
import dev.tonysp.rankedpvp.data.DataPacket;
import dev.tonysp.rankedpvp.game.result.TwoTeamGameResult;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.*;

public class GameManager extends Manager {

    private static final int QUEUE_CHECK_DELAY = 5;

    private final Map<EventType, Integer> timeToCheckQueue = new HashMap<>();
    private final Map<EventType, LinkedList<ArenaPlayer>> playerQueue = new HashMap<>();

    private final Map<ArenaPlayer, Game> waitingForAccept = new HashMap<>();
    private final Map<ArenaPlayer, Game> playersInGame = new HashMap<>();

    private final List<Game> games = new ArrayList<>();

    private Warp spawn;

    private int queueTaskId, gameTickTaskId;

    public GameManager (RankedPvP plugin) {
        super(plugin);
    }

    @Override
    public boolean load () {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        playerQueue.clear();
        timeToCheckQueue.clear();
        games.clear();
        waitingForAccept.clear();
        playersInGame.clear();
        for (EventType eventType : EventType.values()) {
            playerQueue.put(eventType, new LinkedList<>());
        }

        Optional<Location> location = Utils.teleportLocationFromString(plugin.getConfig().getString("spawn"));
        location.ifPresent(value -> {
            spawn = Warp.fromLocation(value);
            if (plugin.dataPackets().isMaster()) {
                spawn.server = plugin.dataPackets().getServerId();
            }
        });

        if (location.isEmpty()) {
            RankedPvP.logWarning("error while loading global spawn point: invalid location");
            return false;
        }

        if (plugin.dataPackets().isMaster()) {
            ArrayList<TwoTeamGameResult> matchHistory = plugin.database().loadMatchHistory();
            for (TwoTeamGameResult twoTeamMatchResult : matchHistory) {
                plugin.players().getPlayerIfExists(twoTeamMatchResult.teamOne)
                        .ifPresent(player -> {
                            player.addMatchToHistory(twoTeamMatchResult);
                        });
                plugin.players().getPlayerIfExists(twoTeamMatchResult.teamTwo)
                        .ifPresent(player -> {
                            player.addMatchToHistory(twoTeamMatchResult);
                        });
            }
        }

        queueTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::checkQueue, 20L, 20L);
        gameTickTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            games.removeIf(game -> !game.shouldTick());
            for (Game game : games) {
                game.tick();
            }
        }, 5L, 5L);

        return true;
    }

    @Override
    public void unload () {
        PlayerDeathEvent.getHandlerList().unregister(this);
        EntityDamageByEntityEvent.getHandlerList().unregister(this);
        EntityDamageEvent.getHandlerList().unregister(this);
        PlayerInteractEvent.getHandlerList().unregister(this);
        PlayerDropItemEvent.getHandlerList().unregister(this);

        Bukkit.getScheduler().cancelTask(queueTaskId);
        Bukkit.getScheduler().cancelTask(gameTickTaskId);
    }

    public void checkQueue () {
        playerQueue.values().forEach(value -> value.forEach(ArenaPlayer::incrementTimeInQueue));
        for (Map.Entry<EventType, Integer> entry : timeToCheckQueue.entrySet()) {
            if (entry.getValue() == 0) {
                tryStartingGame(entry.getKey());
            }

            if (entry.getValue() >= 0) {
                entry.setValue(entry.getValue() - 1);
            }
        }
    }

    public void tryStartingGame (EventType eventType) {
        if (playerQueue.get(eventType).size() <= 1)
            return;

        Optional<Arena> arena = plugin.arenas().getAndLockFreeArena(eventType);
        // If no arena is available
        if (arena.isEmpty()) {
            if (timeToCheckQueue.containsKey(eventType) && timeToCheckQueue.get(eventType) < 3) {
                timeToCheckQueue.put(eventType, 3);
            }
            return;
        }

        Game game;
        if (eventType == EventType.ONE_VS_ONE) {
            game = createTwoPlayerGame(playerQueue.get(eventType), arena.get());
        } else {
            return;
        }

        for (ArenaPlayer arenaPlayer : game.getPlayers()) {
            playerQueue.get(eventType).remove(arenaPlayer);
            waitingForAccept.put(arenaPlayer, game);
        }

        games.add(game);
    }

    public TwoPlayerGame createTwoPlayerGame (LinkedList<ArenaPlayer> queue, Arena arena) {

        // Match two players from queue with lowest rating difference
        double lowest = Double.MAX_VALUE;
        ArenaPlayer first = null, second = null;
        for (ArenaPlayer player1 : queue) {
            for (ArenaPlayer player2 : queue) {
                if (player1.equals(player2)) {
                    continue;
                }
                double diff;
                if (player1.getRating() > player2.getRating()) {
                    diff = player1.getRating() - player2.getRating();
                } else {
                    diff = player2.getRating() - player1.getRating();
                }

                diff -= player1.getTimeInQueue();
                diff -= player2.getTimeInQueue();
                if (diff < lowest) {
                    first = player1;
                    second = player2;
                    lowest = diff;
                }
            }
        }

        List<ArenaPlayer> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);
        return new TwoPlayerGame(arena, participants);
    }

    public Map<ArenaPlayer, Game> getPlayersInGame () {
        return playersInGame;
    }

    public Map<ArenaPlayer, Game> getWaitingForAccept () {
        return waitingForAccept;
    }

    public void tryAcceptingGame (UUID uuid) {
        if (!plugin.dataPackets().isMaster()) {
            DataPacket.newBuilder()
                    .addReceiver(plugin.dataPackets().getMasterId())
                    .action(Action.GAME_ACCEPT)
                    .uuid(uuid)
                    .buildPacket()
                    .send();
            return;
        }

        ArenaPlayer player = plugin.players().getOrCreatePlayer(uuid);
        if (!waitingForAccept.containsKey(player)) {
            Messages.NOT_PART_OF_GAME.sendTo(uuid);
            return;
        }

        waitingForAccept.get(player).accepted(player);
        waitingForAccept.remove(player);
    }

    public void toggleJoin (UUID uuid, EventType eventType) {
        ArenaPlayer arenaPlayer = plugin.players().getOrCreatePlayer(uuid);

        if (playerQueue.get(eventType).contains(arenaPlayer)) {
            tryLeavingQueue(uuid, eventType);
        } else {
            tryJoiningQueue(uuid, eventType);
        }
    }

    public void tryLeavingQueue (UUID uuid, EventType eventType) {
        if (!plugin.dataPackets().isMaster()) {
            DataPacket.newBuilder()
                    .addReceiver(plugin.dataPackets().getMasterId())
                    .action(Action.PLAYER_ARENA_LEAVE)
                    .uuid(uuid)
                    .string(eventType.toString())
                    .buildPacket()
                    .send();
            return;
        }

        ArenaPlayer arenaPlayer = plugin.players().getOrCreatePlayer(uuid);

        if (!playerQueue.get(eventType).contains(arenaPlayer)) {
            Messages.NOT_IN_QUEUE.sendTo(arenaPlayer.getUuid());
            return;
        }

        playerQueue.get(eventType).remove(arenaPlayer);

        Messages.LEFT_QUEUE.sendTo(arenaPlayer.getUuid());
    }


    public void tryJoiningQueue (UUID uuid, EventType eventType) {
        if (!plugin.dataPackets().isMaster()) {
            DataPacket.newBuilder()
                    .addReceiver(plugin.dataPackets().getMasterId())
                    .action(Action.PLAYER_ARENA_JOIN)
                    .uuid(uuid)
                    .string(eventType.toString())
                    .buildPacket()
                    .send();
            return;
        }

        ArenaPlayer arenaPlayer = plugin.players().getOrCreatePlayer(uuid);

        if (waitingForAccept.containsKey(arenaPlayer) || playersInGame.containsKey(arenaPlayer)) {
            return;
        }

        if (playerQueue.get(eventType).contains(arenaPlayer)) {
            Messages.ALREADY_WAITING.sendTo(arenaPlayer.getUuid(), "%EVENT%:" + eventType.getNiceName());
            return;
        }

        if (eventType.isOnCooldown(arenaPlayer)) {
            TextReplacementConfig replacement = TextReplacementConfig.builder().match("%TIME%").replacement(Utils.minutesString(eventType.getCooldown(arenaPlayer))).build();
            Messages.COOLDOWN.sendTo(arenaPlayer.getUuid(), replacement);
            return;
        }

        arenaPlayer.resetTimeInQueue();
        playerQueue.get(eventType).add(arenaPlayer);
        timeToCheckQueue.put(eventType, QUEUE_CHECK_DELAY);

        Messages.WAIT_FOR_OPPONENT.sendTo(arenaPlayer.getUuid());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeathEvent (PlayerDeathEvent event) {
        Optional<ArenaPlayer> player = plugin.players().getPlayerIfExists(event.getEntity().getUniqueId());
        if (player.isEmpty()
                || !playersInGame.containsKey(player.get())) {
            return;
        }

        playersInGame.get(player.get()).processDeath(player.get());
        event.setKeepInventory(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageEvent (EntityDamageEvent event) {
        Optional<ArenaPlayer> player = plugin.players().getPlayerIfExists(event.getEntity().getUniqueId());
        if (player.isEmpty()) {
            return;
        }

        if (!playersInGame.containsKey(player.get())) {
            return;
        }

        LivingEntity livingEntity = (LivingEntity) event.getEntity();
        if (livingEntity.getHealth() - event.getFinalDamage() <= 0) {
            playersInGame.get(player.get()).processDeath(player.get());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntityEvent (EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof org.bukkit.entity.Player) || !(event.getEntity() instanceof org.bukkit.entity.Player)) {
            return;
        }
        Optional<ArenaPlayer> attacker = plugin.players().getPlayerIfExists(event.getDamager().getUniqueId());
        Optional<ArenaPlayer> defender = plugin.players().getPlayerIfExists(event.getEntity().getUniqueId());

        if (attacker.isPresent() && playersInGame.containsKey(attacker.get())) {
            if (defender.isEmpty() || !playersInGame.containsKey(defender.get()) || !playersInGame.get(attacker.get()).equals(playersInGame.get(defender.get()))) {
                event.setCancelled(true);
            }
        } else if (defender.isPresent() && playersInGame.containsKey(defender.get())) {
            if (attacker.isEmpty() || !playersInGame.containsKey(attacker.get()) || !playersInGame.get(defender.get()).equals(playersInGame.get(attacker.get()))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEvent (PlayerInteractEvent event) {
        Optional<ArenaPlayer> player = plugin.players().getPlayerIfExists(event.getPlayer().getUniqueId());
        if (player.isEmpty() || !playersInGame.containsKey(player.get()))
            return;

        Game game = playersInGame.get(player.get());
        if (!game.getArena().isInArena(player.get()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItemEvent (PlayerDropItemEvent event) {
        Optional<ArenaPlayer> player = plugin.players().getPlayerIfExists(event.getPlayer().getUniqueId());
        if (player.isEmpty() || !playersInGame.containsKey(player.get()))
            return;

        Game game = playersInGame.get(player.get());
        if (!game.getArena().isInArena(player.get()))
            event.setCancelled(true);
    }

    public void endAllGames () {
        for (Game game : games) {
            game.gameState = GameState.FAILED;
            game.endMatch();
        }
    }

    public List<Game> getGames () {
        return games;
    }

    public Map<EventType, LinkedList<ArenaPlayer>> getPlayerQueue () {
        return playerQueue;
    }

    public Warp getSpawn () {
        return spawn;
    }

    public boolean isWaitingToAccept (ArenaPlayer player) {
        return waitingForAccept.containsKey(player);
    }
}

