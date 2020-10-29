package dev.tonysp.rankedpvp;

import dev.tonysp.rankedpvp.players.PlayerManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public enum Messages {
    PREFIX,
    WIN_MESSAGE,
    LOSE_MESSAGE,
    DRAW_MESSAGE,
    WIN_MESSAGE_CHANGE,
    LOSE_MESSAGE_CHANGE,
    DRAW_MESSAGE_CHANGE,
    ANNOUNCE_NOT_DRAW,
    ANNOUNCE_DRAW,
    RANK,
    NOT_PART_OF_GAME,
    ACCEPTED,
    NOT_IN_QUEUE,
    LEFT_QUEUE,
    ALREADY_WAITING,
    COOLDOWN,
    WAIT_FOR_OPPONENT,
    DID_NOT_ACCEPT,
    OPPONENT_DID_NOT_ACCEPT,
    BATTLE_STARTED,
    BATTLE_STARTING_IN,
    TIME_REMAINING,
    BATTLE_CANCELLED,
    CLICK_TO_TELEPORT,
    OPPONENT_NOT_IN_ARENA,
    RATING_DIFF_POSITIVE,
    RATING_DIFF_NEGATIVE,

    ONE_PLAYER,
    TWO_TO_FOUR_PLAYERS,
    FIVE_OR_MORE_PLAYERS,
    ONE_SECOND,
    TWO_TO_FOUR_SECONDS,
    FIVE_OR_MORE_SECONDS,
    ONE_MINUTE,
    TWO_TO_FOUR_MINUTES,
    FIVE_OR_MORE_MINUTES,
    ONE_REMAINING,
    TWO_TO_FOUR_REMAINING,
    FIVE_OR_MORE_REMAINING,
    ;

    private String message;
    private boolean isMessageSet = false;

    public static String MESSAGES_SECTION = "messages";

    public void setMessage (String message) {
        if (message != null
                && !message.isEmpty()) {
            this.message = message;
            this.isMessageSet = true;
        } else {
            this.isMessageSet = false;
        }
    }

    public String getMessage () {
        return this.message;
    }

    public static void loadFromConfig (FileConfiguration config) {
        for (Messages message : values()) {
            String key = MESSAGES_SECTION + "." + message.toString().toLowerCase().replaceAll("_", "-");
            message.setMessage(Utils.formatString(config.getString(key, "")));
        }
    }

    public void sendTo (String playerName) {
        if (!isMessageSet || getMessage().equalsIgnoreCase("")) return;

        String message = PREFIX.getMessage() + getMessage();
        PlayerManager.getInstance().sendMessageToPlayer(playerName, ChatColor.translateAlternateColorCodes('&', message), true);
    }

    public void sendTo (String playerName, String...variables) {
        if (!isMessageSet || getMessage().equalsIgnoreCase("")) return;

        String message = PREFIX.getMessage() + getMessage();
        for (String variable : variables) {
            message = message.replaceAll(variable.split(":")[0], variable.split(":")[1]);
        }
        PlayerManager.getInstance().sendMessageToPlayer(playerName, ChatColor.translateAlternateColorCodes('&', message), true);
    }
}