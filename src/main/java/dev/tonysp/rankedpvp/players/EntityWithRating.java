package dev.tonysp.rankedpvp.players;

import de.gesundkrank.jskills.Rating;
import dev.tonysp.rankedpvp.game.MatchResult;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

public class EntityWithRating implements Comparable<EntityWithRating>, Serializable {

    public static final double DEFAULT_RATING = 1200.0;
    public static final double DEFAULT_DEVIATION = 1200.0 / 3;

    private int id = -1;
    protected int wins = 0;
    protected int losses = 0;
    protected int draws = 0;
    protected double rating, deviation;

    public EntityWithRating (double rating, double deviation) {
        this.rating = rating;
        this.deviation = deviation;
    }

    public EntityWithRating () {
        this.rating = DEFAULT_RATING;
        this.deviation = DEFAULT_DEVIATION;
    }

    @Override
    public int compareTo(@NotNull EntityWithRating other) {
        return Comparator.comparing(EntityWithRating::getRatingVisible)
                .thenComparing(EntityWithRating::getRating)
                .thenComparing(EntityWithRating::getWins)
                .thenComparingInt(EntityWithRating::getId)
                .compare(this, other);
    }

    public double getRating () {
        return this.rating;
    }

    public int getRatingVisible () {
        return getRatingRound();
    }

    public int getRatingRound () {
        return (int) Math.round(this.rating);
    }

    public void setRating (Rating rating) {
        this.rating = rating.getMean();
        this.deviation = rating.getStandardDeviation();
    }

    public double getDeviation () {
        return this.deviation;
    }

    public void setId (int id) {
        this.id = id;
    }

    public int getId () {
        return id;
    }

    public int getMatches () {
        return this.wins + this.losses + this.draws;
    }

    public int getWins () {
        return wins;
    }

    public int getLosses () {
        return losses;
    }

    public int getDraws () {
        return draws;
    }
}

