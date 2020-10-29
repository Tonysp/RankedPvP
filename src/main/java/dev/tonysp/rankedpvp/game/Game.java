package dev.tonysp.rankedpvp.game;

import de.gesundkrank.jskills.GameInfo;
import dev.tonysp.rankedpvp.arenas.Arena;
import dev.tonysp.rankedpvp.players.EntityWithRating;

public class Game {

    Arena arena;
    GameState gameState;
    GameInfo gameInfo;
    boolean cancelled = false;

    int timeRemaining = 180 * 4;
    int timeToAccept = 30 * 4;
    int timeToTeleport = 30 * 4;

    int timeToStart = 5 * 4;
    int timeToStartFull = 5 * 4;

    public Game (Arena arena) {
        this.arena = arena;
        this.gameState = GameState.WAITING_TO_ACCEPT;
        this.gameInfo = defaultGameInfo();
    }

    public void tick () {}

    public boolean isCancelled () {
        return cancelled;
    }

    public void setCancelled (boolean cancelled) {
        this.cancelled = cancelled;
    }

    public Arena getArena () {
        return arena;
    }

    public void endMatch () {}

    public static GameInfo defaultGameInfo () {
        return new GameInfo(EntityWithRating.DEFAULT_RATING, EntityWithRating.DEFAULT_DEVIATION, EntityWithRating.DEFAULT_RATING / 6.0, EntityWithRating.DEFAULT_RATING / 300.0, 0.03);
    }
}

