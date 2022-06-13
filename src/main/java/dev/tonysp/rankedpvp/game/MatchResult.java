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
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.TwoPlayerTrueSkillCalculator;

import java.io.Serializable;
import java.sql.Timestamp;

public class MatchResult implements Serializable {

    public int teamOne;
    public int teamTwo;
    public int winnerTeam;
    public String arena;
    public String eventType;
    public Timestamp timestamp;
    public double teamOnePreR;
    public double teamTwoPreR;
    public double teamOnePostR;
    public double teamTwoPostR;
    public double teamOnePreD;
    public double teamTwoPreD;
    public double teamOnePostD;
    public double teamTwoPostD;
    public double quality;

    public double calculateMatchQuality () {
        GameInfo gameInfo = Game.defaultGameInfo();
        Player<String> p0 = new Player<>("p0");
        Player<String> p1 = new Player<>("p1");
        Team t0 = new Team(p0, new Rating(teamOnePreR, teamOnePreD));
        Team t1 = new Team(p1, new Rating(teamTwoPreR, teamTwoPreD));
        TwoPlayerTrueSkillCalculator calculator = new TwoPlayerTrueSkillCalculator();
        this.quality = calculator.calculateMatchQuality(gameInfo, Team.concat(t0, t1));
        return quality;
    }

    public boolean isDraw () {
        return winnerTeam == -1;
    }
}

