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

import de.gesundkrank.jskills.Rating;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Comparator;

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

