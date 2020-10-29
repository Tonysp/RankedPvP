package dev.tonysp.rankedpvp.data;

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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class DataPacketProcessor implements Listener {

    private static DataPacketProcessor instance;

    private DataPacketProcessor () {
        RankedPvP.getInstance().getServer().getPluginManager().registerEvents(this, RankedPvP.getInstance());

        DataPacket.newBuilder()
                .action(Action.SERVER_ONLINE)
                .boolean1(true)
                .boolean2(RankedPvP.IS_MASTER)
                .buildPacket()
                .send();
    }

    public static DataPacketProcessor getInstance() {
        if (instance == null) {
            instance = new DataPacketProcessor();
        }
        return instance;
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
                    GameManager.getInstance().tryJoiningQueue(dataPacket.getString(), EventType.valueOf(dataPacket.getString2().toUpperCase()));
                    break;
                case PLAYER_ARENA_LEAVE:
                    GameManager.getInstance().tryLeavingQueue(dataPacket.getString(), EventType.valueOf(dataPacket.getString2().toUpperCase()));
                    break;
                case GAME_ACCEPT_REMINDER:
                    PlayerManager.getInstance().sendAcceptMessageToPlayer(dataPacket.getString(), dataPacket.getInteger(), false);
                    break;
                case PLAYER_MESSAGE:
                    PlayerManager.getInstance().sendMessageToPlayer(dataPacket.getString(), dataPacket.getString2(), false);
                    break;
                case SAVE_PLAYER_LOCATION_AND_TP:
                    PlayerManager.getInstance().savePlayerLocationAndTeleport(dataPacket.getString(), false);
                    break;
                case GAME_ACCEPT:
                    GameManager.getInstance().tryAcceptingGame(dataPacket.getString());
                    break;
                case ANNOUNCE:
                    PlayerManager.getInstance().announce(dataPacket.getString(), dataPacket.getStringList().get(0), dataPacket.getStringList().get(1), false);
                    break;
                case PLAYER_LOCATION:
                    ArenaPlayer arenaPlayer = PlayerManager.getInstance().getOrCreatePlayer(dataPacket.getString());
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
