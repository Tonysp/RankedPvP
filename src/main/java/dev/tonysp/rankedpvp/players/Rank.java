package dev.tonysp.rankedpvp.players;

import dev.tonysp.rankedpvp.Utils;
import org.bukkit.configuration.ConfigurationSection;

public class Rank {

    private String name;
    private int lowSkill, highSkill;

    public static int GAMES_TO_LOSE_UNRANKED = 0;
    public static Rank UNRANKED = null;

    public Rank (String name, int lowSkill, int highSkill) {
        this.name = name;
        this.lowSkill = lowSkill;
        this.highSkill = highSkill;
    }

    public String getName () {
        return name;
    }

    public int getKey () {
        return lowSkill;
    }

    public static Rank loadFromConfig (ConfigurationSection config) {
        if (config == null)
            return null;
        String name = config.getString("name");
        name = Utils.formatString(name);
        int lowSkill = 0, highSkill = 0;
        if (!config.getCurrentPath().contains("unranked")) {
            lowSkill = Integer.parseInt(config.getString("rating").split("-")[0]);
            highSkill = Integer.parseInt(config.getString("rating").split("-")[1]);
        }

        return new Rank(name, lowSkill, highSkill);
    }

    public static Rank fromRating (int rating, int gamesPlayed) {
        if (UNRANKED != null && GAMES_TO_LOSE_UNRANKED > gamesPlayed) {
            return UNRANKED;
        } else {
            return PlayerManager.getInstance().getRanks().floorEntry(rating).getValue();
        }
    }
}
