package dev.tonysp.rankedpvp.arenas;

import dev.tonysp.plugindata.PluginData;
import dev.tonysp.plugindata.data.DataPacketManager;
import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.data.Action;
import dev.tonysp.rankedpvp.data.DataPacket;
import dev.tonysp.rankedpvp.data.DataPacketProcessor;
import dev.tonysp.rankedpvp.players.PlayerManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

public class Warp implements Serializable, Comparable<Warp> {

    public String name;
    public transient Location location;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public String world;
    public String server;
    public int id = 0;

    public Warp() {
    }

    public boolean isOnLocalServer () {
        if (DataPacketProcessor.getInstance().isCrossServerEnabled()) {
            return this.server.equalsIgnoreCase(DataPacketProcessor.getInstance().getServerId());
        } else {
            return true;
        }
    }

    public void warpPlayer (String playerName, boolean share) {
        final Player player = Bukkit.getPlayer(playerName);
        /*if (player != null) {
            RankedPvP.log("WARPING ONLINE: " + player.isOnline());
        }*/
        if (isOnLocalServer()) {
            if (player != null && player.isOnline()) {
                World world = Bukkit.getWorld(this.world);
                if (world == null)
                    return;

                Optional<Location> location = this.getLocation();
                if (!location.isPresent())
                    return;

                player.teleport(location.get(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            } else {
                if (share && DataPacketProcessor.getInstance().isCrossServerEnabled()) {
                    DataPacket.newBuilder()
                            .action(Action.WARP_PLAYER)
                            .warp(this)
                            .string(playerName)
                            .buildPacket()
                            .send();
                }

                PlayerManager.getInstance().addPlayerToWarp(playerName, this);
            }
        } else {
            if (share) {
                DataPacket.newBuilder()
                        .action(Action.WARP_PLAYER)
                        .addReceiver(server)
                        .warp(this)
                        .string(playerName)
                        .buildPacket()
                        .send();
            }
            if (player != null && player.isOnline()) {
                RankedPvP.log("SWITCHING SERVER");
                PlayerManager.getInstance().switchServer(player, server);
            }
        }
    }

    public static Warp fromLocation (Location location) throws IllegalArgumentException {
        Warp warp = new Warp();
        if (location.getWorld() != null) {
            warp.world = location.getWorld().getName();
        } else {
            throw new IllegalArgumentException("World " + warp.world + " does not exist!");
        }
        warp.x = location.getX();
        warp.y = location.getY();
        warp.z = location.getZ();
        warp.yaw = location.getYaw();
        warp.pitch = location.getPitch();
        warp.server = DataPacketProcessor.getInstance().getServerId();
        return warp;
    }

    public Optional<Location> getLocation () {
        World world = Bukkit.getWorld(this.world);
        if (world == null) {
            return Optional.empty();
        } else {
            this.location = new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
            return Optional.of(this.location);
        }
    }


    @Override
    public String toString () {
        return "Warp{" +
                "name='" + name + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", yaw=" + yaw +
                ", pitch=" + pitch +
                ", world='" + world + '\'' +
                ", server='" + server + '\'' +
                ", id=" + id +
                '}';
    }

    @Override
    public boolean equals (Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Warp warp = (Warp) o;
        return Double.compare(warp.x, x) == 0 &&
                Double.compare(warp.y, y) == 0 &&
                Double.compare(warp.z, z) == 0 &&
                Float.compare(warp.yaw, yaw) == 0 &&
                Float.compare(warp.pitch, pitch) == 0 &&
                id == warp.id &&
                Objects.equals(name, warp.name) &&
                Objects.equals(world, warp.world) &&
                Objects.equals(server, warp.server);
    }

    @Override
    public int hashCode () {
        return Objects.hash(name, x, y, z, yaw, pitch, world, server, id);
    }

    public int compareTo (Warp anotherWarp) {
        return anotherWarp.name.compareTo(this.name);
    }
}
