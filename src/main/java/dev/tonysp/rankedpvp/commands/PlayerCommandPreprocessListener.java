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

        Optional<ArenaPlayer> arenaPlayer = PlayerManager.getInstance().getPlayerIfExists(player.getUniqueId());
        if (!arenaPlayer.isPresent())
            return;

        if (!GameManager.getInstance().getInProgress().containsKey(arenaPlayer.get()))
            return;

        event.setCancelled(true);
        event.setMessage("/pvp");
        event.getPlayer().sendMessage(ChatColor.RED + "You cannot use any commands while in a match.");
    }
}