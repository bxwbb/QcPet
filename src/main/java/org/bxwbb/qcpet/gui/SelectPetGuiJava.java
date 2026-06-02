package org.bxwbb.qcpet.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SelectPetGuiJava implements SelectPetGui {

    @Override
    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 6 * 9, Component.text("宠物选择菜单", TextColor.color(195, 195, 195), TextDecoration.BOLD));
        player.openInventory(inventory);
        if (!page.containsKey(player)) page.put(player, 1);
        update(player, page.get(player));
    }

    @Override
    public void update(Player player, int page) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.displayName(Component.text(""));
            itemStack.setItemMeta(itemMeta);
            inventory.setItem(i, itemStack);
        }
        for (int i = 45; i < 54; i++) {
            ItemStack itemStack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.displayName(Component.text(""));
            itemStack.setItemMeta(itemMeta);
            inventory.setItem(i, itemStack);
        }
    }
}
