package dev.tonysp.rankedpvp.game;

import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.Utils;
import dev.tonysp.rankedpvp.arenas.Arena;
import dev.tonysp.rankedpvp.arenas.ArenaManager;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import dev.tonysp.rankedpvp.players.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
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
                    PlayerManager.getInstance().sendAcceptMessageToPlayer(playerOne.getName(), timeRemaining, true);
                }
                if (!twoAccepted) {
                    PlayerManager.getInstance().sendAcceptMessageToPlayer(playerTwo.getName(), timeRemaining, true);
                }
            }
            timeToAccept --;
            if (timeToAccept <= 0) {
                String message = Messages.DID_NOT_ACCEPT.getMessage();
                String message2 = Messages.OPPONENT_DID_NOT_ACCEPT.getMessage();
                if (!oneAccepted) {
                    PlayerManager.getInstance().sendMessageToPlayer(playerOne.getName(), message, true);
                    if (twoAccepted) {
                        PlayerManager.getInstance().sendMessageToPlayer(playerTwo.getName(), message2, true);
                    }
                }
                if (!twoAccepted) {
                    PlayerManager.getInstance().sendMessageToPlayer(playerTwo.getName(), message, true);
                    if (oneAccepted) {
                        PlayerManager.getInstance().sendMessageToPlayer(playerOne.getName(), message2, true);
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
                    playerOne.backupInventory();
                    playerTwo.backupInventory();

                    arena.eventType.runStartCommands(playerOne.getName());
                    arena.eventType.runStartCommands(playerTwo.getName());
                }

                timeToStart--;
                Player playerOnePlayer = Bukkit.getPlayer(playerOne.getName());
                Player playerTwoPlayer = Bukkit.getPlayer(playerTwo.getName());
                if (timeToStart == 0) {
                    playerOnePlayer.playSound(playerOnePlayer.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
                    playerTwoPlayer.playSound(playerTwoPlayer.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
                    Messages.BATTLE_STARTED.sendTo(playerOne.getName());
                    Messages.BATTLE_STARTED.sendTo(playerTwo.getName());
                    startMatch();
                } else if (timeToStart % 4 == 0) {
                    playerOnePlayer.playSound(playerOnePlayer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    playerTwoPlayer.playSound(playerTwoPlayer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    Messages.BATTLE_STARTING_IN.sendTo(playerOne.getName(), "%TIME%:" + Utils.secondString(timeToStart / 4));
                    Messages.BATTLE_STARTING_IN.sendTo(playerTwo.getName(), "%TIME%:" + Utils.secondString(timeToStart / 4));
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
                    PlayerManager.getInstance().savePlayerLocationAndTeleport(playerOne.getName(), true);
                }
                if (!secondInArena) {
                    PlayerManager.getInstance().savePlayerLocationAndTeleport(playerTwo.getName(), true);
                }
            }
            timeToTeleport --;
        } else if (gameState == GameState.IN_PROGRESS) {
            timeRemaining --;

            boolean firstInArena = arena.isInArena(playerOne);
            boolean secondInArena = arena.isInArena(playerTwo);
            if (timeRemaining % (5*4) == 0) {
                if (timeRemaining != 0 && !firstInArena && secondInArena) {
                    Messages.OPPONENT_NOT_IN_ARENA.sendTo(playerTwo.getName(), "%TIME%:" + Utils.secondString(timeToReturnOne / 4));
                }
                if (timeRemaining != 0 && !secondInArena && firstInArena) {
                    Messages.OPPONENT_NOT_IN_ARENA.sendTo(playerOne.getName(), "%TIME%:" + Utils.secondString(timeToReturnTwo / 4));
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
                PlayerManager.getInstance().sendMessageToPlayer(playerOne.getName(), message, true);
                PlayerManager.getInstance().sendMessageToPlayer(playerTwo.getName(), message, true);
            }
            if (timeRemaining == 30*4) {
                message = message.replaceAll("%TIME%", "zbyva 30 sekund");
                PlayerManager.getInstance().sendMessageToPlayer(playerOne.getName(), message, true);
                PlayerManager.getInstance().sendMessageToPlayer(playerTwo.getName(), message, true);
            }

            if (timeRemaining == 0) {
                endMatch();
            }
        } else if (gameState == GameState.FAILED) {
            Messages.BATTLE_CANCELLED.sendTo(playerOne.getName());
            Messages.BATTLE_CANCELLED.sendTo(playerTwo.getName());
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

        bukkitPlayerOne.setHealth(bukkitPlayerOne.getAttribute(Attribute.GENERIC_MAX_HEALTH).getDefaultValue());
        bukkitPlayerTwo.setHealth(bukkitPlayerOne.getAttribute(Attribute.GENERIC_MAX_HEALTH).getDefaultValue());

        gameState = GameState.IN_PROGRESS;
        arena.openEntrance();
    }

    @Override
    public void endMatch () {
        GameManager.getInstance().getInProgress().remove(playerOne);
        GameManager.getInstance().getInProgress().remove(playerTwo);

        playerOne.restoreInventory();
        playerTwo.restoreInventory();

        if (oneBackLocation != null) {
            oneBackLocation.warpPlayer(playerOne.getName(), true);
        }
        if (twoBackLocation != null) {
            twoBackLocation.warpPlayer(playerTwo.getName(), true);
        }

        GameManager.getInstance().getWaitingForAccept().remove(playerOne);
        GameManager.getInstance().getWaitingForAccept().remove(playerTwo);
        ArenaManager.getInstance().unlockArena(arena);
        if (gameState != GameState.FAILED) {
            gameState = GameState.ENDED;
            GameManager.getInstance().gameEnded(this, winnerId);
        }
        setCancelled(true);
    }

    public void teleportBothToLobby () {
        gameState = GameState.IN_LOBBY;
        PlayerManager.getInstance().savePlayerLocationAndTeleport(playerOne.getName(), true);
        PlayerManager.getInstance().savePlayerLocationAndTeleport(playerTwo.getName(), true);
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

