package org.bxwbb.qcpet.event;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bxwbb.qcpet.QcPet;

public class LoadSaveEvent implements Listener {

    private final QcPet plugin;

    public LoadSaveEvent(QcPet plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info("加载玩家 " + player.getName() + " 的宠物");
        plugin.getPetManger().loadPetsAsync(player)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("加载玩家 " + player.getName() + " 的宠物失败: " + throwable.getMessage());
                    return null;
                });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPetManger().unloadPets(event.getPlayer());
    }
}
