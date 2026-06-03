package org.bxwbb.qcpet.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;
import org.bxwbb.qcpet.utils.TextComponentUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SelectPetGuiJava implements SelectPetGui {

    private static final int[] SELECT_PET_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int SELECT_PREV_SLOT = 45;
    private static final int SELECT_PAGE_SLOT = 49;
    private static final int SELECT_NEXT_SLOT = 53;

    private final QcPet plugin;
    private final PetMenuGui javaPetMenuGui;

    public SelectPetGuiJava(QcPet plugin, PetMenuGui javaPetMenuGui) {
        this.plugin = plugin;
        this.javaPetMenuGui = javaPetMenuGui;
    }

    @Override
    public boolean isSelectMenu(InventoryHolder holder) {
        return holder instanceof PetSelectMenuHolder;
    }

    @Override
    public void open(Player player) {
        open(player, 0);
    }

    @Override
    public void open(Player player, int page) {
        List<Pet> pets = new ArrayList<>(plugin.getPetManger().getPets(player));
        int totalPages = Math.max(1, (int) Math.ceil(pets.size() / (double) SELECT_PET_SLOTS.length));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inventory = Bukkit.createInventory(
                new PetSelectMenuHolder(player.getUniqueId(), safePage),
                54,
                TextComponentUtil.plain("选择宠物", NamedTextColor.GOLD)
        );

        fillBackground(inventory, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        fillSelectionPanel(inventory);

        int startIndex = safePage * SELECT_PET_SLOTS.length;
        for (int index = 0; index < SELECT_PET_SLOTS.length; index++) {
            int petIndex = startIndex + index;
            if (petIndex >= pets.size()) {
                break;
            }
            inventory.setItem(SELECT_PET_SLOTS[index], createSelectPetItem(player, pets.get(petIndex)));
        }

        inventory.setItem(SELECT_PREV_SLOT, createActionItem(
                Material.ARROW,
                "上一页",
                List.of(safePage > 0 ? "点击查看上一页宠物" : "已经是第一页")
        ));
        inventory.setItem(SELECT_PAGE_SLOT, createActionItem(
                Material.BOOK,
                "第 " + (safePage + 1) + " / " + totalPages + " 页",
                List.of("点击宠物即可让它出战")
        ));
        inventory.setItem(SELECT_NEXT_SLOT, createActionItem(
                Material.ARROW,
                "下一页",
                List.of(safePage + 1 < totalPages ? "点击查看下一页宠物" : "已经是最后一页")
        ));

        player.openInventory(inventory);
    }

    @Override
    public void handleInventoryClick(Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetSelectMenuHolder holder)) {
            return;
        }
        if (!holder.ownerUuid().equals(player.getUniqueId())) {
            return;
        }

        int slot = event.getSlot();
        if (slot == SELECT_PREV_SLOT) {
            open(player, holder.page() - 1);
            return;
        }
        if (slot == SELECT_NEXT_SLOT) {
            open(player, holder.page() + 1);
            return;
        }

        int gridIndex = indexOfSlot(SELECT_PET_SLOTS, slot);
        if (gridIndex < 0) {
            return;
        }

        int petIndex = holder.page() * SELECT_PET_SLOTS.length + gridIndex;
        List<Pet> pets = plugin.getPetManger().getPets(player);
        if (petIndex < 0 || petIndex >= pets.size()) {
            return;
        }

        Pet selectedPet = pets.get(petIndex);
        plugin.getPetManger().selectPetAsync(player, selectedPet.id())
                .whenComplete((selected, throwable) -> {
                    if (throwable != null) {
                        send(player, "&c选择宠物失败: " + throwable.getMessage());
                        return;
                    }
                    if (!selected) {
                        send(player, "&c未找到可选择的宠物，ID: " + selectedPet.id());
                        return;
                    }
                    Pet currentPet = plugin.getPetManger().getPet(player, selectedPet.id());
                    send(player, "&a已出战宠物: &r" + plugin.getPetManger().getDisplayName(selectedPet, player));
                    if (currentPet != null) {
                        plugin.getGuiManager().openPetMenuForPlayer(player, currentPet);
                    } else {
                        player.closeInventory();
                    }
                });
    }

    private void fillBackground(Inventory inventory, Material material) {
        ItemStack filler = createGlassPane(material);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void fillSelectionPanel(Inventory inventory) {
        for (int index = 0; index < SELECT_PET_SLOTS.length; index++) {
            Material material = index % 2 == 0 ? Material.BLACK_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
            inventory.setItem(SELECT_PET_SLOTS[index], createGlassPane(material));
        }
    }

    private ItemStack createSelectPetItem(Player player, Pet pet) {
        ItemStack itemStack = javaPetMenuGui.createPetEggItem(player, pet);
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<Component> lore = itemMeta.lore() == null ? new ArrayList<>() : new ArrayList<>(itemMeta.lore());
        lore.add(Component.empty());
        lore.add(TextComponentUtil.plain("点击让这只宠物出战", NamedTextColor.YELLOW));
        itemMeta.lore(lore);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack createActionItem(Material material, String name, List<String> loreLines) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(TextComponentUtil.plain(name, NamedTextColor.WHITE));
        itemMeta.lore(loreLines.stream()
                .map(line -> TextComponentUtil.plain(line, NamedTextColor.GRAY))
                .toList());
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private static ItemStack createGlassPane(Material material) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(TextComponentUtil.plain(" ", NamedTextColor.WHITE));
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private static int indexOfSlot(int[] slots, int targetSlot) {
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] == targetSlot) {
                return index;
            }
        }
        return -1;
    }

    private static void send(Player player, String message) {
        player.sendMessage(TextComponentUtil.legacy(message));
    }

    private record PetSelectMenuHolder(UUID ownerUuid, int page) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
