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

import de.gesundkrank.jskills.IPlayer;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.TwoPlayerTrueSkillCalculator;
import dev.tonysp.rankedpvp.Manager;
import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.Utils;
import dev.tonysp.rankedpvp.arenas.Arena;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.data.Action;
import dev.tonysp.rankedpvp.data.DataPacket;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import net.kyori.adventure.text.TextComponent;
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

    private final Map<EventType, Integer> checkQueue = new HashMap<>();
    private final Map<EventType, LinkedList<ArenaPlayer>> playerQueue = new HashMap<>();

    private final Map<ArenaPlayer, Game> waitingForAccept = new HashMap<>();
    private final Map<ArenaPlayer, Game> inProgress = new HashMap<>();

    private final List<Game> games = new ArrayList<>();

    private Warp spawn;

    private int queueTask, gameTickTask;

    public GameManager (RankedPvP plugin) {
        super(plugin);
    }

    @Override
    public boolean load () {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        playerQueue.clear();
        checkQueue.clear();
        games.clear();
        waitingForAccept.clear();
        inProgress.clear();
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

        if (plugin.dataPackets().isMaster()) {
            ArrayList<MatchResult> matchHistory = plugin.database().loadMatchHistory();
            for (MatchResult matchResult : matchHistory) {
                plugin.players().getPlayerIfExists(matchResult.teamOne)
                        .ifPresent(player -> {
                            player.addMatchToHistory(matchResult);
                        });
                plugin.players().getPlayerIfExists(matchResult.teamTwo)
                        .ifPresent(player -> {
                            player.addMatchToHistory(matchResult);
                        });
            }
        }

        queueTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::checkQueue, 20L, 20L);
        gameTickTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            games.removeIf(Game::isCancelled);
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

        Bukkit.getScheduler().cancelTask(queueTask);
        Bukkit.getScheduler().cancelTask(gameTickTask);
    }

    public void checkQueue () {
        playerQueue.values().forEach(value -> value.forEach(ArenaPlayer::incrementTimeInQueue));
        for (Map.Entry<EventType, Integer> entry : checkQueue.entrySet()) {
            //PvPArena.log("check queue: " + entry.getValue());
            if (entry.getValue() == 0) {
                tryStartingGame(entry.getKey());
            }

            if (entry.getValue() >= 0) {
                entry.setValue(entry.getValue() - 1);
            }
        }
    }

    public void tryStartingGame (EventType eventType) {
        if (playerQueue.get(eventType).size() <= 1) {
            return;
        }
        //PvPArena.log("game starting");

        Optional<Arena> arena = plugin.arenas().getAndLockFreeArena(eventType);
        if (arena.isEmpty()) {
            //PvPArena.log("no arena available");
            if (checkQueue.containsKey(eventType) && checkQueue.get(eventType) < 3) {
                checkQueue.put(eventType, 3);
            }
            return;
        }

        double lowest = Double.MAX_VALUE;
        ArenaPlayer first = null, second = null;
        for (ArenaPlayer player1 : playerQueue.get(eventType)) {
            for (ArenaPlayer player2 : playerQueue.get(eventType)) {
                if (player1.equals(player2)) {
                    continue;
                }
                double score;
                if (player1.getRating() > player2.getRating()) {
                    score = player1.getRating() - player2.getRating();
                } else {
                    score = player2.getRating() - player1.getRating();
                }

                score -= player1.getTimeInQueue();
                score -= player2.getTimeInQueue();
                if (score < lowest) {
                    first = player1;
                    second = player2;
                    lowest = score;
                }
            }
        }
        if (first == null) {
            return;
        }

        playerQueue.get(eventType).remove(first);
        playerQueue.get(eventType).remove(second);

        TwoPlayerGame twoPlayerGame = new TwoPlayerGame(arena.get(), first, second);
        waitingForAccept.put(first, twoPlayerGame);
        waitingForAccept.put(second, twoPlayerGame);
        games.add(twoPlayerGame);
    }

    public void gameEnded (TwoPlayerGame game, int winnerId) {
        de.gesundkrank.jskills.Player<ArenaPlayer> player1 = new de.gesundkrank.jskills.Player<>(game.playerOne);
        de.gesundkrank.jskills.Player<ArenaPlayer> player2 = new de.gesundkrank.jskills.Player<>(game.playerTwo);
        Team team1 = new Team(player1, new Rating(player1.getId().getRating(), player1.getId().getDeviation()));
        Team team2 = new Team(player2, new Rating(player2.getId().getRating(), player2.getId().getDeviation()));

        EventType.ONE_VS_ONE.applyCooldown(player1.getId());
        EventType.ONE_VS_ONE.applyCooldown(player2.getId());

        double player1OldRatingDouble = player1.getId().getRating();
        double player2OldRatingDouble = player2.getId().getRating();
        double player1OldDeviation = player1.getId().getDeviation();
        double player2OldDeviation = player2.getId().getDeviation();
        int player1OldRatingVisible = player1.getId().getRatingVisible();
        int player2OldRatingVisible = player2.getId().getRatingVisible();
        int player1OldMatches = player1.getId().getMatches();
        int player2OldMatches = player2.getId().getMatches();

        Messages announceMessageNotDraw = Messages.ANNOUNCE_NOT_DRAW;
        Messages announceMessageDraw = Messages.ANNOUNCE_DRAW;
        TextComponent winnerAnnounce, loserAnnounce, announceMessage;

        TwoPlayerTrueSkillCalculator calculator = new TwoPlayerTrueSkillCalculator();
        Map<IPlayer, Rating> newRatings;
        if (winnerId == player1.getId().getId()) {
            newRatings = calculator.calculateNewRatings(game.gameInfo, Team.concat(team1, team2), 1, 2);
        } else if (winnerId == player2.getId().getId()) {
            newRatings = calculator.calculateNewRatings(game.gameInfo, Team.concat(team1, team2), 2, 1);
        } else {
            newRatings = calculator.calculateNewRatings(game.gameInfo, Team.concat(team1, team2), 1, 1);
        }

        Rating player1NewRating = newRatings.get(player1);
        Rating player2NewRating = newRatings.get(player2);
        player1.getId().setRating(player1NewRating);
        player2.getId().setRating(player2NewRating);

        MatchResult result = new MatchResult();
        result.teamOne = player1.getId().getId();
        result.teamTwo = player2.getId().getId();
        result.winnerTeam = winnerId;
        result.arena = game.arena.name;
        result.eventType = game.arena.eventType.toString();
        result.timestamp = Utils.getCurrentTimeStamp();
        result.teamOnePreR = player1OldRatingDouble;
        result.teamTwoPreR = player2OldRatingDouble;
        result.teamOnePostR = player1NewRating.getMean();
        result.teamTwoPostR = player2NewRating.getMean();
        result.teamOnePreD = player1OldDeviation;
        result.teamTwoPreD = player2OldDeviation;
        result.teamOnePostD = player1NewRating.getStandardDeviation();
        result.teamTwoPostD = player2NewRating.getStandardDeviation();
        result.calculateMatchQuality();
        player1.getId().addMatchToHistory(result);
        player2.getId().addMatchToHistory(result);
        plugin.database().insertMatchResult(result);

        int winnerOldRatingVisible, loserOldRatingVisible, winnerOldMatches, loserOldMatches;
        Player<ArenaPlayer> winner, loser;

        if (winnerId == player1.getId().getId() || player1OldRatingDouble < player1NewRating.getMean()) {
            winnerOldRatingVisible = player1OldRatingVisible;
            winnerOldMatches = player1OldMatches;
            loserOldMatches = player2OldMatches;
            winner = player1;
            loserOldRatingVisible = player2OldRatingVisible;
            loser = player2;
        } else if (winnerId == player2.getId().getId() || player2OldRatingDouble < player2NewRating.getMean()) {
            winnerOldRatingVisible = player2OldRatingVisible;
            winnerOldMatches = player2OldMatches;
            loserOldMatches = player1OldMatches;
            winner = player2;
            loserOldRatingVisible = player1OldRatingVisible;
            loser = player1;
        } else {
            return;
        }

        double winnerRatingDiff = winner.getId().getRatingVisible() - winnerOldRatingVisible;
        winnerAnnounce = winner.getId().getNameWithRatingAndChange(winnerRatingDiff);
        double loserRatingDiff = loser.getId().getRatingVisible() - loserOldRatingVisible;
        loserAnnounce = loser.getId().getNameWithRatingAndChange(loserRatingDiff);
        TextComponent winnerOldRank = plugin.players().getPlayerRating(winnerOldRatingVisible, winnerOldMatches);
        TextComponent winnerNewRank = plugin.players().getPlayerRating(winner.getId().getRatingVisible(), winner.getId().getMatches());
        TextComponent loserOldRank = plugin.players().getPlayerRating(loserOldRatingVisible, loserOldMatches);
        TextComponent loserNewRank = plugin.players().getPlayerRating(loser.getId().getRatingVisible(), loser.getId().getMatches());
        boolean winnerRankChanged = !winnerOldRank.toString().equalsIgnoreCase(winnerNewRank.toString());
        boolean loserRankChanged = !loserOldRank.toString().equalsIgnoreCase(loserNewRank.toString());

        Messages winnerMessage, loserMessage, drawMessage1, drawMessage2;
        if (winnerRankChanged) {
            winnerMessage = Messages.WIN_MESSAGE_CHANGE;
            drawMessage1 = Messages.DRAW_MESSAGE_CHANGE;
        } else {
            winnerMessage = Messages.WIN_MESSAGE;
            drawMessage1 = Messages.DRAW_MESSAGE;
        }
        if (loserRankChanged) {
            loserMessage = Messages.LOSE_MESSAGE_CHANGE;
            drawMessage2 = Messages.DRAW_MESSAGE_CHANGE;
        } else {
            loserMessage = Messages.LOSE_MESSAGE;
            drawMessage2 = Messages.DRAW_MESSAGE;
        }

        List<TextReplacementConfig> winnerReplacement = new ArrayList<>();
        List<TextReplacementConfig> loserReplacement = new ArrayList<>();
        List<TextReplacementConfig> announceReplacement = new ArrayList<>();
        winnerReplacement.add(TextReplacementConfig.builder().match("%OLD%:").replacement(winnerOldRank).build());
        winnerReplacement.add(TextReplacementConfig.builder().match("%NEW%:").replacement(winnerNewRank).build());
        loserReplacement.add(TextReplacementConfig.builder().match("%OLD%:").replacement(loserOldRank).build());
        loserReplacement.add(TextReplacementConfig.builder().match("%NEW%:").replacement(loserNewRank).build());
        announceReplacement.add(TextReplacementConfig.builder().match("%WINNER%:").replacement(winnerAnnounce).build());
        announceReplacement.add(TextReplacementConfig.builder().match("%LOSER%:").replacement(loserAnnounce).build());


        if (winnerId != player1.getId().getId()
                && winnerId != player2.getId().getId()) {
            announceMessage = announceMessageDraw.getMessage(announceReplacement);
            winnerMessage = drawMessage1;
            loserMessage = drawMessage2;
        } else {
            announceMessage = announceMessageNotDraw.getMessage(announceReplacement);
        }

        final Messages winnerMessageFinal = winnerMessage;
        final Messages loserMessageFinal = loserMessage;
        Bukkit.getScheduler().scheduleSyncDelayedTask(RankedPvP.getInstance(), () -> {
            winnerMessageFinal.sendTo(winner.getId().getUuid(), winnerReplacement);
            loserMessageFinal.sendTo(loser.getId().getUuid(), loserReplacement);
        }, 60);


        plugin.database().updatePlayer(player1.getId());
        plugin.database().updatePlayer(player2.getId());

        final TextComponent announceMessageFinal = announceMessage;
        Bukkit.getScheduler().scheduleSyncDelayedTask(RankedPvP.getInstance(), () -> {
            plugin.players().announce(announceMessageFinal, true);
        }, 60L);
    }

    public Map<ArenaPlayer, Game> getInProgress () {
        return inProgress;
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

        ArenaPlayer arenaPlayer = plugin.players().getOrCreatePlayer(uuid);
        if (!waitingForAccept.containsKey(arenaPlayer)) {
            Messages.NOT_PART_OF_GAME.sendTo(uuid);
            return;
        }

        TwoPlayerGame game = (TwoPlayerGame) waitingForAccept.get(arenaPlayer);
        waitingForAccept.remove(arenaPlayer);
        ArenaPlayer other = game.getOtherPlayer(arenaPlayer);
        if (game.playerOne.equals(arenaPlayer)) {
            game.oneAccepted = true;
        } else if (game.playerTwo.equals(arenaPlayer)) {
            game.twoAccepted = true;
        }
        if (waitingForAccept.containsKey(other)) {
            Messages.ACCEPTED.sendTo(arenaPlayer.getUuid());
        } else {
            inProgress.put(game.playerOne, game);
            inProgress.put(game.playerTwo, game);
            game.teleportBothToLobby();
        }
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

        if (waitingForAccept.containsKey(arenaPlayer) || inProgress.containsKey(arenaPlayer)) {
            return;
        }

        if (playerQueue.get(eventType).contains(arenaPlayer)) {
            Messages.ALREADY_WAITING.sendTo(arenaPlayer.getUuid(), "%EVENT%:" + eventType.getNiceName());
            return;
        }

        if (eventType.isOnCooldown(arenaPlayer)) {
            Messages.COOLDOWN.sendTo(arenaPlayer.getUuid(), "%TIME%:" + Utils.minutesString(eventType.getCooldown(arenaPlayer)));
            return;
        }

        arenaPlayer.resetTimeInQueue();
        playerQueue.get(eventType).add(arenaPlayer);
        checkQueue.put(eventType, QUEUE_CHECK_DELAY);

        Messages.WAIT_FOR_OPPONENT.sendTo(arenaPlayer.getUuid());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeathEvent (PlayerDeathEvent event) {
        Optional<ArenaPlayer> player = plugin.players().getPlayerIfExists(event.getEntity().getUniqueId());
        if (player.isEmpty()
                || !inProgress.containsKey(player.get())) {
            return;
        }

        RankedPvP.log("PlayerDeathEvent in arena, should never happen :(");
        final TwoPlayerGame game = (TwoPlayerGame) inProgress.get(player.get());
        game.playerDied(player.get());
        event.setKeepInventory(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageEvent (EntityDamageEvent event) {
        Optional<ArenaPlayer> player = plugin.players().getPlayerIfExists(event.getEntity().getUniqueId());
        if (player.isEmpty()) {
            return;
        }

        if (!inProgress.containsKey(player.get())) {
            return;
        }

        LivingEntity livingEntity = (LivingEntity) event.getEntity();
        if (livingEntity.getHealth() - event.getFinalDamage() <= 0) {
            TwoPlayerGame game = (TwoPlayerGame) inProgress.get(player.get());
            game.playerDied(player.get());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntityEvent (EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof org.bukkit.entity.Player) || !(event.getEntity() instanceof org.bukkit.entity.Player)) {
            return;
        }
        Optional<ArenaPlayer> player1 = plugin.players().getPlayerIfExists(event.getDamager().getUniqueId());
        Optional<ArenaPlayer> player2 = plugin.players().getPlayerIfExists(event.getEntity().getUniqueId());

        if (player1.isPresent() && inProgress.containsKey(player1.get())) {
            if (player2.isEmpty() || !inProgress.containsKey(player2.get()) || !inProgress.get(player1.get()).equals(inProgress.get(player2.get()))) {
                event.setCancelled(true);
            }
        } else if (player2.isPresent() && inProgress.containsKey(player2.get())) {
            if (player1.isEmpty() || !inProgress.containsKey(player1.get()) || !inProgress.get(player2.get()).equals(inProgress.get(player1.get()))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEvent (PlayerInteractEvent event) {
        Optional<ArenaPlayer> player = plugin.players().getPlayerIfExists(event.getPlayer().getUniqueId());
        if (player.isEmpty() || !inProgress.containsKey(player.get()))
            return;

        TwoPlayerGame game = (TwoPlayerGame) inProgress.get(player.get());
        if (!game.getArena().isInArena(player.get()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItemEvent (PlayerDropItemEvent event) {
        Optional<ArenaPlayer> player = plugin.players().getPlayerIfExists(event.getPlayer().getUniqueId());
        if (player.isEmpty() || !inProgress.containsKey(player.get()))
            return;

        TwoPlayerGame game = (TwoPlayerGame) inProgress.get(player.get());
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
}

