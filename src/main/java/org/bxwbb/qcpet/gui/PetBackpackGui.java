package org.bxwbb.qcpet.gui;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;
import org.bxwbb.qcpet.utils.TextComponentUtil;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PetBackpackGui {

    private static final int BACKPACK_SIZE = 54;

    private final QcPet plugin;

    public PetBackpackGui(QcPet plugin) {
        this.plugin = plugin;
    }

    public boolean isBackpack(InventoryHolder holder) {
        return holder instanceof PetBackpackHolder;
    }

    public void open(Player player, Pet pet) {
        int unlockedSlots = plugin.getPetBackpackService().getUnlockedSlots(pet);
        int maxSlots = plugin.getPetBackpackService().getMaxSlots(pet);
        Inventory inventory = Bukkit.createInventory(
                new PetBackpackHolder(player.getUniqueId(), pet.id(), unlockedSlots, maxSlots),
                BACKPACK_SIZE,
                TextComponentUtil.plain("宠物背包", NamedTextColor.GOLD)
        );

        ItemStack[] savedContents = plugin.getPetBackpackService().loadContents(pet.id());
        for (int slot = 0; slot < BACKPACK_SIZE; slot++) {
            if (slot < unlockedSlots) {
                inventory.setItem(slot, slot < savedContents.length ? savedContents[slot] : null);
                continue;
            }
            inventory.setItem(slot, slot < maxSlots ? createLockedUpgradeableItem() : createNeverUnlockItem());
        }
        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetBackpackHolder holder)) {
            return;
        }
        if (!holder.ownerUuid().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < BACKPACK_SIZE && rawSlot >= holder.unlockedSlots()) {
            event.setCancelled(true);
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
                if (holder.unlockedSlots() < BACKPACK_SIZE || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    event.setCancelled(true);
                }
            }
        }
    }

    public void handleInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetBackpackHolder holder)) {
            return;
        }
        Set<Integer> rawSlots = event.getRawSlots();
        for (int rawSlot : rawSlots) {
            if (rawSlot >= 0 && rawSlot < BACKPACK_SIZE && rawSlot >= holder.unlockedSlots()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    public void handleInventoryClose(Inventory inventory) {
        if (!(inventory.getHolder() instanceof PetBackpackHolder holder)) {
            return;
        }

        ItemStack[] contents = new ItemStack[BACKPACK_SIZE];
        for (int slot = 0; slot < holder.unlockedSlots(); slot++) {
            contents[slot] = inventory.getItem(slot);
        }
        plugin.getPetBackpackService().saveContents(holder.petId(), contents, holder.unlockedSlots());
    }

    private static ItemStack createLockedUpgradeableItem() {
        ItemStack itemStack = new ItemStack(Material.STRUCTURE_VOID);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(TextComponentUtil.plain("未解锁背包格", NamedTextColor.YELLOW));
        itemMeta.lore(List.of(
                TextComponentUtil.plain("达到更高等级后可解锁", NamedTextColor.GRAY)
        ));
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private static ItemStack createNeverUnlockItem() {
        ItemStack itemStack = new ItemStack(Material.BARRIER);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(TextComponentUtil.plain("不可解锁背包格", NamedTextColor.RED));
        itemMeta.lore(List.of(
                TextComponentUtil.plain("这只宠物无法解锁该格位", NamedTextColor.GRAY)
        ));
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private record PetBackpackHolder(UUID ownerUuid, long petId, int unlockedSlots, int maxSlots) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
