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

package dev.tonysp.rankedpvp.players;

import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.Utils;
import org.bukkit.configuration.ConfigurationSection;

public class Rank {

    private final String name;
    private final int lowSkill, highSkill;

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
        if (config.getCurrentPath() != null
                && !config.getCurrentPath().contains("unranked")) {
            String ratingString = config.getString("rating");
            if (ratingString == null)
                return null;
            lowSkill = Integer.parseInt(ratingString.split("-")[0]);
            highSkill = Integer.parseInt(ratingString.split("-")[1]);
        }

        return new Rank(name, lowSkill, highSkill);
    }

    public static Rank fromRating (int rating, int gamesPlayed) {
        if (UNRANKED != null && GAMES_TO_LOSE_UNRANKED > gamesPlayed) {
            return UNRANKED;
        } else {
            return RankedPvP.getInstance().players().getRanks().floorEntry(rating).getValue();
        }
    }
}
