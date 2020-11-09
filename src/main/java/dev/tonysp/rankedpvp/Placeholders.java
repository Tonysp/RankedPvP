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

package dev.tonysp.rankedpvp;

import dev.tonysp.rankedpvp.game.EventType;
import dev.tonysp.rankedpvp.game.GameManager;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import dev.tonysp.rankedpvp.players.PlayerManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Optional;

public class Placeholders extends PlaceholderExpansion {

    private RankedPvP plugin;

    public Placeholders (RankedPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean persist(){
        return true;
    }

    @Override
    public String getIdentifier() {
        return "rankedpvp";
    }

    @Override
    public String getAuthor(){
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion(){
        return plugin.getDescription().getVersion();
    }

    @Override
    public String getRequiredPlugin() {
        return "RankedPvP";
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest (OfflinePlayer offlinePlayer, String param) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) { return ""; }

        param = param.toLowerCase();

        if (param.equalsIgnoreCase("playing-1v1-amount")) {
            return String.valueOf(GameManager.getInstance().getGames().size() * 2);
        } else if (param.equalsIgnoreCase("playing-1v1-amount-noun")) {
            return Utils.playersString(GameManager.getInstance().getGames().size() * 2);
        } else if (param.equalsIgnoreCase("queue-1v1-amount")) {
            return String.valueOf(GameManager.getInstance().getPlayerQueue().get(EventType.ONE_VS_ONE).size());
        } else if (param.equalsIgnoreCase("queue-1v1-amount-noun")) {
            return Utils.playersString(GameManager.getInstance().getPlayerQueue().get(EventType.ONE_VS_ONE).size());
        } else if (param.contains("player-name-rank")) {
            int rank = Integer.parseInt(param.split("player-name-rank")[1]);
            Optional<ArenaPlayer> arenaPlayer = PlayerManager.getInstance().getPlayerByRank(rank);
            return arenaPlayer.map(player -> String.valueOf(player.getName())).orElse("");
        } else if (param.contains("player-rating-rank")) {
            int rank = Integer.parseInt(param.split("player-rating-rank")[1]);
            Optional<ArenaPlayer> arenaPlayer = PlayerManager.getInstance().getPlayerByRank(rank);
            return arenaPlayer.map(player -> PlayerManager.getInstance().getPlayerRating(player.getRatingVisible(), player.getMatches())).orElse("");
        } else if (param.contains("player-rating")) {
            if (offlinePlayer.getName() == null)
                return "";
            ArenaPlayer arenaPlayer = PlayerManager.getInstance().getOrCreatePlayer(offlinePlayer.getUniqueId());
            return PlayerManager.getInstance().getPlayerRating(arenaPlayer.getRatingVisible(), arenaPlayer.getMatches());
        }

        return null;
    }
}