package org.bxwbb.qcpet.pet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bxwbb.qcpet.QcPet;

import java.io.File;
import java.io.IOException;

public class PetBackpackService {

    private static final int MAX_BACKPACK_SLOTS = 54;

    private final QcPet plugin;
    private final File backpackDirectory;

    public PetBackpackService(QcPet plugin) {
        this.plugin = plugin;
        this.backpackDirectory = new File(plugin.getDataFolder(), "pet-backpacks");
        if (!backpackDirectory.exists()) {
            backpackDirectory.mkdirs();
        }
    }

    public ItemStack[] loadContents(long petId) {
        ItemStack[] contents = new ItemStack[MAX_BACKPACK_SLOTS];
        File file = resolveBackpackFile(petId);
        if (!file.exists()) {
            return contents;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        for (int slot = 0; slot < MAX_BACKPACK_SLOTS; slot++) {
            contents[slot] = configuration.getItemStack("slots." + slot);
        }
        return contents;
    }

    public void saveContents(long petId, ItemStack[] contents, int unlockedSlots) {
        YamlConfiguration configuration = new YamlConfiguration();
        int safeUnlockedSlots = Math.max(0, Math.min(MAX_BACKPACK_SLOTS, unlockedSlots));
        for (int slot = 0; slot < safeUnlockedSlots; slot++) {
            ItemStack itemStack = slot < contents.length ? contents[slot] : null;
            if (itemStack != null) {
                configuration.set("slots." + slot, itemStack);
            }
        }

        File file = resolveBackpackFile(petId);
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存宠物背包文件: " + file.getName(), exception);
        }
    }

    public int getMaxSlots(Pet pet) {
        PetConfig petConfig = pet == null ? null : plugin.getPetConfigManger().pets.get(pet.type());
        if (petConfig == null) {
            return MAX_BACKPACK_SLOTS;
        }
        return Math.max(0, Math.min(MAX_BACKPACK_SLOTS, petConfig.backpackMaxSlots()));
    }

    public int getUnlockedSlots(Pet pet) {
        if (pet == null) {
            return 0;
        }
        int maxSlots = getMaxSlots(pet);
        int baseSlots = Math.max(0, Math.min(MAX_BACKPACK_SLOTS, plugin.getConfig().getInt("pet.backpack.base-slots", 9)));
        int slotsPerLevel = Math.max(0, plugin.getConfig().getInt("pet.backpack.slots-per-level", 1));
        int unlockedSlots = baseSlots + Math.max(0, pet.level()) * slotsPerLevel;
        return Math.max(0, Math.min(maxSlots, unlockedSlots));
    }

    private File resolveBackpackFile(long petId) {
        return new File(backpackDirectory, petId + ".yml");
    }
}
