package dev.tonysp.rankedpvp.commands;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.TwoPlayerTrueSkillCalculator;
import dev.tonysp.rankedpvp.game.EventType;
import dev.tonysp.rankedpvp.game.Game;
import dev.tonysp.rankedpvp.game.GameManager;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import dev.tonysp.rankedpvp.players.EntityWithRating;
import dev.tonysp.rankedpvp.players.PlayerManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.Optional;
import java.util.TreeSet;

public class PvPCommand implements CommandExecutor {

    public static final String TITLE = ChatColor.GOLD + "--// " + ChatColor.GRAY + "RankedPvP" + ChatColor.GOLD + " §l//--";
    public static final String FANCY_LINE = ChatColor.GRAY + "" + ChatColor.BOLD + "▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰▰";



    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("pvp")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(TITLE);
            sender.sendMessage(ChatColor.GOLD + "/pvp " + ChatColor.YELLOW + "player [name]" + ChatColor.GRAY + " - Shows player's PvP stats");
            sender.sendMessage(ChatColor.GOLD + "/pvp " + ChatColor.YELLOW + "join [type]" + ChatColor.GRAY + " - Joins queue");
            sender.sendMessage(ChatColor.GOLD + "/pvp " + ChatColor.YELLOW + "leave [type]" + ChatColor.GRAY + " - Leaves queue");
            sender.sendMessage(ChatColor.GOLD + "/pvp " + ChatColor.YELLOW + "ladder [type]" + ChatColor.GRAY + " - Shows top 10 players of given ladder");
            sender.sendMessage(ChatColor.GOLD + "/pvp " + ChatColor.YELLOW + "accept" + ChatColor.GRAY + " - Accepts match");
            return true;
        }

        if (args[0].equalsIgnoreCase("player")) {
            if (args.length <= 1) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /pvp player [name]");
                return true;
            }
            Optional<ArenaPlayer> player = PlayerManager.getInstance().getPlayerIfExists(args[1]);
            if (player.isPresent()) {
                sender.sendMessage(FANCY_LINE);
                String rank = PlayerManager.getInstance().getPlayerRating(player.get().getRatingVisible(), player.get().getMatches());
                sender.sendMessage(ChatColor.YELLOW + "player: " + rank + " " + ChatColor.GRAY + player.get().getName());
                sender.sendMessage(ChatColor.YELLOW + "number of matches: " + ChatColor.GRAY + player.get().getMatches());
                sender.sendMessage(ChatColor.YELLOW + "wins/loses/draws: " + ChatColor.GRAY + player.get().getWins() + "/" + player.get().getLosses() + "/" + player.get().getDraws());
            } else {
                String rank = PlayerManager.getInstance().getPlayerRating((int) EntityWithRating.DEFAULT_RATING, 0);
                sender.sendMessage(ChatColor.YELLOW + "player: " + rank + " " + ChatColor.GRAY + args[1]);
                sender.sendMessage(ChatColor.YELLOW + "number of matches: " + ChatColor.GRAY + "0");
                sender.sendMessage(ChatColor.YELLOW + "wins/loses/draws: " + ChatColor.GRAY + "0/0/0");
            }
            return true;
        } else if (args[0].equalsIgnoreCase("ladder")) {
            TreeSet<ArenaPlayer> players = PlayerManager.getInstance().getTopPlayers();
            if (players.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "The ladder is empty.");
                return true;
            }
            int pos = 1;
            for (ArenaPlayer player : players) {
                sender.sendMessage(ChatColor.YELLOW + "" + (pos++) + ". " + player.getNameWithRating());
                if (pos >= 11 || pos > players.size()) {
                    return true;
                }
            }
        } else if (args[0].equalsIgnoreCase("join")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage(ChatColor.RED + "This command can be only used in game.");
                return true;
            }
            if (args.length <= 1) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /pvp join [type]");
                return true;
            }

            Optional<EventType> eventType = EventType.fromString(args[1]);
            if (!eventType.isPresent()) {
                sender.sendMessage(ChatColor.RED + "Wrong type, specify one of these types: 1v1");
                return true;
            }

            GameManager.getInstance().tryJoiningQueue(sender.getName(), eventType.get());
        } else if (args[0].equalsIgnoreCase("leave")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage(ChatColor.RED + "This command can be only used in game.");
                return true;
            }
            if (args.length <= 1) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /pvp leave [type]");
                return true;
            }

            Optional<EventType> eventType = EventType.fromString(args[1]);
            if (!eventType.isPresent()) {
                sender.sendMessage(ChatColor.RED + "Wrong type, specify one of these types: 1v1");
                return true;
            }

            GameManager.getInstance().tryLeavingQueue(sender.getName(), eventType.get());
        } else if (args[0].equalsIgnoreCase("togglejoin")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage(ChatColor.RED + "This command can be only used in game.");
                return true;
            }
            if (args.length <= 1) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /pvp togglejoin [type]");
                return true;
            }

            Optional<EventType> eventType = EventType.fromString(args[1]);
            if (!eventType.isPresent()) {
                sender.sendMessage(ChatColor.RED + "Wrong type, specify one of these types: 1v1");
                return true;
            }

            GameManager.getInstance().toggleJoin(sender.getName(), eventType.get());
        } else if (args[0].equalsIgnoreCase("accept")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage(ChatColor.RED + "This command can be only used in game.");
                return true;
            }

            GameManager.getInstance().tryAcceptingGame(sender.getName());
        } else if (args[0].equalsIgnoreCase("matchquality")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage(ChatColor.RED + "This command can be only used in game.");
                return true;
            }

            double r0 = Double.parseDouble(args[1]);
            double r1 = Double.parseDouble(args[2]);
            double d0 = Double.parseDouble(args[3]);
            double d1 = Double.parseDouble(args[4]);
            Player<String> p0 = new Player<>("p0");
            Player<String> p1 = new Player<>("p1");
            Team t0 = new Team(p0, new Rating(r0, d0));
            Team t1 = new Team(p1, new Rating(r1, d1));
            TwoPlayerTrueSkillCalculator calculator = new TwoPlayerTrueSkillCalculator();
            GameInfo gameInfo = Game.defaultGameInfo();
            sender.sendMessage("Match quality: " + calculator.calculateMatchQuality(gameInfo, Team.concat(t0, t1)));
        }

        return true;
    }
}