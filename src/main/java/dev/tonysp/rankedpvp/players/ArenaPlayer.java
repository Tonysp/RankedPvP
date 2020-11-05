package dev.tonysp.rankedpvp.players;

import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.game.MatchResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ArenaPlayer extends EntityWithRating implements Comparable<EntityWithRating>, Serializable {

    private String name;
    private transient ItemStack[] inventoryBackup, arenaInventoryBackup;
    private int timeInQueue = 0;
    private final List<MatchResult> matchHistory = new ArrayList<>();

    public ArenaPlayer (String name) {
        super();
        this.name = name;
    }

    public ArenaPlayer (String name, double rating, double deviation) {
        super(rating, deviation);
        this.name = name;
    }

    public String getName () {
        return name;
    }

    public String getNameWithRating () {
        return name + " " + getRatingColored();
    }

    public String getRatingColored () {
        return PlayerManager.getInstance().getPlayerRating(getRatingVisible(), getMatches());
    }

    @Override
    public int getRatingVisible () {
        if (matchHistory.size() == 0) {
            return getRatingRound();
        }
        double averageQuality = matchHistory.stream().mapToDouble(MatchResult::calculateMatchQuality).sum() / matchHistory.size();
        double coefficient = Math.pow(averageQuality, 2.0) * (6.0 + 2.0/3.0);
        if (coefficient > 1) {
            coefficient = 1;
        }
        //RankedPvP.log(name + " amq: " + averageQuality + ", coeff: " + coefficient + ", total: " + (int) (getRating() * coefficient) + ", size: " + matchHistory.size());

        return (int) (getRating() * coefficient);
    }

    public void backupInventory () {
        Player player = Bukkit.getPlayer(getName());
        if (player == null || !player.isOnline())
            return;

        inventoryBackup = player.getInventory().getContents();
        player.getInventory().clear();
    }

    public void restoreInventory () {
        Player player = Bukkit.getPlayer(getName());
        if (player == null || !player.isOnline())
            return;

        player.setHealth(20);
        if (inventoryBackup != null) {
            player.getInventory().clear();
            player.getInventory().setContents(inventoryBackup);
            inventoryBackup = null;
        }
    }

    public void backupArenaEquip () {
        Player player = Bukkit.getPlayer(getName());
        if (player == null || !player.isOnline())
            return;

        arenaInventoryBackup = player.getInventory().getContents();
        player.getInventory().clear();
    }

    public void restoreArenaEquip () {
        Player player = Bukkit.getPlayer(getName());
        if (player == null || !player.isOnline())
            return;

        player.getInventory().clear();
        player.getInventory().setContents(arenaInventoryBackup);
    }

    public void incrementTimeInQueue () {
        timeInQueue++;
    }

    public int getTimeInQueue () {
        return this.timeInQueue;
    }

    public void resetTimeInQueue () {
        this.timeInQueue = 0;
    }

    public void addMatchToHistory (MatchResult matchResult) {
        matchHistory.add(matchResult);
        if (matchResult.winnerTeam == getId()) {
            wins ++;
        } else if (matchResult.isDraw()) {
            draws ++;
        } else {
            losses ++;
        }
    }

    public String getNameWithRatingAndChange (double change) {
        if (PlayerManager.getInstance().ranksEnabled()) {
            return getNameWithRating() + ChatColor.RESET;
        }

        Messages message = Messages.RATING_DIFF_POSITIVE;
        if (change < 0) {
            message = Messages.RATING_DIFF_NEGATIVE;
            change *= -1.0;
        }
        String changeString;
        if (change > 2) {
            changeString = String.valueOf((int) Math.round(change));
        } else if (change <= 0.1) {
            changeString = "0.1";
        } else {
            DecimalFormat df = new DecimalFormat("0.#");
            changeString = df.format(change);
        }

        return getNameWithRating() + message.getMessage().replaceAll("%AMOUNT%", changeString) + ChatColor.RESET;
    }
}

