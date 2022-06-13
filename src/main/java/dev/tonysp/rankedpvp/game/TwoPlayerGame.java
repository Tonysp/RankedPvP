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

import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.Utils;
import dev.tonysp.rankedpvp.arenas.Arena;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class TwoPlayerGame extends Game {

    public ArenaPlayer playerOne, playerTwo;
    boolean oneAccepted = false, twoAccepted = false;
    public Warp oneBackLocation, twoBackLocation;
    int timeToReturnOne = 60*4;
    int timeToReturnTwo = 60*4;
    int winnerId = -1;

    public TwoPlayerGame (Arena arena, ArenaPlayer playerOne, ArenaPlayer playerTwo) {
        super(arena);
        this.playerOne = playerOne;
        this.playerTwo = playerTwo;
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
                String message = Messages.DID_NOT_ACCEPT.getMessage();
                String message2 = Messages.OPPONENT_DID_NOT_ACCEPT.getMessage();
                if (!oneAccepted) {
                    plugin.players().sendMessageToPlayer(playerOne.getUuid(), message, true);
                    if (twoAccepted) {
                        plugin.players().sendMessageToPlayer(playerTwo.getUuid(), message2, true);
                    }
                }
                if (!twoAccepted) {
                    plugin.players().sendMessageToPlayer(playerTwo.getUuid(), message, true);
                    if (oneAccepted) {
                        plugin.players().sendMessageToPlayer(playerOne.getUuid(), message2, true);
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
                    Messages.BATTLE_STARTING_IN.sendTo(playerOne.getUuid(), "%TIME%:" + Utils.secondString(timeToStart / 4));
                    Messages.BATTLE_STARTING_IN.sendTo(playerTwo.getUuid(), "%TIME%:" + Utils.secondString(timeToStart / 4));
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
                if (timeRemaining != 0 && !firstInArena && secondInArena) {
                    Messages.OPPONENT_NOT_IN_ARENA.sendTo(playerTwo.getUuid(), "%TIME%:" + Utils.secondString(timeToReturnOne / 4));
                }
                if (timeRemaining != 0 && !secondInArena && firstInArena) {
                    Messages.OPPONENT_NOT_IN_ARENA.sendTo(playerOne.getUuid(), "%TIME%:" + Utils.secondString(timeToReturnTwo / 4));
                }
            }

            if (!firstInArena) {
                timeToReturnOne --;
            }
            if (!secondInArena) {
                timeToReturnTwo --;
            }

            if (timeToReturnOne <= 0) {
                gameState = GameState.ENDED;
                if (secondInArena)
                    winnerId = playerTwo.getId();
                endMatch();
                return;
            }

            if (timeToReturnTwo <= 0) {
                gameState = GameState.ENDED;
                if (firstInArena)
                    winnerId = playerOne.getId();
                endMatch();
                return;
            }

            String message = Messages.TIME_REMAINING.getMessage();
            if (timeRemaining % (60*4) == 0 && timeRemaining != 0) {
                if (timeRemaining == 60*4) {
                    message = message.replaceAll("%TIME%", "zbyva 1 minuta");
                } else if (timeRemaining == 120*4) {
                    message = message.replaceAll("%TIME%", "zbyvaji 2 minuty");
                }
                plugin.players().sendMessageToPlayer(playerOne.getUuid(), message, true);
                plugin.players().sendMessageToPlayer(playerTwo.getUuid(), message, true);
            }
            if (timeRemaining == 30*4) {
                message = message.replaceAll("%TIME%", "zbyva 30 sekund");
                plugin.players().sendMessageToPlayer(playerOne.getUuid(), message, true);
                plugin.players().sendMessageToPlayer(playerTwo.getUuid(), message, true);
            }

            if (timeRemaining == 0) {
                endMatch();
            }
        } else if (gameState == GameState.FAILED) {
            Messages.BATTLE_CANCELLED.sendTo(playerOne.getUuid());
            Messages.BATTLE_CANCELLED.sendTo(playerTwo.getUuid());
            oneBackLocation.warpPlayer(playerOne.getName(), true);
            twoBackLocation.warpPlayer(playerTwo.getName(), true);
            setCancelled(true);
        }
    }

    public void playerDied (ArenaPlayer player) {
        if (player.equals(playerOne)) {
            winnerId = playerTwo.getId();
        } else {
            winnerId = playerOne.getId();
        }

        endMatch();
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
        plugin.games().getInProgress().remove(playerOne);
        plugin.games().getInProgress().remove(playerTwo);

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


        if (oneBackLocation != null) {
            oneBackLocation.warpPlayer(playerOne.getName(), true);
        }
        if (twoBackLocation != null) {
            twoBackLocation.warpPlayer(playerTwo.getName(), true);
        }

        plugin.games().getWaitingForAccept().remove(playerOne);
        plugin.games().getWaitingForAccept().remove(playerTwo);
        plugin.arenas().unlockArena(arena);
        if (gameState != GameState.FAILED) {
            gameState = GameState.ENDED;
            plugin.games().gameEnded(this, winnerId);
        }
        setCancelled(true);
    }

    public void teleportBothToLobby () {
        gameState = GameState.IN_LOBBY;
        plugin.players().savePlayerLocationAndTeleport(playerOne.getUuid(), true);
        plugin.players().savePlayerLocationAndTeleport(playerTwo.getUuid(), true);
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

