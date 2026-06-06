package org.bxwbb.qcpet.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;
import org.bxwbb.qcpet.pet.PetConfig;
import org.bxwbb.qcpet.utils.TextComponentUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PetMenuGuiJava implements PetMenuGui {

    private static final int[] PET_PANEL_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int PET_EGG_SLOT = 20;
    private static final int RENAME_SLOT = 24;
    private static final int HIDE_SLOT = 33;
    private static final int BATH_SLOT = 34;
    private static final int FEED_SLOT = 35;
    private static final int MUTE_SLOT = 41;
    private static final int CLOSE_SLOT = 42;

    private final QcPet plugin;
    private final Map<UUID, RenameSession> renameSessions = new HashMap<>();

    public PetMenuGuiJava(QcPet plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isPetMenu(InventoryHolder holder) {
        return holder instanceof PetMenuHolder;
    }

    @Override
    public void openPetMenu(Player player, Pet pet) {
        Inventory inventory = Bukkit.createInventory(
                new PetMenuHolder(player.getUniqueId(), pet.id()),
                45,
                TextComponentUtil.plain("宠物界面", NamedTextColor.GOLD)
        );

        fillBackground(inventory, Material.GRAY_STAINED_GLASS_PANE);
        fillPetPanel(inventory);
        inventory.setItem(PET_EGG_SLOT, createPetEggItem(player, pet));
        inventory.setItem(RENAME_SLOT, createActionItem(
                Material.NAME_TAG,
                "重命名宠物",
                List.of("点击后进入聊天输入模式", "可修改当前宠物名称")
        ));
        inventory.setItem(HIDE_SLOT, createActionItem(
                Material.BLAZE_POWDER,
                "隐藏宠物",
                List.of("点击后收起当前宠物")
        ));
        inventory.setItem(BATH_SLOT, createBathItem(pet));
        inventory.setItem(FEED_SLOT, createFeedItem(pet));
        inventory.setItem(MUTE_SLOT, createMuteItem(pet));
        inventory.setItem(CLOSE_SLOT, createActionItem(
                Material.BARRIER,
                "关闭界面",
                List.of("不执行任何操作")
        ));
        player.openInventory(inventory);
    }

    @Override
    public void handleInventoryClick(Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetMenuHolder holder)) {
            return;
        }
        if (!holder.ownerUuid().equals(player.getUniqueId())) {
            return;
        }

        int slot = event.getSlot();
        if (slot == HIDE_SLOT) {
            plugin.getPetManger().hidePetAsync(player, holder.petId())
                    .whenComplete((hidden, throwable) -> {
                        if (throwable != null) {
                            send(player, "&c隐藏宠物失败: " + throwable.getMessage());
                            return;
                        }
                        if (!hidden) {
                            send(player, "&c宠物状态已变化，请重新打开界面。");
                            return;
                        }
                        player.closeInventory();
                        send(player, "&a宠物已隐藏。");
                    });
            return;
        }

        if (slot == RENAME_SLOT) {
            renameSessions.put(player.getUniqueId(), new RenameSession(holder.petId()));
            player.closeInventory();
            send(player, "&e请在聊天栏输入新的宠物名称，输入 cancel 取消。");
            if (plugin.getConfig().getBoolean("pet.rename.allow-color-codes", true)) {
                send(player, "&7支持使用 & 颜色代码。");
            }
            return;
        }

        if (slot == BATH_SLOT) {
            Pet pet = plugin.getPetManger().getPet(player, holder.petId());
            if (pet == null) {
                send(player, "&c未找到对应宠物。");
                player.closeInventory();
                return;
            }
            if (!plugin.getPetManger().needsBath(pet)) {
                send(player, "&c这只宠物现在还不想洗澡。");
                return;
            }
            plugin.getPetManger().bathPetAsync(player, holder.petId())
                    .whenComplete((bathedPet, throwable) -> {
                        if (throwable != null) {
                            send(player, "&c洗澡失败: " + throwable.getMessage());
                            return;
                        }
                        if (bathedPet == null) {
                            send(player, "&c未找到对应宠物。");
                            return;
                        }
                        send(player, "&a洗澡完成，获得 " + plugin.getPetManger().getBathRewardExp(bathedPet) + " 经验。");
                        openPetMenu(player, bathedPet);
                    });
            return;
        }

        if (slot == FEED_SLOT) {
            Pet pet = plugin.getPetManger().getPet(player, holder.petId());
            if (pet == null) {
                send(player, "&c未找到对应宠物。");
                player.closeInventory();
                return;
            }
            if (!plugin.getPetManger().needsFeed(pet)) {
                send(player, "&c这只宠物现在还不饿。");
                return;
            }
            plugin.getPetManger().feedPetAsync(player, holder.petId())
                    .whenComplete((fedPet, throwable) -> {
                        if (throwable != null) {
                            send(player, "&c喂食失败: " + throwable.getMessage());
                            return;
                        }
                        if (fedPet == null) {
                            send(player, "&c未找到对应宠物。");
                            return;
                        }
                        send(player, "&a宠物已经吃饱了，获得 " + plugin.getPetManger().getFeedRewardExp(fedPet) + " 经验。");
                        openPetMenu(player, fedPet);
                    });
            return;
        }

        if (slot == MUTE_SLOT) {
            plugin.getPetManger().togglePetMutedAsync(player, holder.petId())
                    .whenComplete((updatedPet, throwable) -> {
                        if (throwable != null) {
                            send(player, "&c切换静音失败: " + throwable.getMessage());
                            return;
                        }
                        if (updatedPet == null) {
                            send(player, "&c未找到对应宠物。");
                            player.closeInventory();
                            return;
                        }
                        send(player, plugin.getPetManger().isPetMuted(updatedPet)
                                ? "&a宠物已设为静音。"
                                : "&a宠物已恢复发声。");
                        openPetMenu(player, updatedPet);
                    });
            return;
        }

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    @Override
    public void handleRenameChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        RenameSession session = renameSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        event.setCancelled(true);
        String rawMessage = event.getMessage().trim();
        if (rawMessage.equalsIgnoreCase("cancel")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> send(player, "&e已取消宠物重命名。"));
            return;
        }

        String normalizedName = plugin.getPetManger().normalizePetName(rawMessage);
        if (normalizedName.isEmpty()) {
            restoreRenameSession(player, session, "名称不能为空，请重新输入，或输入 cancel 取消。");
            return;
        }

        int maxLength = plugin.getConfig().getInt("pet.rename.max-length", 32);
        if (normalizedName.length() > maxLength) {
            restoreRenameSession(player, session, "名称过长，最多 " + maxLength + " 个字符。");
            return;
        }

        String invalidPattern = plugin.getConfig().getString("pet.rename.invalid-pattern", "[\\r\\n\\t]");
        if (containsInvalidCharacters(rawMessage, invalidPattern)) {
            restoreRenameSession(player, session, "名称包含非法字符，请重新输入。");
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getPetManger().renamePetAsync(player, session.petId(), rawMessage)
                        .whenComplete((pet, throwable) -> {
                            if (throwable != null) {
                                send(player, "&c重命名失败: " + throwable.getMessage());
                                return;
                            }
                            if (pet == null) {
                                send(player, "&c未找到对应宠物，可能已被删除。");
                                return;
                            }
                            send(player, "&a宠物已重命名。");
                            openPetMenu(player, pet);
                        })
        );
    }

    @Override
    public void handlePlayerQuit(Player player) {
        renameSessions.remove(player.getUniqueId());
    }

    public ItemStack createPetEggItem(Player player, Pet pet) {
        PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
        boolean blindBox = plugin.getPetManger().isBlindBoxPet(pet);
        Material eggMaterial = blindBox ? Material.CHEST : resolveSpawnEggMaterial(petConfig);
        ItemStack itemStack = new ItemStack(eggMaterial);
        ItemMeta itemMeta = itemStack.getItemMeta();

        int requiredExp = plugin.getPetProgressService().getRequiredExp(pet);
        String progressBar = plugin.getPetProgressService().buildProgressBar(pet, 18);
        boolean muted = plugin.getPetManger().isPetMuted(pet);
        List<Component> lore = new ArrayList<>();
        if (blindBox) {
            lore.add(TextComponentUtil.plain("等级: ???", NamedTextColor.GRAY));
            lore.add(TextComponentUtil.plain("经验: " + pet.exp() + " / " + requiredExp, NamedTextColor.GRAY));
            lore.add(TextComponentUtil.plain("属性: ???", NamedTextColor.GRAY));
        } else {
            lore.add(TextComponentUtil.plain("等级: " + pet.level(), NamedTextColor.GRAY));
            lore.add(TextComponentUtil.plain("经验: " + pet.exp() + " / " + requiredExp, NamedTextColor.GRAY));
            lore.add(TextComponentUtil.plain(progressBar, NamedTextColor.GREEN));
        }
        lore.add(TextComponentUtil.plain("宠物ID: " + pet.id(), NamedTextColor.DARK_GRAY));
        lore.add(TextComponentUtil.plain("状态: " + (pet.show() ? "已出战" : "未出战"), pet.show() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(TextComponentUtil.plain("静音: " + (muted ? "是" : "否"), muted ? NamedTextColor.YELLOW : NamedTextColor.GRAY));

        itemMeta.displayName(TextComponentUtil.legacy(plugin.getPetManger().getDisplayName(pet, player)));
        itemMeta.lore(lore);
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private void fillBackground(Inventory inventory, Material material) {
        ItemStack filler = createGlassPane(material);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void fillPetPanel(Inventory inventory) {
        for (int index = 0; index < PET_PANEL_SLOTS.length; index++) {
            Material material = index % 2 == 0 ? Material.BLACK_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
            inventory.setItem(PET_PANEL_SLOTS[index], createGlassPane(material));
        }
    }

    private ItemStack createBathItem(Pet pet) {
        boolean needsBath = plugin.getPetManger().needsBath(pet);
        List<String> lore = new ArrayList<>();
        if (needsBath) {
            lore.add("点击后给宠物洗澡");
            lore.add("洗完澡会获得 " + plugin.getPetManger().getBathRewardExp(pet) + " 经验");
        } else {
            lore.add("这只宠物现在很干净");
            lore.add("暂时不需要洗澡");
        }
        return createActionItem(
                needsBath ? Material.WATER_BUCKET : Material.BUCKET,
                needsBath ? "给宠物洗澡" : "宠物很干净",
                lore
        );
    }

    private ItemStack createFeedItem(Pet pet) {
        boolean needsFeed = plugin.getPetManger().needsFeed(pet);
        List<String> lore = new ArrayList<>();
        if (needsFeed) {
            lore.add("点击后给宠物喂食");
            lore.add("喂食后会恢复饥饿状态");
            lore.add("喂食后会获得 " + plugin.getPetManger().getFeedRewardExp(pet) + " 经验");
        } else {
            lore.add("这只宠物现在不饿");
            lore.add("暂时不需要喂食");
        }
        return createActionItem(
                needsFeed ? Material.COOKED_BEEF : Material.BREAD,
                needsFeed ? "给宠物喂食" : "宠物不饿",
                lore
        );
    }

    private ItemStack createMuteItem(Pet pet) {
        boolean muted = plugin.getPetManger().isPetMuted(pet);
        return createActionItem(
                muted ? Material.BELL : Material.NOTE_BLOCK,
                muted ? "取消静音" : "静音宠物",
                List.of(
                        "当前状态: " + (muted ? "已静音" : "正常发声"),
                        muted ? "点击后恢复宠物发声" : "点击后让宠物静音"
                )
        );
    }

    private static Material resolveSpawnEggMaterial(PetConfig petConfig) {
        if (petConfig == null) {
            return Material.NAME_TAG;
        }
        String spawnEggName = petConfig.entityType().name() + "_SPAWN_EGG";
        Material spawnEggMaterial = Material.matchMaterial(spawnEggName);
        return spawnEggMaterial == null ? Material.NAME_TAG : spawnEggMaterial;
    }

    private static boolean containsInvalidCharacters(String value, String invalidPattern) {
        try {
            return Pattern.compile(invalidPattern).matcher(value).find();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
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

    private void restoreRenameSession(Player player, RenameSession session, String message) {
        renameSessions.put(player.getUniqueId(), session);
        plugin.getServer().getScheduler().runTask(plugin, () -> send(player, "&c" + message));
    }

    private static void send(Player player, String message) {
        player.sendMessage(TextComponentUtil.legacy(message));
    }

    private record PetMenuHolder(UUID ownerUuid, long petId) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record RenameSession(long petId) {
    }
}
