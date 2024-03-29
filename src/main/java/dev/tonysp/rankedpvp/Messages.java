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

package dev.tonysp.rankedpvp;

import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.UUID;

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

    private TextComponent message;
    private boolean isMessageSet = false;

    public static final String MESSAGES_SECTION = "messages";
    private static final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public void setMessage (TextComponent message) {
        this.message = message;
        this.isMessageSet = true;
    }

    public String getMessageContent () {
        return this.message.content();
    }

    public TextComponent getMessage () {
        return message;
    }

    public static LegacyComponentSerializer getSerializer () {
        return serializer;
    }

    public static void loadFromConfig (FileConfiguration config) {
        for (Messages message : values()) {
            String key = MESSAGES_SECTION + "." + message.toString().toLowerCase().replaceAll("_", "-");
            String stringMessage = config.getString(key, "");
            if (stringMessage.isEmpty())
                continue;

            message.setMessage(getSerializer().deserialize(stringMessage));
        }
    }

    public void sendTo (UUID playerUuid) {
        if (!isMessageSet)
            return;

        TextComponent finalMessage = PREFIX.getMessage().append(getMessage());
        RankedPvP.getInstance().players().sendMessageToPlayer(playerUuid, finalMessage, true);
    }

    public void sendTo (UUID playerUuid, String... variables) {
        if (!isMessageSet)
            return;

        TextComponent finalMessage = PREFIX.getMessage().append(getMessage());
        for (String variable : variables) {
            TextReplacementConfig replacement = TextReplacementConfig.builder()
                    .match(variable.split(":")[0])
                    .replacement(variable.split(":")[1])
                    .build();
            finalMessage = (TextComponent) finalMessage.replaceText(replacement);
        }
        RankedPvP.getInstance().players().sendMessageToPlayer(playerUuid, finalMessage, true);
    }

    public void sendTo (UUID playerUuid, Iterable<? extends TextReplacementConfig> replacements) {
        if (!isMessageSet)
            return;

        TextComponent finalMessage = PREFIX.getMessage().append(getMessage());
        for (TextReplacementConfig variable : replacements) {
            finalMessage = (TextComponent) finalMessage.replaceText(variable);
        }
        RankedPvP.getInstance().players().sendMessageToPlayer(playerUuid, finalMessage, true);
    }

    public void sendTo (UUID playerUuid, TextReplacementConfig... replacements) {
        sendTo(playerUuid, Arrays.asList(replacements));
    }

    public TextComponent getMessage (Iterable<? extends TextReplacementConfig> replacements) {
        TextComponent finalMessage = message;
        for (TextReplacementConfig variable : replacements) {
            finalMessage = (TextComponent) finalMessage.replaceText(variable);
        }
        return finalMessage;
    }

    public TextComponent getMessage (TextReplacementConfig... replacements) {
        return getMessage(Arrays.asList(replacements));
    }
}