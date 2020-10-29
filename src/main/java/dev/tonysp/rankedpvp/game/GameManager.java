package dev.tonysp.rankedpvp.game;

import de.gesundkrank.jskills.IPlayer;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.TwoPlayerTrueSkillCalculator;
import dev.tonysp.plugindata.data.DataPacketManager;
import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.Utils;
import dev.tonysp.rankedpvp.arenas.Arena;
import dev.tonysp.rankedpvp.arenas.ArenaManager;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.data.Action;
import dev.tonysp.rankedpvp.data.DataPacket;
import dev.tonysp.rankedpvp.data.Database;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import dev.tonysp.rankedpvp.players.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.text.DecimalFormat;
import java.util.*;

public class GameManager implements Listener {

    private static GameManager instance;
    private static final int QUEUE_CHECK_DELAY = 5;
    public static String SLASHES = ChatColor.RED + "§l//" + ChatColor.RESET;

    private HashMap<EventType, Integer> checkQueue;
    private HashMap<EventType, LinkedList<ArenaPlayer>> playerQueue;

    private HashMap<ArenaPlayer, Game> waitingForAccept;
    private HashMap<ArenaPlayer, Game> inProgress;

    private ArrayList<Game> games;

    private Warp spawn;

    public static GameManager getInstance () {
        return instance;
    }

    public static void initialize (RankedPvP plugin) {
        instance = new GameManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        instance.playerQueue = new HashMap<>();
        instance.checkQueue = new HashMap<>();
        instance.games = new ArrayList<>();
        instance.waitingForAccept = new HashMap<>();
        instance.inProgress = new HashMap<>();
        for (EventType eventType : EventType.values()) {
            instance.playerQueue.put(eventType, new LinkedList<>());
        }

        Optional<Location> location = Utils.teleportLocationFromString(plugin.getConfig().getString("spawn"));
        location.ifPresent(value -> {
            instance.spawn = Warp.fromLocation(value);
            if (RankedPvP.IS_MASTER) {
                instance.spawn.server = DataPacketManager.getInstance().SERVER_ID;
            }
        });

        if (RankedPvP.IS_MASTER) {
            ArrayList<MatchResult> matchHistory = Database.loadMatchHistory();
            for (MatchResult matchResult : matchHistory) {
                PlayerManager.getInstance().getPlayerIfExists(matchResult.teamOne)
                        .ifPresent(player -> {
                            player.getMatchHistory().add(matchResult);
                        });
                PlayerManager.getInstance().getPlayerIfExists(matchResult.teamTwo)
                        .ifPresent(player -> {
                            player.getMatchHistory().add(matchResult);
                        });
            }
        }

        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> instance.checkQueue(), 20L, 20L);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            instance.games.removeIf(Game::isCancelled);
            for (Game game : instance.games) {
                game.tick();
            }
        }, 5L, 5L);
    }

    public void checkQueue () {
        playerQueue.values().forEach(value -> value.forEach(player -> player.incrementTimeInQueue()));
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

        Optional<Arena> arena = ArenaManager.getInstance().getAndLockFreeArena(eventType);
        if (!arena.isPresent()) {
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
        if (first == null || second == null) {
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
        //PvPArena.log("game ended calculations win: " + winnerId);
        de.gesundkrank.jskills.Player<ArenaPlayer> player1 = new de.gesundkrank.jskills.Player<>(game.playerOne);
        de.gesundkrank.jskills.Player<ArenaPlayer> player2 = new de.gesundkrank.jskills.Player<>(game.playerTwo);
        Team team1 = new Team(player1, new Rating(player1.getId().getRating(), player1.getId().getDeviation()));
        Team team2 = new Team(player2, new Rating(player2.getId().getRating(), player2.getId().getDeviation()));

        PlayerManager.getInstance().applyCooldown(player1.getId());
        PlayerManager.getInstance().applyCooldown(player2.getId());

        double player1OldRatingDouble = player1.getId().getRating();
        double player2OldRatingDouble = player2.getId().getRating();
        double player1OldDeviation = player1.getId().getDeviation();
        double player2OldDeviation = player2.getId().getDeviation();
        int player1OldRating = player1.getId().getRatingRound();
        int player2OldRating = player2.getId().getRatingRound();
        int player1OldRatingVisible = player1.getId().getRatingVisible();
        int player2OldRatingVisible = player2.getId().getRatingVisible();
        int player1OldMatches = player1.getId().getMatches();
        int player2OldMatches = player2.getId().getMatches();

        String announceMessageNotDraw = Messages.ANNOUNCE_NOT_DRAW.getMessage();
        String announceMessageDraw = Messages.ANNOUNCE_DRAW.getMessage();
        String winnerAnnounce, loserAnnounce, announceMessage;

        TwoPlayerTrueSkillCalculator calculator = new TwoPlayerTrueSkillCalculator();
        Map<IPlayer, Rating> newRatings;
        if (winnerId == player1.getId().getId()) {
            newRatings = calculator.calculateNewRatings(game.gameInfo, Team.concat(team1, team2), 1, 2);
            player1.getId().addWin();
            player2.getId().addLoss();
        } else if (winnerId == player2.getId().getId()) {
            newRatings = calculator.calculateNewRatings(game.gameInfo, Team.concat(team1, team2), 2, 1);
            player2.getId().addWin();
            player1.getId().addLoss();
        } else {
            newRatings = calculator.calculateNewRatings(game.gameInfo, Team.concat(team1, team2), 1, 1);
            player1.getId().addDraw();
            player2.getId().addDraw();
        }

        Rating player1NewRating = newRatings.get(player1);
        Rating player2NewRating = newRatings.get(player2);
        player1.getId().setRating(player1NewRating);
        player2.getId().setRating(player2NewRating);

        int winnerOldRating, loserOldRating, winnerOldRatingVisible, loserOldRatingVisible, winnerOldMatches, loserOldMatches;
        double winnerOldRatingDouble, loserOldRatingDouble;
        Player<ArenaPlayer> winner, loser;

        if (winnerId == player1.getId().getId() || player1OldRatingDouble < player1NewRating.getMean()) {
            winnerOldRating = player1OldRating;
            winnerOldRatingVisible = player1OldRatingVisible;
            winnerOldRatingDouble = player1OldRatingDouble;
            winnerOldMatches = player1OldMatches;
            loserOldMatches = player2OldMatches;
            winner = player1;
            loserOldRating = player2OldRating;
            loserOldRatingVisible = player2OldRatingVisible;
            loserOldRatingDouble = player2OldRatingDouble;
            loser = player2;
        } else if (winnerId == player2.getId().getId() || player2OldRatingDouble < player2NewRating.getMean()) {
            winnerOldRating = player2OldRating;
            winnerOldRatingVisible = player2OldRatingVisible;
            winnerOldRatingDouble = player2OldRatingDouble;
            winnerOldMatches = player2OldMatches;
            loserOldMatches = player1OldMatches;
            winner = player2;
            loserOldRating = player1OldRating;
            loserOldRatingVisible = player1OldRatingVisible;
            loserOldRatingDouble = player1OldRatingDouble;
            loser = player1;
        } else {
            return;
        }

        double winnerRatingDiff = winner.getId().getRatingVisible() - winnerOldRatingVisible;
        winnerAnnounce = winner.getId().getNameWithRatingAndChange(winnerRatingDiff);
        double loserRatingDiff = loser.getId().getRatingVisible() - loserOldRatingVisible;
        loserAnnounce = loser.getId().getNameWithRatingAndChange(loserRatingDiff);
        String winnerOldRank = PlayerManager.getInstance().getPlayerRating(winnerOldRatingVisible, winnerOldMatches);
        String winnerNewRank = PlayerManager.getInstance().getPlayerRating(winner.getId().getRatingVisible(), winner.getId().getMatches());
        String loserOldRank = PlayerManager.getInstance().getPlayerRating(loserOldRatingVisible, loserOldMatches);
        String loserNewRank = PlayerManager.getInstance().getPlayerRating(loser.getId().getRatingVisible(), loser.getId().getMatches());
        boolean winnerRankChanged = !winnerOldRank.equalsIgnoreCase(winnerNewRank);
        boolean loserRankChanged = !loserOldRank.equalsIgnoreCase(loserNewRank);

        String winnerMessage, loserMessage, drawMessage1, drawMessage2;
        if (winnerRankChanged) {
            winnerMessage = Messages.WIN_MESSAGE_CHANGE.getMessage();
            drawMessage1 = Messages.DRAW_MESSAGE_CHANGE.getMessage();
        } else {
            winnerMessage = Messages.WIN_MESSAGE.getMessage();
            drawMessage1 = Messages.DRAW_MESSAGE.getMessage();
        }
        if (loserRankChanged) {
            loserMessage = Messages.LOSE_MESSAGE_CHANGE.getMessage();
            drawMessage2 = Messages.DRAW_MESSAGE_CHANGE.getMessage();
        } else {
            loserMessage = Messages.LOSE_MESSAGE.getMessage();
            drawMessage2 = Messages.DRAW_MESSAGE.getMessage();
        }

        winnerMessage = winnerMessage.replaceAll("%OLD%", winnerOldRank)
                .replaceAll("%NEW%", winnerNewRank);
        loserMessage = loserMessage.replaceAll("%OLD%", loserOldRank)
                .replaceAll("%NEW%", loserNewRank);
        drawMessage1 = drawMessage1.replaceAll("%OLD%", winnerOldRank)
                .replaceAll("%NEW%", winnerNewRank);
        drawMessage2 = drawMessage2.replaceAll("%OLD%", loserOldRank)
                .replaceAll("%NEW%", loserNewRank);


        if (winnerId != player1.getId().getId()
                && winnerId != player2.getId().getId()) {
            announceMessage = announceMessageDraw.replaceAll("%WINNER%", winnerAnnounce).replaceAll("%LOSER%", loserAnnounce);
            winnerMessage = drawMessage1;
            loserMessage = drawMessage2;
        } else {
            announceMessage = announceMessageNotDraw.replaceAll("%WINNER%", winnerAnnounce).replaceAll("%LOSER%", loserAnnounce);
        }

        final String winnerMessageFinal = winnerMessage;
        final String loserMessageFinal = loserMessage;
        Bukkit.getScheduler().scheduleSyncDelayedTask(RankedPvP.getInstance(), () -> {
            PlayerManager.getInstance().sendMessageToPlayer(winner.getId().getName(), winnerMessageFinal, true);
            PlayerManager.getInstance().sendMessageToPlayer(loser.getId().getName(), loserMessageFinal, true);
        }, 60);


        Database.updatePlayer(player1.getId());
        Database.updatePlayer(player2.getId());

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
        player1.getId().getMatchHistory().add(result);
        player2.getId().getMatchHistory().add(result);
        Database.insertMatchResult(result);

        final String announceMessageFinal = announceMessage;
        Bukkit.getScheduler().scheduleSyncDelayedTask(RankedPvP.getInstance(), () -> {
            PlayerManager.getInstance().announce(announceMessageFinal, true);
        }, 60L);
    }

    public void gameEndedOld (TwoPlayerGame game, int winnerId) {
        //PvPArena.log("game ended calculations win: " + winnerId);
        de.gesundkrank.jskills.Player<ArenaPlayer> player1 = new de.gesundkrank.jskills.Player<>(game.playerOne);
        de.gesundkrank.jskills.Player<ArenaPlayer> player2 = new de.gesundkrank.jskills.Player<>(game.playerTwo);
        Team team1 = new Team(player1, new Rating(player1.getId().getRating(), player1.getId().getDeviation()));
        Team team2 = new Team(player2, new Rating(player2.getId().getRating(), player2.getId().getDeviation()));

        PlayerManager.getInstance().applyCooldown(player1.getId());
        PlayerManager.getInstance().applyCooldown(player2.getId());

        double player1OldRatingDouble = player1.getId().getRating();
        double player2OldRatingDouble = player2.getId().getRating();
        double player1OldDeviation = player1.getId().getDeviation();
        double player2OldDeviation = player2.getId().getDeviation();
        int player1OldRating = player1.getId().getRatingRound();
        int player2OldRating = player2.getId().getRatingRound();
        int player1OldMatches = player1.getId().getMatches();
        int player2OldMatches = player2.getId().getMatches();

        String winnerMessage = Messages.WIN_MESSAGE_CHANGE.getMessage();
        String loserMessage = Messages.LOSE_MESSAGE_CHANGE.getMessage();
        String drawMessage1 = Messages.DRAW_MESSAGE_CHANGE.getMessage();
        String drawMessage2 = Messages.DRAW_MESSAGE_CHANGE.getMessage();
        String announceMessageNotDraw = Messages.ANNOUNCE_NOT_DRAW.getMessage();
        String announceMessageDraw = Messages.ANNOUNCE_DRAW.getMessage();
        String firstMessage, secondMessage;
        String winnerAnnounce, loserAnnounce, announceMessage;

        TwoPlayerTrueSkillCalculator calculator = new TwoPlayerTrueSkillCalculator();
        Map<IPlayer, Rating> newRatings;
        if (winnerId == player1.getId().getId()) {
            newRatings = calculator.calculateNewRatings(game.gameInfo, Team.concat(team1, team2), 1, 2);
            player1.getId().addWin();
            player2.getId().addLoss();
        } else if (winnerId == player2.getId().getId()) {
            newRatings = calculator.calculateNewRatings(game.gameInfo, Team.concat(team1, team2), 2, 1);
            player2.getId().addWin();
            player1.getId().addLoss();
        } else {
            newRatings = calculator.calculateNewRatings(game.gameInfo, Team.concat(team1, team2), 1, 1);
            player1.getId().addDraw();
            player2.getId().addDraw();
        }
        Rating player1NewRating = newRatings.get(player1);
        Rating player2NewRating = newRatings.get(player2);
        player1.getId().setRating(player1NewRating);
        player2.getId().setRating(player2NewRating);

        int winnerOldRating, loserOldRating, winnerOldMatches, loserOldMatches;
        double winnerOldRatingDouble, loserOldRatingDouble;
        Player<ArenaPlayer> winner, loser;

        if (winnerId == player1.getId().getId() || player1OldRatingDouble < player1NewRating.getMean()) {
            winnerOldRating = player1OldRating;
            winnerOldRatingDouble = player1OldRatingDouble;
            winnerOldMatches = player1OldMatches;
            loserOldMatches = player2OldMatches;
            winner = player1;
            loserOldRating = player2OldRating;
            loserOldRatingDouble = player2OldRatingDouble;
            loser = player2;
        } else if (winnerId == player2.getId().getId() || player2OldRatingDouble < player2NewRating.getMean()) {
            winnerOldRating = player2OldRating;
            winnerOldRatingDouble = player2OldRatingDouble;
            winnerOldMatches = player2OldMatches;
            loserOldMatches = player1OldMatches;
            winner = player2;
            loserOldRating = player1OldRating;
            loserOldRatingDouble = player1OldRatingDouble;
            loser = player1;
        } else {
            return;
        }

        winnerMessage = winnerMessage.replaceAll("%OLD%", PlayerManager.getInstance().getPlayerRating(winnerOldRating, winnerOldMatches))
                .replaceAll("%NEW%", PlayerManager.getInstance().getPlayerRating(winner.getId().getRatingRound(), winner.getId().getMatches()));
        loserMessage = loserMessage.replaceAll("%OLD%", PlayerManager.getInstance().getPlayerRating(loserOldRating, loserOldMatches))
                .replaceAll("%NEW%", PlayerManager.getInstance().getPlayerRating(loser.getId().getRatingRound(), loser.getId().getMatches()));
        drawMessage1 = drawMessage1.replaceAll("%OLD%", PlayerManager.getInstance().getPlayerRating(player1OldRating, player1OldMatches))
                .replaceAll("%NEW%", PlayerManager.getInstance().getPlayerRating(player1.getId().getRatingRound(), player1.getId().getMatches()));
        drawMessage2 = drawMessage2.replaceAll("%OLD%", PlayerManager.getInstance().getPlayerRating(player2OldRating, player2OldMatches))
                .replaceAll("%NEW%", PlayerManager.getInstance().getPlayerRating(player2.getId().getRatingRound(), player2.getId().getMatches()));


        double winnerRatingDiff = winner.getId().getRating() - winnerOldRatingDouble;
        winnerAnnounce = winner.getId().getNameWithRatingAndChange(winnerRatingDiff);
        double loserRatingDiff = loser.getId().getRating() - loserOldRatingDouble;
        loserAnnounce = loser.getId().getNameWithRatingAndChange(loserRatingDiff);

        announceMessage = announceMessageNotDraw.replaceAll("%WINNER%", winnerAnnounce).replaceAll("%LOSER%", loserAnnounce);

        if (winnerId == player1.getId().getId()) {
            firstMessage = winnerMessage;
            secondMessage = loserMessage;
        } else if (winnerId == player2.getId().getId()) {
            firstMessage = loserMessage;
            secondMessage = winnerMessage;
        } else {
            announceMessage = announceMessageDraw.replaceAll("%WINNER%", winnerAnnounce).replaceAll("%LOSER%", loserAnnounce);
            firstMessage = drawMessage1;
            secondMessage = drawMessage2;
        }

        Bukkit.getScheduler().scheduleSyncDelayedTask(RankedPvP.getInstance(), () -> {
            PlayerManager.getInstance().sendMessageToPlayer(player1.getId().getName(), firstMessage, true);
            PlayerManager.getInstance().sendMessageToPlayer(player2.getId().getName(), secondMessage, true);
        }, 60);


        Database.updatePlayer(player1.getId());
        Database.updatePlayer(player2.getId());

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
        Database.insertMatchResult(result);

        final String announceMessageFinal = announceMessage;
        Bukkit.getScheduler().scheduleSyncDelayedTask(RankedPvP.getInstance(), () -> {
            PlayerManager.getInstance().announce(announceMessageFinal, true);
        }, 60L);
    }

    public HashMap<ArenaPlayer, Game> getInProgress () {
        return inProgress;
    }

    public HashMap<ArenaPlayer, Game> getWaitingForAccept () {
        return waitingForAccept;
    }

    public void tryAcceptingGame (String playerName) {
        if (!RankedPvP.IS_MASTER) {
            DataPacket.newBuilder()
                    .addReceiver(RankedPvP.MASTER_ID)
                    .action(Action.GAME_ACCEPT)
                    .string(playerName)
                    .buildPacket()
                    .send();
            return;
        }

        ArenaPlayer arenaPlayer = PlayerManager.getInstance().getOrCreatePlayer(playerName);
        if (!waitingForAccept.containsKey(arenaPlayer)) {
            Messages.NOT_PART_OF_GAME.sendTo(playerName);
            return;
        }

        TwoPlayerGame game = (TwoPlayerGame) waitingForAccept.get(arenaPlayer);
        waitingForAccept.remove(arenaPlayer);
        String messageIfOtherDidntAccept = Messages.ACCEPTED.getMessage();
        ArenaPlayer other = game.getOtherPlayer(arenaPlayer);
        if (game.playerOne.equals(arenaPlayer)) {
            game.oneAccepted = true;
        } else if (game.playerTwo.equals(arenaPlayer)) {
            game.twoAccepted = true;
        }
        if (waitingForAccept.containsKey(other)) {
            PlayerManager.getInstance().sendMessageToPlayer(arenaPlayer.getName(), messageIfOtherDidntAccept, true);
        } else {
            inProgress.put(game.playerOne, game);
            inProgress.put(game.playerTwo, game);
            game.teleportBothToLobby();
        }
    }

    public void toggleJoin (String playerName, EventType eventType) {
        ArenaPlayer arenaPlayer = PlayerManager.getInstance().getOrCreatePlayer(playerName);

        if (playerQueue.get(eventType).contains(arenaPlayer)) {
            tryLeavingQueue(playerName, eventType);
        } else {
            tryJoiningQueue(playerName, eventType);
        }
    }

    public void tryLeavingQueue (String playerName, EventType eventType) {
        if (!RankedPvP.IS_MASTER) {
            DataPacket.newBuilder()
                    .addReceiver(RankedPvP.MASTER_ID)
                    .action(Action.PLAYER_ARENA_LEAVE)
                    .string(playerName)
                    .string2(eventType.toString())
                    .buildPacket()
                    .send();
            return;
        }

        ArenaPlayer arenaPlayer = PlayerManager.getInstance().getOrCreatePlayer(playerName);

        if (!playerQueue.get(eventType).contains(arenaPlayer)) {
            Messages.NOT_IN_QUEUE.sendTo(arenaPlayer.getName());
            return;
        }

        playerQueue.get(eventType).remove(arenaPlayer);

        Messages.LEFT_QUEUE.sendTo(arenaPlayer.getName());
    }


    public void tryJoiningQueue (String playerName, EventType eventType) {
        if (!RankedPvP.IS_MASTER) {
            DataPacket.newBuilder()
                    .addReceiver(RankedPvP.MASTER_ID)
                    .action(Action.PLAYER_ARENA_JOIN)
                    .string(playerName)
                    .string2(eventType.toString())
                    .buildPacket()
                    .send();
            return;
        }

        ArenaPlayer arenaPlayer = PlayerManager.getInstance().getOrCreatePlayer(playerName);

        if (waitingForAccept.containsKey(arenaPlayer) || inProgress.containsKey(arenaPlayer)) {
            return;
        }

        if (playerQueue.get(eventType).contains(arenaPlayer)) {
            Messages.ALREADY_WAITING.sendTo(arenaPlayer.getName(), "%EVENT%:" + eventType.getNiceName());
            return;
        }


        if (PlayerManager.getInstance().isOnCooldown(arenaPlayer)) {
            Messages.COOLDOWN.sendTo(arenaPlayer.getName(), "%TIME%:" + Utils.minutesString(PlayerManager.getInstance().getCooldown(arenaPlayer)));
            return;
        }

        arenaPlayer.resetTimeInQueue();
        playerQueue.get(eventType).add(arenaPlayer);
        checkQueue.put(eventType, QUEUE_CHECK_DELAY);

        Messages.WAIT_FOR_OPPONENT.sendTo(arenaPlayer.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeathEvent (PlayerDeathEvent event) {
        Optional<ArenaPlayer> player = PlayerManager.getInstance().getPlayerIfExists(event.getEntity().getName());
        if (!player.isPresent()
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
        Optional<ArenaPlayer> player = PlayerManager.getInstance().getPlayerIfExists(event.getEntity().getName());
        if (!player.isPresent()) {
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
        Optional<ArenaPlayer> player1 = PlayerManager.getInstance().getPlayerIfExists(event.getDamager().getName());
        Optional<ArenaPlayer> player2 = PlayerManager.getInstance().getPlayerIfExists(event.getEntity().getName());

        if (player1.isPresent() && inProgress.containsKey(player1.get())) {
            if (!player2.isPresent() || !inProgress.containsKey(player2.get()) || !inProgress.get(player1.get()).equals(inProgress.get(player2.get()))) {
                event.setCancelled(true);
            }
        } else if (player2.isPresent() && inProgress.containsKey(player2.get())) {
            if (!player1.isPresent() || !inProgress.containsKey(player1.get()) || !inProgress.get(player2.get()).equals(inProgress.get(player1.get()))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEvent (PlayerInteractEvent event) {
        Optional<ArenaPlayer> player = PlayerManager.getInstance().getPlayerIfExists(event.getPlayer().getName());
        if (!player.isPresent() || !inProgress.containsKey(player.get()))
            return;

        TwoPlayerGame game = (TwoPlayerGame) inProgress.get(player.get());
        if (!game.getArena().isInArena(player.get()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItemEvent (PlayerDropItemEvent event) {
        Optional<ArenaPlayer> player = PlayerManager.getInstance().getPlayerIfExists(event.getPlayer().getName());
        if (!player.isPresent() || !inProgress.containsKey(player.get()))
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

    public ArrayList<Game> getGames () {
        return games;
    }

    public HashMap<EventType, LinkedList<ArenaPlayer>> getPlayerQueue () {
        return playerQueue;
    }

    public Warp getSpawn () {
        return spawn;
    }
}

