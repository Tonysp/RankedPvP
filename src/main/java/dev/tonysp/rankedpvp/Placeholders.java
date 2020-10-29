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
            return arenaPlayer.map(player -> String.valueOf(player.getRatingRound())).orElse("");
        } else if (param.contains("player-rating")) {
            if (offlinePlayer.getName() == null)
                return "";
            ArenaPlayer arenaPlayer = PlayerManager.getInstance().getOrCreatePlayer(offlinePlayer.getName());
            return String.valueOf(arenaPlayer.getRatingRound());
        }

        return null;
    }
}