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

package dev.tonysp.rankedpvp.data;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.TwoPlayerTrueSkillCalculator;
import dev.tonysp.plugindata.connections.mysql.MysqlConnection;
import dev.tonysp.rankedpvp.Manager;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.game.Game;
import dev.tonysp.rankedpvp.game.result.TwoTeamGameResult;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.*;

public class DatabaseManager extends Manager {
    private MysqlConnection mysqlConnection;

    public DatabaseManager (RankedPvP plugin) {
        super(plugin);
    }

    @Override
    public boolean load () {
        FileConfiguration config = RankedPvP.getInstance().getConfig();
        String url, username, password;
        url = config.getString("mysql.url", "");
        username = config.getString("mysql.username", "");
        password = config.getString("mysql.password", "");
        String connectionName = "RankedPvP-plugin-mysql";
        try {
            mysqlConnection = new MysqlConnection(connectionName, url, username, password);
        } catch (Exception exception) {
            RankedPvP.logWarning("Error while initializing MySQL connection! MySQL is required for the function of this plugin.");
            exception.printStackTrace();
            return false;
        }
        RankedPvP.log("Initialized MySQL connection.");
        mysqlConnection.test();

        return true;
    }

    @Override
    public void unload () {
        if (mysqlConnection != null)
            mysqlConnection.closeDataSource();
    }

    private Connection getConnection () throws SQLException {
        return mysqlConnection.getConnection();
    }

    public void initializeTables(){
        try (Connection connection = getConnection()) {
            PreparedStatement sql = connection.prepareStatement("CREATE TABLE IF NOT EXISTS `rankedpvp_players` (`id` int(10) unsigned NOT NULL AUTO_INCREMENT,`name` varchar(16) NOT NULL,`uuid` varchar(36) NOT NULL,`rating` double NOT NULL,`deviation` double NOT NULL,`visible_rating` double NOT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=latin1 AUTO_INCREMENT=1;");
            sql.executeUpdate();
            sql.close();

            sql = connection.prepareStatement("CREATE TABLE IF NOT EXISTS `rankedpvp_matches` (`id` int(10) unsigned NOT NULL AUTO_INCREMENT,`team-one` int(10) unsigned NOT NULL,`team-two` int(10) unsigned NOT NULL,`winner-team` int(10),`arena` varchar(30) NOT NULL,`event-type` varchar(20) NOT NULL,`datetime` datetime,`team-one-pre-rating` double NOT NULL,`team-two-pre-rating` double NOT NULL,`team-one-post-rating` double NOT NULL,`team-two-post-rating` double NOT NULL,`team-one-pre-deviation` double NOT NULL,`team-two-pre-deviation` double NOT NULL,`team-one-post-deviation` double NOT NULL,`team-two-post-deviation` double NOT NULL,`quality` double NOT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=latin1 AUTO_INCREMENT=1;");
            sql.executeUpdate();
            sql.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean loadPlayers(final Map<UUID, ArenaPlayer> players) {
        players.clear();
        try (Connection connection = getConnection();
             PreparedStatement sql = connection.prepareStatement("SELECT * FROM `rankedpvp_players`;");
             ResultSet resultSet = sql.executeQuery();
        ) {
            while (resultSet.next()) {
                UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                ArenaPlayer player = new ArenaPlayer(uuid, resultSet.getDouble("rating"), resultSet.getDouble("deviation"));
                player.setId(resultSet.getInt("id"));
                players.put(uuid, player);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public ArrayList<TwoTeamGameResult> loadMatchHistory() {
        ArrayList<TwoTeamGameResult> matches = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement sql = connection.prepareStatement("SELECT * FROM `rankedpvp_matches`;");
             ResultSet resultSet = sql.executeQuery();
        ) {
            while (resultSet.next()) {
                TwoTeamGameResult twoTeamMatchResult = new TwoTeamGameResult();
                twoTeamMatchResult.teamOne = resultSet.getInt("team-one");
                twoTeamMatchResult.teamTwo = resultSet.getInt("team-two");
                twoTeamMatchResult.winnerTeam = resultSet.getInt("winner-team");
                twoTeamMatchResult.arena = resultSet.getString("arena");
                twoTeamMatchResult.eventType = resultSet.getString("event-type");
                twoTeamMatchResult.timestamp = resultSet.getTimestamp("datetime");
                twoTeamMatchResult.teamOnePreR = resultSet.getDouble("team-one-pre-rating");
                twoTeamMatchResult.teamTwoPreR = resultSet.getDouble("team-two-pre-rating");;
                twoTeamMatchResult.teamOnePostR = resultSet.getDouble("team-one-post-rating");
                twoTeamMatchResult.teamTwoPostR = resultSet.getDouble("team-two-post-rating");
                twoTeamMatchResult.teamOnePreD = resultSet.getDouble("team-one-pre-deviation");
                twoTeamMatchResult.teamTwoPreD = resultSet.getDouble("team-two-pre-deviation");
                twoTeamMatchResult.teamOnePostD = resultSet.getDouble("team-one-post-deviation");
                twoTeamMatchResult.teamTwoPostD = resultSet.getDouble("team-two-post-deviation");
                twoTeamMatchResult.quality = resultSet.getDouble("quality");
                matches.add(twoTeamMatchResult);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return matches;
    }

    public void insertPlayer (ArenaPlayer player){
        Bukkit.getScheduler().runTask(RankedPvP.getInstance(), () -> {
            try (Connection connection = getConnection();
                 PreparedStatement sql = connection.prepareStatement("INSERT INTO `rankedpvp_players` (name, uuid, rating, deviation, visible_rating) VALUES (?,?,?,?,?);", Statement.RETURN_GENERATED_KEYS);
            ) {
                sql.setString(1, player.getName());
                sql.setString(2, player.getUuid().toString());
                sql.setDouble(3, player.getRating());
                sql.setDouble(4, player.getDeviation());
                sql.setDouble(5, player.getRatingVisible());
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
             PreparedStatement sql = connection.prepareStatement("SELECT * FROM `rankedpvp_matches`;");
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
                    for (ArenaPlayer player : plugin.players().getPlayers()) {
                        if (player.getId() == i0) {
                            matchInfo = new MatchInfo(player.getName(), player.getRatingRound(), player.getDeviation());
                            break;
                        }
                    }
                    players.put(i0, matchInfo);
                }
                if (!players.containsKey(i1)) {
                    MatchInfo matchInfo = null;
                    for (ArenaPlayer player : plugin.players().getPlayers()) {
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

    public void insertMatchResult (TwoTeamGameResult result){
        Bukkit.getScheduler().runTask(RankedPvP.getInstance(), () -> {
            try (Connection connection = getConnection();
                 PreparedStatement sql = connection.prepareStatement("INSERT INTO `rankedpvp_matches`(`team-one`, `team-two`, `winner-team`, `arena`, `event-type`, `datetime`, `team-one-pre-rating`, `team-two-pre-rating`, `team-one-post-rating`, `team-two-post-rating`, `team-one-pre-deviation`, `team-two-pre-deviation`, `team-one-post-deviation`, `team-two-post-deviation`, `quality`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);");
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
                sql.setDouble(15, result.quality);
                sql.executeUpdate();
            }catch (Exception e){
                e.printStackTrace();
            }
        });
    }

    public void updatePlayer (ArenaPlayer player){
        Bukkit.getScheduler().runTask(RankedPvP.getInstance(), () -> {
            try (Connection connection = getConnection();
                 PreparedStatement sql = connection.prepareStatement("UPDATE `rankedpvp_players` SET name=?,uuid=?,rating=?,deviation=?,visible_rating=? WHERE id=?;");
            ) {
                sql.setString(1, player.getName());
                sql.setString(2, player.getUuid().toString());
                sql.setDouble(3, player.getRating());
                sql.setDouble(4, player.getDeviation());
                sql.setDouble(5, player.getRatingVisible());
                sql.setInt(6, player.getId());
                sql.executeUpdate();
            } catch (Exception e){
                e.printStackTrace();
            }
        });
    }
}

