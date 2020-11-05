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

package dev.tonysp.rankedpvp.data;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.TwoPlayerTrueSkillCalculator;
import dev.tonysp.plugindata.connections.mysql.MysqlConnection;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.game.Game;
import dev.tonysp.rankedpvp.game.MatchResult;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import dev.tonysp.rankedpvp.players.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class Database {

    private static Database instance;

    private static String TABLE_PREFIX;
    private MysqlConnection mysqlConnection;

    public static Database getInstance () {
        if (instance == null) {
            instance = new Database();
        }
        return instance;
    }

    private Database () {
        FileConfiguration config = RankedPvP.getInstance().getConfig();
        String url, username, password;
        url = config.getString("mysql.url", "");
        username = config.getString("mysql.username", "");
        password = config.getString("mysql.password", "");
        String connectionName = "RankedPvP-plugin-mysql";
        try {
            mysqlConnection = new MysqlConnection(connectionName, url, username, password);
        } catch (Exception exception) {
            RankedPvP.logWarning("Error while initializing MySQL connection!");
            return;
        }
        RankedPvP.log("Initialized MySQL connection.");
        mysqlConnection.test();
    }

    private Connection getConnection () throws SQLException {
        return mysqlConnection.getConnection();
    }

    public void initializeTables(){
        TABLE_PREFIX = RankedPvP.getInstance().getConfig().getString("mysql.table-prefix", "rankedpvp_");
        try (Connection connection = getConnection()) {
            PreparedStatement sql = connection.prepareStatement("CREATE TABLE IF NOT EXISTS `" + TABLE_PREFIX + "players` (`id` int(10) unsigned NOT NULL AUTO_INCREMENT,`name` varchar(16) NOT NULL,`rating` double NOT NULL,`deviation` double NOT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=latin1 AUTO_INCREMENT=1;");
            sql.executeUpdate();
            sql.close();

            sql = connection.prepareStatement("CREATE TABLE IF NOT EXISTS `" + TABLE_PREFIX + "matches` (`id` int(10) unsigned NOT NULL AUTO_INCREMENT,`team-one` int(10) unsigned NOT NULL,`team-two` int(10) unsigned NOT NULL,`winner-team` int(10),`arena` varchar(30) NOT NULL,`event-type` varchar(20) NOT NULL,`datetime` datetime,`team-one-pre-rating` double NOT NULL,`team-two-pre-rating` double NOT NULL,`team-one-post-rating` double NOT NULL,`team-two-post-rating` double NOT NULL,`team-one-pre-deviation` double NOT NULL,`team-two-pre-deviation` double NOT NULL,`team-one-post-deviation` double NOT NULL,`team-two-post-deviation` double NOT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=latin1 AUTO_INCREMENT=1;");
            sql.executeUpdate();
            sql.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public HashMap<String, ArenaPlayer> loadPlayers() {
        HashMap<String, ArenaPlayer> players = new HashMap<>();
        try (Connection connection = getConnection();
             PreparedStatement sql = connection.prepareStatement("SELECT * FROM " + TABLE_PREFIX + "players;");
             ResultSet resultSet = sql.executeQuery();
        ) {
            while (resultSet.next()) {
                ArenaPlayer player = new ArenaPlayer(resultSet.getString("name"), resultSet.getDouble("rating"), resultSet.getDouble("deviation"));
                player.setId(resultSet.getInt("id"));
                players.put(player.getName().toLowerCase(), player);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return players;
    }

    public ArrayList<MatchResult> loadMatchHistory() {
        ArrayList<MatchResult> matches = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement sql = connection.prepareStatement("SELECT * FROM " + TABLE_PREFIX + "matches;");
             ResultSet resultSet = sql.executeQuery();
        ) {
            while (resultSet.next()) {
                MatchResult matchResult = new MatchResult();
                matchResult.teamOne = resultSet.getInt("team-one");
                matchResult.teamTwo = resultSet.getInt("team-two");
                matchResult.winnerTeam = resultSet.getInt("winner-team");
                matchResult.arena = resultSet.getString("arena");
                matchResult.eventType = resultSet.getString("event-type");
                matchResult.timestamp = resultSet.getTimestamp("datetime");
                matchResult.teamOnePreR = resultSet.getDouble("team-one-pre-rating");
                matchResult.teamTwoPreR = resultSet.getDouble("team-two-pre-rating");;
                matchResult.teamOnePostR = resultSet.getDouble("team-one-post-rating");
                matchResult.teamTwoPostR = resultSet.getDouble("team-two-post-rating");
                matchResult.teamOnePreD = resultSet.getDouble("team-one-pre-deviation");
                matchResult.teamTwoPreD = resultSet.getDouble("team-two-pre-deviation");
                matchResult.teamOnePostD = resultSet.getDouble("team-one-post-deviation");
                matchResult.teamTwoPostD = resultSet.getDouble("team-two-post-deviation");
                matches.add(matchResult);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return matches;
    }

    public void insertPlayer (ArenaPlayer player){
        Bukkit.getScheduler().runTask(RankedPvP.getInstance(), () -> {
            try (Connection connection = getConnection();
                 PreparedStatement sql = connection.prepareStatement("INSERT INTO " + TABLE_PREFIX + "players (name, rating, deviation) VALUES (?,?,?);", Statement.RETURN_GENERATED_KEYS);
            ) {
                sql.setString(1, player.getName());
                sql.setDouble(2, player.getRating());
                sql.setDouble(3, player.getDeviation());
                sql.executeUpdate();

                ResultSet res = sql.getGeneratedKeys();
                if (res.next()) {
                    player.setId(res.getInt(1));
                }
                res.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        });
    }

    public void showQualities (){
        try (Connection connection = getConnection();
             PreparedStatement sql = connection.prepareStatement("SELECT * FROM `" + TABLE_PREFIX + "matches`;");
        ) {
            ResultSet resultSet = sql.executeQuery();
            GameInfo gameInfo = Game.defaultGameInfo();
            int totalCount = 0;
            double totalQuality = 0;
            HashMap<Integer, MatchInfo> players = new HashMap<>();
            while (resultSet.next()) {
                int i0 = resultSet.getInt("team-one");
                int i1 = resultSet.getInt("team-two");
                double r0 = resultSet.getDouble("team-one-pre-rating");
                double r1 = resultSet.getDouble("team-two-pre-rating");
                double d0 = resultSet.getDouble("team-one-pre-deviation");
                double d1 = resultSet.getDouble("team-two-pre-deviation");
                Player<String> p0 = new Player<>("p0");
                Player<String> p1 = new Player<>("p1");
                Team t0 = new Team(p0, new Rating(r0, d0));
                Team t1 = new Team(p1, new Rating(r1, d1));
                TwoPlayerTrueSkillCalculator calculator = new TwoPlayerTrueSkillCalculator();
                double quality = calculator.calculateMatchQuality(gameInfo, Team.concat(t0, t1));
                //RankedPvP.log(resultSet.getInt("id") + " " + r0 + ":" + d0 + " vs " + r1 + ":" + d1 + " - " + quality);
                RankedPvP.log(quality + "");
                if (!players.containsKey(i0)) {
                    MatchInfo matchInfo = null;
                    for (ArenaPlayer player : PlayerManager.getInstance().getPlayers()) {
                        if (player.getId() == i0) {
                            matchInfo = new MatchInfo(player.getName(), player.getRatingRound(), player.getDeviation());
                            break;
                        }
                    }
                    players.put(i0, matchInfo);
                }
                if (!players.containsKey(i1)) {
                    MatchInfo matchInfo = null;
                    for (ArenaPlayer player : PlayerManager.getInstance().getPlayers()) {
                        if (player.getId() == i1) {
                            matchInfo = new MatchInfo(player.getName(), player.getRatingRound(), player.getDeviation());
                            break;
                        }
                    }
                    players.put(i1, matchInfo);
                }

                players.get(i0).count++;
                players.get(i0).quality += quality;
                players.get(i1).count++;
                players.get(i1).quality += quality;

                totalCount++;
                totalQuality += quality;
            }
            RankedPvP.log("count: " + totalCount + ", mean quality: " + (totalQuality / totalCount));
            MatchInfo mm = new MatchInfo("", 0, 0);
            mm.count++;
            players.forEach((k, v) -> {
                //RankedPvP.log(v.name + " " + v.rating + " <> " + v.deviation + " - count: " + v.count + ", mean quality: " + v.getAverageQuality());
                RankedPvP.log(v.getAverageQuality() + "");
                if (v.getAverageQuality() <= 0.3) {
                    RankedPvP.log("BAD QUALITY " + mm.count++);
                }
            });
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void insertMatchResult (MatchResult result){
        Bukkit.getScheduler().runTask(RankedPvP.getInstance(), () -> {
            try (Connection connection = getConnection();
                 PreparedStatement sql = connection.prepareStatement("INSERT INTO `" + TABLE_PREFIX + "matches`(`team-one`, `team-two`, `winner-team`, `arena`, `event-type`, `datetime`, `team-one-pre-rating`, `team-two-pre-rating`, `team-one-post-rating`, `team-two-post-rating`, `team-one-pre-deviation`, `team-two-pre-deviation`, `team-one-post-deviation`, `team-two-post-deviation`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?);");
            ) {
                sql.setInt(1, result.teamOne);
                sql.setInt(2, result.teamTwo);
                sql.setInt(3, result.winnerTeam);
                sql.setString(4, result.arena);
                sql.setString(5, result.eventType);
                sql.setTimestamp(6, result.timestamp);
                sql.setDouble(7, result.teamOnePreR);
                sql.setDouble(8, result.teamTwoPreR);
                sql.setDouble(9, result.teamOnePostR);
                sql.setDouble(10, result.teamTwoPostR);
                sql.setDouble(11, result.teamOnePreD);
                sql.setDouble(12, result.teamTwoPreD);
                sql.setDouble(13, result.teamOnePostD);
                sql.setDouble(14, result.teamTwoPostD);
                sql.executeUpdate();
            }catch (Exception e){
                e.printStackTrace();
            }
        });
    }

    public void updatePlayer (ArenaPlayer player){
        Bukkit.getScheduler().runTask(RankedPvP.getInstance(), () -> {
            try (Connection connection = getConnection();
                 PreparedStatement sql = connection.prepareStatement("UPDATE " + TABLE_PREFIX + "players SET name=?,rating=?,deviation=? WHERE id=?;");
            ) {
                sql.setString(1, player.getName());
                sql.setDouble(2, player.getRating());
                sql.setDouble(3, player.getDeviation());
                sql.setInt(4, player.getId());
                sql.executeUpdate();
            } catch (Exception e){
                e.printStackTrace();
            }
        });
    }
}

