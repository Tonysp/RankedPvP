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

import dev.tonysp.plugindata.connections.redis.RedisConnection;
import dev.tonysp.plugindata.data.events.DataPacketReceiveEvent;
import dev.tonysp.rankedpvp.Manager;
import dev.tonysp.rankedpvp.Messages;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.game.EventType;
import dev.tonysp.rankedpvp.game.TwoPlayerGame;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.HashSet;
import java.util.Set;

public class DataPacketManager extends Manager {

    private boolean crossServerEnabled = false;
    private dev.tonysp.plugindata.data.DataPacketManager dataPacketManager;

    private String masterId;
    private String serverId;
    private boolean isMaster = true;
    private static final Set<String> otherServers = new HashSet<>();
    private static final String pluginId = "rankedpvp";

    public DataPacketManager (RankedPvP pluign) {
        super(pluign);
    }

    @Override
    public boolean load () {
        FileConfiguration config = RankedPvP.getInstance().getConfig();
        if (!config.getBoolean("cross-server-settings.enabled", false)) {
            return true;
        }
        isMaster = config.getBoolean("cross-server-settings.master", false);

        // Initialize Redis connection
        String ip, password;
        int port;
        ip = config.getString("cross-server-settings.redis-ip", "127.0.0.1");
        port = config.getInt("cross-server-settings.redis-port", 6379);
        password = config.getString("cross-server-settings.redis-password", "");
        String connectionName = "RankedPvP-plugin-redis";
        RedisConnection redisConnection;
        try {
            redisConnection = new RedisConnection(RankedPvP.getInstance(), connectionName, ip, password, port);
        } catch (Exception exception) {
            RankedPvP.logWarning("Error while initializing Redis connection!");
            return false;
        }
        RankedPvP.log("Initialized Redis connection.");
        redisConnection.test();

        // Initialize DataPacketManager
        serverId = config.getString("cross-server-settings.bungeecord-server-name", "");
        dataPacketManager = new dev.tonysp.plugindata.data.DataPacketManager(RankedPvP.getInstance(), redisConnection, "RankedPvP", serverId, dev.tonysp.plugindata.data.DataPacketManager.DEFAULT_PACKET_SEND_RECEIVE_INTERVAL, dev.tonysp.plugindata.data.DataPacketManager.DEFAULT_CLEAR_OLD_PACKETS);

        RankedPvP.getInstance().getServer().getPluginManager().registerEvents(this, RankedPvP.getInstance());

        crossServerEnabled = true;

        return true;
    }

    @Override
    public void unload () {
        if (dataPacketManager != null)
            dataPacketManager.shutDown(true);
    }

    public void shareServerOnline () {
        if (isCrossServerEnabled()) {
            DataPacket.newBuilder()
                    .action(Action.SERVER_ONLINE)
                    .boolean1(true)
                    .boolean2(isMaster())
                    .buildPacket()
                    .send();
        }
    }

    public void onDisable () {
        if (dataPacketManager != null) {
            dataPacketManager.shutDown(true);
        }
    }

    public String getServerId () {
        return serverId;
    }

    public dev.tonysp.plugindata.data.DataPacketManager getDataPacketManager () {
        return dataPacketManager;
    }

    public boolean isCrossServerEnabled () {
        return crossServerEnabled;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDataPacketReceiveEvent (DataPacketReceiveEvent event) {
        if (!event.getDataPacket().getApplicationId().equalsIgnoreCase(pluginId))
            return;

        Bukkit.getScheduler().scheduleSyncDelayedTask(RankedPvP.getInstance(), () -> {
            DataPacket dataPacket = (DataPacket) event.getDataPacket();
            RankedPvP.logDebug("DATAPACKET RECEIVED -> " + dataPacket.getApplicationId() + ", " + dataPacket.getAction());
            Action action = dataPacket.getAction();
            switch (action) {
                case ARENA_DATA -> plugin.arenas().processData(dataPacket);
                case PLAYER_DATA -> plugin.players().processData(dataPacket);
                case PLAYER_ARENA_JOIN ->
                        plugin.games().tryJoiningQueue(dataPacket.getUuid(), EventType.valueOf(dataPacket.getString().toUpperCase()));
                case PLAYER_ARENA_LEAVE ->
                        plugin.games().tryLeavingQueue(dataPacket.getUuid(), EventType.valueOf(dataPacket.getString().toUpperCase()));
                case GAME_ACCEPT_REMINDER ->
                        plugin.players().sendAcceptMessageToPlayer(dataPacket.getString(), dataPacket.getInteger(), false);
                case PLAYER_MESSAGE ->
                        plugin.players().sendMessageToPlayer(dataPacket.getUuid(), Messages.getSerializer().deserialize(dataPacket.getString()), false);
                case SAVE_PLAYER_LOCATION_AND_TP ->
                        plugin.players().savePlayerLocationAndTeleport(dataPacket.getUuid(), false);
                case GAME_ACCEPT -> plugin.games().tryAcceptingGame(dataPacket.getUuid());
                case ANNOUNCE ->
                        plugin.players().announce(Messages.getSerializer().deserialize(dataPacket.getString()), dataPacket.getStringList().get(0), dataPacket.getStringList().get(1), false);
                case PLAYER_LOCATION -> {
                    ArenaPlayer arenaPlayer = plugin.players().getOrCreatePlayer(dataPacket.getUuid());
                    if (!plugin.games().getInProgress().containsKey(arenaPlayer))
                        return;
                    TwoPlayerGame game = (TwoPlayerGame) plugin.games().getInProgress().get(arenaPlayer);
                    Warp location = dataPacket.getWarp();
                    if (game.playerOne.equals(arenaPlayer)) {
                        game.oneBackLocation = location;
                        game.teleportPlayerOneToLobby();
                    } else {
                        game.twoBackLocation = location;
                        game.teleportPlayerTwoToLobby();
                    }
                }
                case WARP_PLAYER -> dataPacket.getWarp().warpPlayer(dataPacket.getString(), false);
                case SERVER_ONLINE -> {
                    addServer(dataPacket.getSender());
                    if (dataPacket.getBoolean2()) {
                        masterId = dataPacket.getSender();
                        if (plugin.games().getSpawn() != null) {
                            plugin.games().getSpawn().server = masterId;
                        }
                    } else {
                        plugin.arenas().shareData(dataPacket.getSender());
                        plugin.players().shareData(dataPacket.getSender());
                    }
                    if (dataPacket.getBoolean()) {
                        DataPacket.newBuilder()
                                .action(Action.SERVER_ONLINE)
                                .boolean1(false)
                                .boolean2(isMaster())
                                .addReceiver(dataPacket.getSender())
                                .buildPacket()
                                .send();
                    }
                }
                default -> {
                }
            }
        });
    }

    public void addServer (String serverId) {
        otherServers.add(serverId);
    }

    public boolean isMaster () {
        return isMaster;
    }

    public String getMasterId () {
        return masterId;
    }
}
