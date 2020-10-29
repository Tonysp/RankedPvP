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

    public double calculateMatchQuality () {
        GameInfo gameInfo = Game.defaultGameInfo();
        Player<String> p0 = new Player<>("p0");
        Player<String> p1 = new Player<>("p1");
        Team t0 = new Team(p0, new Rating(teamOnePreR, teamOnePreD));
        Team t1 = new Team(p1, new Rating(teamTwoPreR, teamTwoPreD));
        TwoPlayerTrueSkillCalculator calculator = new TwoPlayerTrueSkillCalculator();
        return calculator.calculateMatchQuality(gameInfo, Team.concat(t0, t1));
    }
}

