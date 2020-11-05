package dev.tonysp.rankedpvp.data;

import dev.tonysp.plugindata.data.DataPacketManager;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.arenas.Arena;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.players.ArenaPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataPacket extends dev.tonysp.plugindata.data.packets.DataPacket {

    private final Action action;
    private final Warp warp;
    private final ArenaPlayer player;
    private final String string, string2;
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
