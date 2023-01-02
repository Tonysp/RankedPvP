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
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.TwoPlayerTrueSkillCalculator;
import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.Utils;
import dev.tonysp.rankedpvp.arenas.Arena;
import dev.tonysp.rankedpvp.game.result.TwoTeamGameResult;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class TwoPlayerGame extends Game {

    public ArenaPlayer playerOne, playerTwo;
    boolean oneAccepted = false, twoAccepted = false;
    int oneTimeToReturn = 60*4;
    int twoTimeToReturn = 60*4;
    int winnerId = -1;

    public TwoPlayerGame (Arena arena, List<ArenaPlayer> participants) {
        super(arena, participants, EventType.ONE_VS_ONE);
        this.playerOne = participants.get(0);
        this.playerTwo = participants.get(1);
    }

    @Override
    public void tick () {
        if (gameState == GameState.WAITING_TO_ACCEPT) {
            if (timeToAccept % (5*4) == 0) {
                int timeRemaining = timeToAccept / 4;
                if (!oneAccepted) {
                    plugin.players().sendAcceptMessageToPlayer(playerOne.getName(), timeRemaining, true);
                }
                if (!twoAccepted) {
                    plugin.players().sendAcceptMessageToPlayer(playerTwo.getName(), timeRemaining, true);
                }
            }
            timeToAccept --;
            if (timeToAccept <= 0) {
                TextComponent message = Messages.DID_NOT_ACCEPT.getMessage();
                TextComponent message2 = Messages.OPPONENT_DID_NOT_ACCEPT.getMessage();
                if (!oneAccepted) {
                    Messages.DID_NOT_ACCEPT.sendTo(playerOne.getUuid());
                    if (twoAccepted) {
                        Messages.OPPONENT_DID_NOT_ACCEPT.sendTo(playerTwo.getUuid());
                    }
                }
                if (!twoAccepted) {
                    Messages.DID_NOT_ACCEPT.sendTo(playerTwo.getUuid());
                    if (oneAccepted) {
                        Messages.OPPONENT_DID_NOT_ACCEPT.sendTo(playerOne.getUuid());
                    }
                }

                gameState = GameState.FAILED;
                endMatch();
            }
        } else if (gameState == GameState.IN_LOBBY) {
            boolean firstInArena = arena.isInArena(playerOne);
            boolean secondInArena = arena.isInArena(playerTwo);

            if (firstInArena && secondInArena) {
                if (timeToStart == timeToStartFull) {
                    if (getArena().eventType.isBackupInventory()) {
                        playerOne.backupInventory(false);
                        playerTwo.backupInventory(false);
                    }
                    if (getArena().eventType.isBackupStatusEffects()) {
                        playerOne.backupStatusEffects(false);
                        playerTwo.backupStatusEffects(false);
                    }

                    arena.eventType.runStartCommands(playerOne.getName());
                    arena.eventType.runStartCommands(playerTwo.getName());
                }

                timeToStart--;
                Player playerOnePlayer = Bukkit.getPlayer(playerOne.getName());
                Player playerTwoPlayer = Bukkit.getPlayer(playerTwo.getName());
                if (timeToStart == 0) {
                    if (playerOnePlayer != null)
                        playerOnePlayer.playSound(playerOnePlayer.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
                    if (playerTwoPlayer != null)
                        playerTwoPlayer.playSound(playerTwoPlayer.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
                    Messages.BATTLE_STARTED.sendTo(playerOne.getUuid());
                    Messages.BATTLE_STARTED.sendTo(playerTwo.getUuid());
                    startMatch();
                } else if (timeToStart % 4 == 0) {
                    if (playerOnePlayer != null)
                        playerOnePlayer.playSound(playerOnePlayer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    if (playerTwoPlayer != null)
                        playerTwoPlayer.playSound(playerTwoPlayer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

                    TextReplacementConfig replacement = TextReplacementConfig.builder()
                            .match("%TIME%")
                            .replacement(Utils.secondString(timeToStart / 4))
                            .build();
                    Messages.BATTLE_STARTING_IN.sendTo(playerOne.getUuid(), replacement);
                    Messages.BATTLE_STARTING_IN.sendTo(playerTwo.getUuid(), replacement);
                }
                return;
            }

            if (timeToTeleport == 0) {
                gameState = GameState.FAILED;
                endMatch();
                return;
            }

            if (timeToTeleport % (7*4) == 0 && timeToTeleport <= 21*4) {
                if (!firstInArena) {
                    plugin.players().savePlayerLocationAndTeleport(playerOne.getUuid(), true);
                }
                if (!secondInArena) {
                    plugin.players().savePlayerLocationAndTeleport(playerTwo.getUuid(), true);
                }
            }
            timeToTeleport --;
        } else if (gameState == GameState.IN_PROGRESS) {
            timeRemaining --;

            boolean firstInArena = arena.isInArena(playerOne);
            boolean secondInArena = arena.isInArena(playerTwo);
            if (timeRemaining % (5*4) == 0) {
                TextReplacementConfig replacement = TextReplacementConfig.builder()
                        .match("%TIME%")
                        .replacement(Utils.secondString(oneTimeToReturn / 4))
                        .build();
                if (timeRemaining != 0 && !firstInArena && secondInArena) {
                    Messages.OPPONENT_NOT_IN_ARENA.sendTo(playerTwo.getUuid(), replacement);
                }
                if (timeRemaining != 0 && !secondInArena && firstInArena) {
                    Messages.OPPONENT_NOT_IN_ARENA.sendTo(playerOne.getUuid(), replacement);
                }
            }

            if (!firstInArena) {
                oneTimeToReturn--;
            }
            if (!secondInArena) {
                twoTimeToReturn--;
            }

            if (oneTimeToReturn <= 0) {
                gameState = GameState.ENDED;
                if (secondInArena)
                    winnerId = playerTwo.getId();
                endMatch();
                return;
            }

            if (twoTimeToReturn <= 0) {
                gameState = GameState.ENDED;
                if (firstInArena)
                    winnerId = playerOne.getId();
                endMatch();
                return;
            }

            String timeVariable;
            if (timeRemaining % (60*4) == 0 && timeRemaining != 0) {
                TextReplacementConfig replacement = TextReplacementConfig.builder()
                        .match("%TIME%")
                        .replacement(Utils.minutesString(timeRemaining / (60*4)))
                        .build();
                Messages.TIME_REMAINING.sendTo(playerOne.getUuid(), replacement);
                Messages.TIME_REMAINING.sendTo(playerTwo.getUuid(), replacement);
            }
            if (timeRemaining == 30*4) {
                TextReplacementConfig replacement = TextReplacementConfig.builder()
                        .match("%TIME%")
                        .replacement(Utils.secondString(30))
                        .build();
                Messages.TIME_REMAINING.sendTo(playerOne.getUuid(), replacement);
                Messages.TIME_REMAINING.sendTo(playerTwo.getUuid(), replacement);
            }

            if (timeRemaining == 0) {
                endMatch();
            }
        } else if (gameState == GameState.FAILED) {
            Messages.BATTLE_CANCELLED.sendTo(playerOne.getUuid());
            Messages.BATTLE_CANCELLED.sendTo(playerTwo.getUuid());
            playerOne.warpToReturnLocation();
            playerTwo.warpToReturnLocation();
        }
    }

    public void startMatch () {
        Player bukkitPlayerOne = Bukkit.getPlayer(playerOne.getName());
        Player bukkitPlayerTwo = Bukkit.getPlayer(playerTwo.getName());
        if (bukkitPlayerOne == null
                || bukkitPlayerTwo == null
                || bukkitPlayerOne.isDead()
                || bukkitPlayerTwo.isDead()
                || !bukkitPlayerOne.isOnline()
                || !bukkitPlayerTwo.isOnline()) {
            return;
        }

        playerOne.heal();
        playerTwo.heal();

        gameState = GameState.IN_PROGRESS;
        arena.openEntrance();
    }

    @Override
    public void endMatch () {
        plugin.games().getPlayersInGame().remove(playerOne);
        plugin.games().getPlayersInGame().remove(playerTwo);

        playerOne.heal();
        playerTwo.heal();

        if (getArena().eventType.isBackupInventory()) {
            playerOne.restoreInventory(false);
            playerTwo.restoreInventory(false);
        }
        if (getArena().eventType.isBackupStatusEffects()) {
            playerOne.restoreStatusEffects(false);
            playerTwo.restoreStatusEffects(false);
        }

        playerOne.warpToReturnLocation();
        playerTwo.warpToReturnLocation();

        plugin.games().getWaitingForAccept().remove(playerOne);
        plugin.games().getWaitingForAccept().remove(playerTwo);
        plugin.arenas().unlockArena(arena);

        if (gameState == GameState.FAILED)
            return;

        gameState = GameState.ENDED;

        applyCooldowns();
        calculateAndAnnounceResult();
        result.save(getPlayers());
        updatePlayers();
    }

    @Override
    public void accepted (ArenaPlayer player) {
        ArenaPlayer other = getOtherPlayer(player);
        if (playerOne.equals(player)) {
            oneAccepted = true;
        } else if (playerTwo.equals(player)) {
            twoAccepted = true;
        }
        if (plugin.games().isWaitingToAccept(other)) {
            Messages.ACCEPTED.sendTo(player.getUuid());
        } else {
            plugin.games().getPlayersInGame().put(playerOne, this);
            plugin.games().getPlayersInGame().put(playerTwo, this);
            teleportAllPlayersToLobby();
        }
    }

    @Override
    public void teleportAllPlayersToLobby () {
        gameState = GameState.IN_LOBBY;
        plugin.players().savePlayerLocationAndTeleport(playerOne.getUuid(), true);
        plugin.players().savePlayerLocationAndTeleport(playerTwo.getUuid(), true);
    }

    @Override
    public void processDeath (ArenaPlayer player) {
        if (player.equals(playerOne)) {
            winnerId = playerTwo.getId();
        } else {
            winnerId = playerOne.getId();
        }

        endMatch();
    }

    @Override
    public void teleportToLobby (ArenaPlayer player) {
        if (playerOne.equals(player)) {
            teleportPlayerOneToLobby();
        } else {
            teleportPlayerTwoToLobby();
        }
    }

    public void applyCooldowns () {
        getPlayers().forEach(eventType::applyCooldown);
    }

    public void calculateAndAnnounceResult () {
        de.gesundkrank.jskills.Player<ArenaPlayer> player1 = new de.gesundkrank.jskills.Player<>(playerOne);
        de.gesundkrank.jskills.Player<ArenaPlayer> player2 = new de.gesundkrank.jskills.Player<>(playerTwo);
        Team team1 = new Team(player1, new Rating(player1.getId().getRating(), player1.getId().getDeviation()));
        Team team2 = new Team(player2, new Rating(player2.getId().getRating(), player2.getId().getDeviation()));

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
            newRatings = calculator.calculateNewRatings(gameInfo, Team.concat(team1, team2), 1, 2);
        } else if (winnerId == player2.getId().getId()) {
            newRatings = calculator.calculateNewRatings(gameInfo, Team.concat(team1, team2), 2, 1);
        } else {
            newRatings = calculator.calculateNewRatings(gameInfo, Team.concat(team1, team2), 1, 1);
        }

        Rating player1NewRating = newRatings.get(player1);
        Rating player2NewRating = newRatings.get(player2);
        player1.getId().setRating(player1NewRating);
        player2.getId().setRating(player2NewRating);

        TwoTeamGameResult result = new TwoTeamGameResult();
        this.result = result;
        result.teamOne = player1.getId().getId();
        result.teamTwo = player2.getId().getId();
        result.winnerTeam = winnerId;
        result.arena = arena.name;
        result.eventType = arena.eventType.toString();
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

        int winnerOldRatingVisible, loserOldRatingVisible, winnerOldMatches, loserOldMatches;
        de.gesundkrank.jskills.Player<ArenaPlayer> winner, loser;

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
        winnerReplacement.add(TextReplacementConfig.builder().match("%OLD%").replacement(winnerOldRank).build());
        winnerReplacement.add(TextReplacementConfig.builder().match("%NEW%").replacement(winnerNewRank).build());
        loserReplacement.add(TextReplacementConfig.builder().match("%OLD%").replacement(loserOldRank).build());
        loserReplacement.add(TextReplacementConfig.builder().match("%NEW%").replacement(loserNewRank).build());
        announceReplacement.add(TextReplacementConfig.builder().match("%WINNER%").replacement(winnerAnnounce).build());
        announceReplacement.add(TextReplacementConfig.builder().match("%LOSER%").replacement(loserAnnounce).build());


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

        final TextComponent announceMessageFinal = announceMessage;
        Bukkit.getScheduler().scheduleSyncDelayedTask(RankedPvP.getInstance(), () -> {
            plugin.players().announce(announceMessageFinal, true);
        }, 60L);
    }

    public void teleportPlayerOneToLobby () {
        int index = ThreadLocalRandom.current().nextInt(arena.teamOneWarps.size());
        arena.teamOneWarps.get(index).warpPlayer(playerOne.getName(), true);
    }

    public void teleportPlayerTwoToLobby () {
        int index = ThreadLocalRandom.current().nextInt(arena.teamTwoWarps.size());
        arena.teamTwoWarps.get(index).warpPlayer(playerTwo.getName(), true);
    }

    public ArenaPlayer getOtherPlayer (ArenaPlayer player) {
        if (playerOne.equals(player)) {
            return playerTwo;
        } else {
            return playerOne;
        }
    }
}

