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

import dev.tonysp.plugindata.connections.redis.RedisConnection;
import dev.tonysp.plugindata.data.DataPacketManager;
import dev.tonysp.plugindata.data.events.DataPacketReceiveEvent;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.arenas.ArenaManager;
import dev.tonysp.rankedpvp.arenas.Warp;
import dev.tonysp.rankedpvp.game.EventType;
import dev.tonysp.rankedpvp.game.GameManager;
import dev.tonysp.rankedpvp.game.TwoPlayerGame;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import dev.tonysp.rankedpvp.players.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public class DataPacketProcessor implements Listener {

    private static DataPacketProcessor instance;

    private boolean crossServerEnabled = false;
    private DataPacketManager dataPacketManager;
    private String serverId = "";

    public static DataPacketProcessor getInstance() {
        if (instance == null) {
            instance = new DataPacketProcessor();
        }
        return instance;
    }

    private DataPacketProcessor () {
        FileConfiguration config = RankedPvP.getInstance().getConfig();
        if (!config.getBoolean("cross-server-settings.enabled", false)) {
            return;
        }
        RankedPvP.IS_MASTER = config.getBoolean("cross-server-settings.master", false);

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
            return;
        }
        RankedPvP.log("Initialized Redis connection.");
        redisConnection.test();

        // Initialize DataPacketManager
        serverId = config.getString("cross-server-settings.bungeecord-server-name", "");
        dataPacketManager = new DataPacketManager(RankedPvP.getInstance(), redisConnection, "RankedPvP", serverId, DataPacketManager.DEFAULT_PACKET_SEND_RECEIVE_INTERVAL, DataPacketManager.DEFAULT_CLEAR_OLD_PACKETS);

        RankedPvP.getInstance().getServer().getPluginManager().registerEvents(this, RankedPvP.getInstance());

        crossServerEnabled = true;
    }

    public void shareServerOnline () {
        if (isCrossServerEnabled()) {
            DataPacket.newBuilder()
                    .action(Action.SERVER_ONLINE)
                    .boolean1(true)
                    .boolean2(RankedPvP.IS_MASTER)
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

    public DataPacketManager getDataPacketManager () {
        return dataPacketManager;
    }

    public boolean isCrossServerEnabled () {
        return crossServerEnabled;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDataPacketReceiveEvent (DataPacketReceiveEvent event) {
        if (!event.getDataPacket().getApplicationId().equalsIgnoreCase(RankedPvP.PLUGIN_ID))
            return;

        Bukkit.getScheduler().scheduleSyncDelayedTask(RankedPvP.getInstance(), () -> {
            DataPacket dataPacket = (DataPacket) event.getDataPacket();
            RankedPvP.log("DATAPACKET RECEIVED -> " + dataPacket.getApplicationId() + ", " + dataPacket.getAction());
            Action action = dataPacket.getAction();
            switch (action) {
                case ARENA_DATA:
                    ArenaManager.getInstance().processData(dataPacket);
                    break;
                case PLAYER_DATA:
                    PlayerManager.getInstance().processData(dataPacket);
                    break;
                case PLAYER_ARENA_JOIN:
                    GameManager.getInstance().tryJoiningQueue(dataPacket.getUuid(), EventType.valueOf(dataPacket.getString().toUpperCase()));
                    break;
                case PLAYER_ARENA_LEAVE:
                    GameManager.getInstance().tryLeavingQueue(dataPacket.getUuid(), EventType.valueOf(dataPacket.getString().toUpperCase()));
                    break;
                case GAME_ACCEPT_REMINDER:
                    PlayerManager.getInstance().sendAcceptMessageToPlayer(dataPacket.getString(), dataPacket.getInteger(), false);
                    break;
                case PLAYER_MESSAGE:
                    PlayerManager.getInstance().sendMessageToPlayer(dataPacket.getUuid(), dataPacket.getString(), false);
                    break;
                case SAVE_PLAYER_LOCATION_AND_TP:
                    PlayerManager.getInstance().savePlayerLocationAndTeleport(dataPacket.getUuid(), false);
                    break;
                case GAME_ACCEPT:
                    GameManager.getInstance().tryAcceptingGame(dataPacket.getUuid());
                    break;
                case ANNOUNCE:
                    PlayerManager.getInstance().announce(dataPacket.getString(), dataPacket.getStringList().get(0), dataPacket.getStringList().get(1), false);
                    break;
                case PLAYER_LOCATION:
                    ArenaPlayer arenaPlayer = PlayerManager.getInstance().getOrCreatePlayer(dataPacket.getUuid());
                    if (!GameManager.getInstance().getInProgress().containsKey(arenaPlayer))
                        return;

                    TwoPlayerGame game = (TwoPlayerGame) GameManager.getInstance().getInProgress().get(arenaPlayer);
                    Warp location = dataPacket.getWarp();
                    if (game.playerOne.equals(arenaPlayer)) {
                        game.oneBackLocation = location;
                        game.teleportPlayerOneToLobby();
                    } else {
                        game.twoBackLocation = location;
                        game.teleportPlayerTwoToLobby();
                    }
                    break;
                case WARP_PLAYER:
                    dataPacket.getWarp().warpPlayer(dataPacket.getString(), false);
                    break;
                case SERVER_ONLINE:
                    RankedPvP.getInstance().addServer(dataPacket.getSender());
                    if (dataPacket.getBoolean2()) {
                        RankedPvP.MASTER_ID = dataPacket.getSender();
                        if (GameManager.getInstance().getSpawn() != null) {
                            GameManager.getInstance().getSpawn().server = RankedPvP.MASTER_ID;
                        }
                    } else {
                        ArenaManager.getInstance().shareData(dataPacket.getSender());
                        PlayerManager.getInstance().shareData(dataPacket.getSender());
                    }
                    if (dataPacket.getBoolean()) {
                        DataPacket.newBuilder()
                                .action(Action.SERVER_ONLINE)
                                .boolean1(false)
                                .boolean2(RankedPvP.IS_MASTER)
                                .addReceiver(dataPacket.getSender())
                                .buildPacket()
                                .send();
                    }
                    break;
                default:
                    break;
            }
        });
    }
}
