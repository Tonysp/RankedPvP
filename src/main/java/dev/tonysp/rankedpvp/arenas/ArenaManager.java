package dev.tonysp.rankedpvp.arenas;

import dev.tonysp.plugindata.data.packets.BasicDataPacket;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.Utils;
import dev.tonysp.rankedpvp.data.Action;
import dev.tonysp.rankedpvp.data.DataPacket;
import dev.tonysp.rankedpvp.game.EventType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class ArenaManager {

    private static ArenaManager instance;

    private ArrayList<Arena> arenas = new ArrayList<>();
    private HashSet<Arena> lockedArenas = new HashSet<>();


    public static ArenaManager getInstance () {
        return instance;
    }

    public static void initialize (RankedPvP plugin, ConfigurationSection config) {
        instance = new ArenaManager();

        if (!RankedPvP.IS_MASTER) {
            return;
        }

        // Load arenas from config
        RankedPvP.log("Loading arenas:");
        if (config.getConfigurationSection("arenas") != null)
        for (String arenaName : config.getConfigurationSection("arenas").getKeys(false)) {
            RankedPvP.log("... loading arena " + arenaName);
            ConfigurationSection arenaConfig = config.getConfigurationSection("arenas." + arenaName);

            Optional<EventType> eventType = EventType.fromString(arenaConfig.getString("event-type", ""));
            if (!eventType.isPresent()) {
                RankedPvP.logWarning("error while loading arena: invalid event type");
                continue;
            }

            String name = arenaConfig.getString("name");

            boolean isRanked = arenaConfig.getBoolean("ranked", false);

            String regionName = arenaConfig.getString("region", "");

            List<String> teamOneWarpsStrings = arenaConfig.getStringList("team-one-warps");
            List<Warp> teamOneWarps = loadWarps(teamOneWarpsStrings);
            if (teamOneWarps.isEmpty()) {
                RankedPvP.logWarning("error while loading arena: no team-one warps loaded");
                continue;
            }

            List<String> teamTwoWarpsStrings = arenaConfig.getStringList("team-two-warps");
            List<Warp> teamTwoWarps = loadWarps(teamTwoWarpsStrings);
            if (teamTwoWarps.isEmpty()) {
                RankedPvP.logWarning("error while loading arena: no team-two warps loaded");
                continue;
            }

            boolean error = false;
            HashMap<Location, Material> lobbyDoorBlocks = new HashMap<>();
            for (String doorBlockString : arenaConfig.getStringList("lobby-door-blocks")) {
                String[] blockData = doorBlockString.split(",");
                int x = Integer.parseInt(blockData[0]);
                int y = Integer.parseInt(blockData[1]);
                int z = Integer.parseInt(blockData[2]);
                World world = Bukkit.getWorld(blockData[3]);
                if (world == null) {
                    error = true;
                    RankedPvP.logWarning("error while loading arena: invalid world " + blockData[3]);
                    break;
                }
                Material material = Material.valueOf(blockData[4].toUpperCase());

                Location location = new Location(world, x, y, z);
                lobbyDoorBlocks.put(location, material);
            }

            if (error)
                continue;

            Arena arena = new Arena(name, eventType.get(), teamOneWarps, teamTwoWarps, regionName, isRanked);
            arena.lobbyDoorBlocks = lobbyDoorBlocks;
            instance.arenas.add(arena);
        }
    }

    public Optional<Arena> getAndLockFreeArena (EventType eventType) {
        for (Arena arena : arenas) {
            if (arena.eventType == eventType && !lockedArenas.contains(arena)) {
                lockedArenas.add(arena);
                return Optional.of(arena);
            }
        }

        return Optional.empty();
    }

    public void unlockArena (Arena arena) {
        lockedArenas.remove(arena);
        arena.closeEntrance();
    }

    private static List<Warp> loadWarps (List<String> locationStrings) {
        List<Warp> warps = new ArrayList<>();
        for (String locationString : locationStrings) {
            Optional<Location> locationOptional = Utils.teleportLocationFromString(locationString);
            locationOptional.ifPresent(location -> warps.add(Warp.fromLocation(location)));
        }
        return warps;
    }

    public void shareData (String serverId) {
        for (Arena arena : arenas) {
            RankedPvP.log("sharing arena " + arena.name);
            shareArena(arena, serverId);
        }
    }

    public void shareArena (Arena arena, String serverId) {
        ArrayList<String> stringData = new ArrayList<>();
        stringData.add(arena.eventType.toString());
        stringData.add(arena.region);
        stringData.add(arena.name);

        Map<String, List<Warp>> warps = new HashMap<>();
        warps.put("teamOne", arena.teamOneWarps);
        warps.put("teamTwo", arena.teamTwoWarps);
        DataPacket.Builder builder = DataPacket.newBuilder()
                .boolean1(arena.ranked)
                .action(Action.ARENA_DATA)
                .stringList(stringData)
                .warps(warps);
        if (!serverId.isEmpty()) {
            builder.addReceiver(serverId);
            RankedPvP.log("adding receiver " + serverId);
        }
        builder.buildPacket().send();
    }

    public void processData (DataPacket data) {
        Arena arena = new Arena();
        arena.eventType = EventType.valueOf(data.getStringList().get(0).toUpperCase());
        arena.region = data.getStringList().get(1);
        arena.name = data.getStringList().get(2);
        arena.ranked = data.getBoolean();
        arena.teamOneWarps = data.getWarps().get("teamOne");
        arena.teamOneWarps = data.getWarps().get("teamTwo");

        arenas.add(arena);
    }
}
