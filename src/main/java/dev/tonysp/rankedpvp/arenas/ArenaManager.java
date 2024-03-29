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

package dev.tonysp.rankedpvp.arenas;

import dev.tonysp.rankedpvp.Manager;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.Utils;
import dev.tonysp.rankedpvp.data.Action;
import dev.tonysp.rankedpvp.data.DataPacket;
import dev.tonysp.rankedpvp.game.EventType;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class ArenaManager extends Manager {

    private final ArrayList<Arena> arenas = new ArrayList<>();
    private final HashSet<Arena> lockedArenas = new HashSet<>();

    public ArenaManager (RankedPvP plugin) {
        super(plugin);
    }

    @Override
    public boolean load () {
        arenas.clear();
        lockedArenas.clear();


        RankedPvP plugin = RankedPvP.getInstance();
        ConfigurationSection config = plugin.getConfig();

        if (!plugin.dataPackets().isMaster()) {
            return true;
        }

        // Load arenas from config
        RankedPvP.log("Loading arenas:");
        ConfigurationSection arenasConfig = config.getConfigurationSection("arenas");
        if (arenasConfig == null) {
            return false;
        }
        for (String arenaName : arenasConfig.getKeys(false)) {
            RankedPvP.log("... loading arena " + arenaName);
            ConfigurationSection arenaConfig = config.getConfigurationSection("arenas." + arenaName);
            if (arenaConfig == null) {
                RankedPvP.logWarning("error while loading arena: invalid arena config");
                continue;
            }
            String eventTypeString = arenaConfig.getString("event-type", "");
            Optional<EventType> eventType = EventType.fromString(eventTypeString);
            if (eventType.isEmpty()) {
                RankedPvP.logWarning("error while loading arena: invalid event type");
                continue;
            }

            String name = arenaConfig.getString("name");

            boolean isRanked = arenaConfig.getBoolean("ranked", false);

            String regionName = arenaConfig.getString("region", "");

            if (regionName.isEmpty()) {
                RankedPvP.logWarning("error while loading arena: WorldGuard region name must be specified");
                return false;
            }

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
            if (!arena.teamOneWarps.stream().allMatch(warp -> arena.isLocationInArena(warp.location))
                    || !arena.teamTwoWarps.stream().allMatch(warp -> arena.isLocationInArena(warp.location))) {
                RankedPvP.logWarning("error while loading arena: warps must be inside the arena region");
                continue;
            }
            arena.lobbyDoorBlocks = lobbyDoorBlocks;
            arenas.add(arena);
        }


        return true;
    }

    @Override
    public void unload () {

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
        arena.teamTwoWarps = data.getWarps().get("teamTwo");

        arenas.add(arena);
    }
}
