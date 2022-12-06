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

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.tonysp.rankedpvp.game.EventType;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Arena {

    public String name;
    public EventType eventType;
    public List<Warp> teamOneWarps;
    public List<Warp> teamTwoWarps;
    public String region;
    public boolean ranked;

    public HashMap<Location, Material> lobbyDoorBlocks = new HashMap<>();

    public Arena (String name, EventType eventType, List<Warp> teamOneWarps, List<Warp> teamTwoWarps, String region, boolean ranked) {
        this.name = name;
        this.eventType = eventType;
        this.teamOneWarps = teamOneWarps;
        this.teamTwoWarps = teamTwoWarps;
        this.region = region;
        this.ranked = ranked;
    }

    public Arena () {
        teamOneWarps = new ArrayList<>();
        teamTwoWarps = new ArrayList<>();
    }

    public void openEntrance () {
        for (Map.Entry<Location, Material> entry : lobbyDoorBlocks.entrySet()) {
            entry.getKey().getBlock().setType(Material.AIR);
        }
    }

    public void closeEntrance () {
        for (Map.Entry<Location, Material> entry : lobbyDoorBlocks.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }
    }

    public boolean isInArena (ArenaPlayer arenaPlayer) {
        Player player = Bukkit.getPlayer(arenaPlayer.getName());
        if (player == null || !player.isOnline() || player.getLocation().getWorld() == null) {
            return false;
        }

        return isLocationInArena(player.getLocation());
    }

    public boolean isLocationInArena (Location location) {
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(location.getWorld()));
        if (manager == null)
            return false;
        ApplicableRegionSet set = manager.getApplicableRegions(BukkitAdapter.asBlockVector(location));
        for (ProtectedRegion each : set)
            if (each.getId().equalsIgnoreCase(region))
                return true;
        return false;
    }
}