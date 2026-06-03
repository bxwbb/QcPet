package org.bxwbb.qcpet.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bxwbb.qcpet.pet.Pet;

public interface PetMenuGui {

    boolean isPetMenu(InventoryHolder holder);

    void openPetMenu(Player player, Pet pet);

    ItemStack createPetEggItem(Player player, Pet pet);

    void handleInventoryClick(Player player, InventoryClickEvent event);

    void handleRenameChat(AsyncPlayerChatEvent event);

    void handlePlayerQuit(Player player);
}
