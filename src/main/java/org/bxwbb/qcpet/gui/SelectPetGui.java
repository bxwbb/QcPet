package org.bxwbb.qcpet.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public interface SelectPetGui {

    boolean isSelectMenu(InventoryHolder holder);

    void open(Player player);

    void open(Player player, int page);

    void handleInventoryClick(Player player, InventoryClickEvent event);
}
