package org.bxwbb.qcpet.gui;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;
import org.bxwbb.qcpet.utils.TextComponentUtil;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PetEquipmentGui {

    private static final int MENU_SIZE = 27;
    private static final int MAIN_HAND_SLOT = 10;
    private static final int HELMET_SLOT = 12;
    private static final int CHESTPLATE_SLOT = 13;
    private static final int LEGGINGS_SLOT = 14;
    private static final int BOOTS_SLOT = 15;
    private static final int OFF_HAND_SLOT = 16;
    private static final Map<Integer, EquipmentAccessor> EQUIPMENT_SLOTS = Map.of(
            MAIN_HAND_SLOT, new EquipmentAccessor("主手", EntityEquipment::getItemInMainHand, EntityEquipment::setItemInMainHand),
            HELMET_SLOT, new EquipmentAccessor("头盔", EntityEquipment::getHelmet, EntityEquipment::setHelmet),
            CHESTPLATE_SLOT, new EquipmentAccessor("胸甲", EntityEquipment::getChestplate, EntityEquipment::setChestplate),
            LEGGINGS_SLOT, new EquipmentAccessor("护腿", EntityEquipment::getLeggings, EntityEquipment::setLeggings),
            BOOTS_SLOT, new EquipmentAccessor("靴子", EntityEquipment::getBoots, EntityEquipment::setBoots),
            OFF_HAND_SLOT, new EquipmentAccessor("副手", EntityEquipment::getItemInOffHand, EntityEquipment::setItemInOffHand)
    );

    private final QcPet plugin;

    public PetEquipmentGui(QcPet plugin) {
        this.plugin = plugin;
    }

    public boolean isEquipmentMenu(InventoryHolder holder) {
        return holder instanceof PetEquipmentHolder;
    }

    public void open(Player player, Pet pet) {
        Pet currentPet = pet == null ? null : plugin.getPetManger().getPet(player, pet.id());
        if (currentPet == null) {
            send(player, "&c未找到对应宠物。");
            return;
        }

        EntityEquipment equipment = getEquipment(currentPet);
        if (equipment == null) {
            send(player, "&c该宠物当前没有可读取的装备栏，请先放出宠物后再试。");
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                new PetEquipmentHolder(player.getUniqueId(), currentPet.id()),
                MENU_SIZE,
                TextComponentUtil.plain("宠物盔甲栏", NamedTextColor.GOLD)
        );

        fillBackground(inventory);
        for (Map.Entry<Integer, EquipmentAccessor> entry : EQUIPMENT_SLOTS.entrySet()) {
            ItemStack itemStack = entry.getValue().get(equipment);
            inventory.setItem(entry.getKey(), isEmpty(itemStack) ? null : itemStack.clone());
        }
        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetEquipmentHolder holder)) {
            return;
        }

        if (!holder.ownerUuid().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot >= MENU_SIZE) {
            return;
        }

        if (!EQUIPMENT_SLOTS.containsKey(rawSlot)) {
            event.setCancelled(true);
        }
    }

    public void handleInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetEquipmentHolder)) {
            return;
        }
        Set<Integer> rawSlots = event.getRawSlots();
        for (int rawSlot : rawSlots) {
            if (rawSlot >= 0 && rawSlot < MENU_SIZE && !EQUIPMENT_SLOTS.containsKey(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    public void handleInventoryClose(Player player, Inventory inventory) {
        if (!(inventory.getHolder() instanceof PetEquipmentHolder holder)) {
            return;
        }

        Pet pet = plugin.getPetManger().getPet(player, holder.petId());
        EntityEquipment equipment = getEquipment(pet);
        if (equipment == null) {
            returnEditableItems(player, inventory);
            send(player, "&c宠物装备栏已不可用，菜单内物品已退回。");
            return;
        }

        for (Map.Entry<Integer, EquipmentAccessor> entry : EQUIPMENT_SLOTS.entrySet()) {
            ItemStack itemStack = inventory.getItem(entry.getKey());
            entry.getValue().set(equipment, isEmpty(itemStack) ? null : itemStack.clone());
        }
    }

    private EntityEquipment getEquipment(Pet pet) {
        if (pet == null) {
            return null;
        }
        Entity entity = pet.entity();
        if (!(entity instanceof LivingEntity livingEntity) || !entity.isValid() || entity.isDead()) {
            return null;
        }
        return livingEntity.getEquipment();
    }

    private void fillBackground(Inventory inventory) {
        ItemStack filler = createMenuItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack createMenuItem(Material material, String name) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(TextComponentUtil.plain(name, NamedTextColor.WHITE));
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private void returnEditableItems(Player player, Inventory inventory) {
        for (int slot : EQUIPMENT_SLOTS.keySet()) {
            ItemStack itemStack = inventory.getItem(slot);
            if (isEmpty(itemStack)) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack.clone());
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            inventory.setItem(slot, null);
        }
    }

    private static boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir();
    }

    private static void send(Player player, String message) {
        player.sendMessage(TextComponentUtil.legacy(message));
    }

    private record PetEquipmentHolder(UUID ownerUuid, long petId) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record EquipmentAccessor(String name, EquipmentGetter getter, EquipmentSetter setter) {

        private ItemStack get(EntityEquipment equipment) {
            return getter.get(equipment);
        }

        private void set(EntityEquipment equipment, ItemStack itemStack) {
            setter.set(equipment, itemStack);
        }
    }

    @FunctionalInterface
    private interface EquipmentGetter {

        ItemStack get(EntityEquipment equipment);
    }

    @FunctionalInterface
    private interface EquipmentSetter {

        void set(EntityEquipment equipment, ItemStack itemStack);
    }
}
