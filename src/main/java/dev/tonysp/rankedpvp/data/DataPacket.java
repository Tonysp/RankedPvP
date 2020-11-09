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

import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.players.ArenaPlayer;

import java.util.*;

public class DataPacket extends dev.tonysp.plugindata.data.packets.DataPacket {

    private final Action action;
    private final Warp warp;
    private final ArenaPlayer player;
    private final String string, string2;
    private final UUID uuid;
    private final int integer;
    private final boolean boolean1, boolean2;
    private final Map<String, String> stringMap;
    private final List<String> stringList;
    private final Map<String, List<Warp>> warps;

    public static DataPacket.Builder newBuilder () {
        return new DataPacket.Builder();
    }

    public DataPacket (
            String sender,
            Set<String> receivers,
            Action action,
            Warp warp,
            ArenaPlayer player,
            String string,
            String string2,
            UUID uuid,
            int integer,
            boolean boolean1,
            boolean boolean2,
            Map<String, String> stringMap,
            List<String> stringList,
            Map<String, List<Warp>> warps
    ) {
        super(DataPacketProcessor.getInstance().getDataPacketManager(), RankedPvP.PLUGIN_ID, receivers);
        this.setSender(sender);
        this.action = action;
        this.warp = warp;
        this.player = player;
        this.string = string;
        this.string2 = string2;
        this.uuid = uuid;
        this.integer = integer;
        this.boolean1 = boolean1;
        this.boolean2 = boolean2;
        this.stringMap = stringMap;
        this.stringList = stringList;
        this.warps = warps;
    }

    public Action getAction () {
        return action;
    }

    public Warp getWarp () {
        return warp;
    }

    public ArenaPlayer getPlayer () {
        return player;
    }

    public String getString () {
        return string;
    }

    public String getString2 () {
        return string2;
    }

    public UUID getUuid () {
        return uuid;
    }

    public int getInteger () {
        return integer;
    }

    public boolean getBoolean () {
        return boolean1;
    }

    public boolean getBoolean2 () {
        return boolean2;
    }

    public Map<String, String> getStringMap () {
        return stringMap;
    }

    public List<String> getStringList () {
        return stringList;
    }

    public Map<String, List<Warp>> getWarps () {
        return warps;
    }

    public static class Builder {
        private HashSet<String> receivers;

        private Action action;
        private Warp warp;
        private ArenaPlayer player;
        private String string, string2;
        private UUID uuid;
        private int integer;
        private boolean boolean1, boolean2;
        private Map<String, String> stringMap;
        private List<String> stringList;
        private Map<String, List<Warp>> warps;

        public DataPacket buildPacket () {
            return new DataPacket(
                    DataPacketProcessor.getInstance().getServerId(),
                    this.receivers,
                    this.action,
                    this.warp,
                    this.player,
                    this.string,
                    this.string2,
                    this.uuid,
                    this.integer,
                    this.boolean1,
                    this.boolean2,
                    this.stringMap,
                    this.stringList,
                    this.warps
            );
        }

        public DataPacket.Builder addReceiver (String serverName) {
            if (this.receivers == null) {
                this.receivers = new HashSet<>();
            }

            this.receivers.add(serverName);
            return this;
        }

        public DataPacket.Builder action (Action action) {
            this.action = action;
            return this;
        }

        public DataPacket.Builder warp (Warp warp) {
            this.warp = warp;
            return this;
        }

        public DataPacket.Builder player (ArenaPlayer player) {
            this.player = player;
            return this;
        }

        public DataPacket.Builder string (String string) {
            this.string = string;
            return this;
        }

        public DataPacket.Builder string2 (String string2) {
            this.string2 = string2;
            return this;
        }

        public DataPacket.Builder uuid (UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public DataPacket.Builder integer (int integer) {
            this.integer = integer;
            return this;
        }

        public DataPacket.Builder boolean1 (boolean boolean1) {
            this.boolean1 = boolean1;
            return this;
        }

        public DataPacket.Builder boolean2 (boolean boolean2) {
            this.boolean2 = boolean2;
            return this;
        }

        public DataPacket.Builder stringMap (Map<String, String> stringMap) {
            this.stringMap = stringMap;
            return this;
        }

        public DataPacket.Builder stringList (List<String> stringList) {
            this.stringList = stringList;
            return this;
        }

        public DataPacket.Builder warps (Map<String, List<Warp>> warps) {
            this.warps = warps;
            return this;
        }
    }
}
