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
        if (player == null || !player.isOnline()) {
            return false;
        }

        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(player.getLocation().getWorld()));
        ApplicableRegionSet set = manager.getApplicableRegions(BukkitAdapter.asBlockVector(player.getLocation()));
        for (ProtectedRegion each : set)
            if (each.getId().equalsIgnoreCase(region))
                return true;
        return false;
    }
}