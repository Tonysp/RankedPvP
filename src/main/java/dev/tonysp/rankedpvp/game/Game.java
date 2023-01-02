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

import de.gesundkrank.jskills.GameInfo;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.arenas.Arena;
import dev.tonysp.rankedpvp.game.result.GameResult;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import dev.tonysp.rankedpvp.players.EntityWithRating;

import java.util.List;

public abstract class Game {

    protected final RankedPvP plugin;
    protected final Arena arena;
    protected final EventType eventType;

    private final List<ArenaPlayer> players;
    protected GameState gameState;
    protected GameInfo gameInfo;

    protected GameResult result;

    // Game ticks every 250 ms (every 5 Minecraft ticks)
    int timeRemaining = 180 * 4;
    int timeToAccept = 30 * 4;
    int timeToTeleport = 30 * 4;

    int timeToStart = 5 * 4;
    int timeToStartFull = 5 * 4;

    public Game (Arena arena, List<ArenaPlayer> players, EventType eventType) {
        this.plugin = RankedPvP.getInstance();
        this.arena = arena;
        this.players = players;
        this.eventType = eventType;
        this.gameState = GameState.WAITING_TO_ACCEPT;
        this.gameInfo = defaultGameInfo();
    }

    public abstract void tick ();

    public abstract void endMatch ();

    public abstract void accepted (ArenaPlayer player);

    public abstract void teleportAllPlayersToLobby ();

    public abstract void processDeath (ArenaPlayer player);

    public abstract void teleportToLobby (ArenaPlayer player);

    public boolean shouldTick () {
        return gameState != GameState.FAILED && gameState != GameState.ENDED;
    }

    public Arena getArena () {
        return arena;
    }

    public static GameInfo defaultGameInfo () {
        return new GameInfo(
                EntityWithRating.DEFAULT_RATING,
                EntityWithRating.DEFAULT_DEVIATION,
                EntityWithRating.DEFAULT_RATING / 6.0,
                EntityWithRating.DEFAULT_RATING / 300.0,
                0.03
        );
    }

    public List<ArenaPlayer> getPlayers () {
        return players;
    }

    public void updatePlayers () {
        getPlayers().forEach(player -> plugin.database().updatePlayer(player));
    }
}

