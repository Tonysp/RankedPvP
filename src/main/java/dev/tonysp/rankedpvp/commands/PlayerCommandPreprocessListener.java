package dev.tonysp.rankedpvp.commands;

import dev.tonysp.rankedpvp.RankedPvP;
import dev.tonysp.rankedpvp.game.GameManager;
import dev.tonysp.rankedpvp.players.ArenaPlayer;
import dev.tonysp.rankedpvp.players.PlayerManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Optional;

public class PlayerCommandPreprocessListener implements Listener {

    public PlayerCommandPreprocessListener (){
        RankedPvP.getInstance().getServer().getPluginManager().registerEvents(this, RankedPvP.getInstance());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand (PlayerCommandPreprocessEvent event){
        Player player = event.getPlayer();
        if(player.isOp())
            return;

        Optional<ArenaPlayer> arenaPlayer = PlayerManager.getInstance().getPlayerIfExists(player.getName());
        if (!arenaPlayer.isPresent())
            return;

        if (!GameManager.getInstance().getInProgress().containsKey(arenaPlayer.get()))
            return;

        event.setCancelled(true);
        event.setMessage("/pvp");
        event.getPlayer().sendMessage(ChatColor.RED + "You cannot use any commands while in a match.");
    }
}