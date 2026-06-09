package org.bxwbb.qcpet.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;
import org.bxwbb.qcpet.pet.PetConfig;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Locale;

public class PetMenuGuiBedrock implements PetMenuGui {

    private static final String IMAGE_PATH_PET = "textures/items/name_tag";
    private static final String IMAGE_PATH_RENAME = "textures/ui/pencil_edit_icon";
    private static final String IMAGE_PATH_HIDE = "textures/ui/cancel";
    private static final String IMAGE_PATH_BATH = "textures/items/bucket_water";
    private static final String IMAGE_PATH_FEED = "textures/items/beef_cooked";
    private static final String IMAGE_PATH_MUTE = "textures/ui/sound_glyph_color_2x";
    private static final String IMAGE_PATH_REFRESH = "textures/ui/icon_import";
    private static final String IMAGE_PATH_BACK = "textures/ui/arrow_left";
    private static final String IMAGE_PATH_CLOSE = "textures/ui/realms_red_x";

    private final QcPet plugin;
    private final PetMenuGuiJava fallbackJavaGui;

    public PetMenuGuiBedrock(QcPet plugin) {
        this.plugin = plugin;
        this.fallbackJavaGui = new PetMenuGuiJava(plugin);
    }

    @Override
    public boolean isPetMenu(InventoryHolder holder) {
        return false;
    }

    @Override
    public void openPetMenu(Player player, Pet pet) {
        if (!canUseFloodgateForms(player)) {
            fallbackJavaGui.openPetMenu(player, pet);
            return;
        }

        Pet currentPet = plugin.getPetManger().getPet(player, pet.id());
        if (currentPet == null) {
            send(player, "§c未找到对应宠物。");
            return;
        }

        boolean needsBath = plugin.getPetManger().needsBath(currentPet);
        boolean needsFeed = plugin.getPetManger().needsFeed(currentPet);
        boolean muted = plugin.getPetManger().isPetMuted(currentPet);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§6宠物面板")
                .content(buildPetContent(player, currentPet, needsBath, needsFeed, muted));

        builder.button("§e查看宠物信息", FormImage.Type.PATH, IMAGE_PATH_PET);
        builder.button("§b重命名宠物", FormImage.Type.PATH, IMAGE_PATH_RENAME);
        builder.button("§c隐藏宠物", FormImage.Type.PATH, IMAGE_PATH_HIDE);
        builder.button(needsBath ? "§9给宠物洗澡" : "§7宠物很干净", FormImage.Type.PATH, IMAGE_PATH_BATH);
        builder.button(needsFeed ? "§6给宠物喂食" : "§7宠物不饿", FormImage.Type.PATH, IMAGE_PATH_FEED);
        builder.button(muted ? "§a取消静音" : "§e静音宠物", FormImage.Type.PATH, IMAGE_PATH_MUTE);
        builder.button("§a刷新界面", FormImage.Type.PATH, IMAGE_PATH_REFRESH);
        builder.button("§e返回宠物列表", FormImage.Type.PATH, IMAGE_PATH_BACK);
        builder.button("§8关闭", FormImage.Type.PATH, IMAGE_PATH_CLOSE);

        builder.validResultHandler(response -> Bukkit.getScheduler().runTask(plugin, () -> {
            switch (response.clickedButtonId()) {
                case 0, 6 -> openPetMenu(player, currentPet);
                case 1 -> openRenameForm(player, currentPet);
                case 2 -> hidePet(player, currentPet.id());
                case 3 -> handleBath(player, currentPet.id());
                case 4 -> handleFeed(player, currentPet.id());
                case 5 -> toggleMute(player, currentPet.id());
                case 7 -> plugin.getGuiManager().openPetSelectMenu(player);
                default -> {
                }
            }
        }));
        builder.closedOrInvalidResultHandler(() ->
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().openPetSelectMenu(player)));

        if (!FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder)) {
            fallbackJavaGui.openPetMenu(player, pet);
        }
    }

    @Override
    public ItemStack createPetEggItem(Player player, Pet pet) {
        return fallbackJavaGui.createPetEggItem(player, pet);
    }

    @Override
    public void handleInventoryClick(Player player, InventoryClickEvent event) {
    }

    @Override
    public void handleRenameChat(AsyncPlayerChatEvent event) {
    }

    @Override
    public void handlePlayerQuit(Player player) {
    }

    private void openRenameForm(Player player, Pet pet) {
        if (!canUseFloodgateForms(player)) {
            fallbackJavaGui.openPetMenu(player, pet);
            return;
        }

        CustomForm.Builder builder = CustomForm.builder()
                .title("重命名宠物")
                .label("§7当前名称: §f" + plugin.getPetManger().getDisplayName(pet, player))
                .label("§7输入的新名称会保留模板里的前后缀。")
                .input("新名称", "请输入新的宠物名称", pet.name());

        builder.validResultHandler(response -> {
            String newName = response.asInput(2);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (newName == null || newName.trim().isEmpty()) {
                    send(player, "§c名称不能为空。");
                    openRenameForm(player, pet);
                    return;
                }
                plugin.getPetManger().renamePetAsync(player, pet.id(), newName.trim())
                        .whenComplete((updatedPet, throwable) -> {
                            if (throwable != null) {
                                send(player, "§c重命名失败: " + throwable.getMessage());
                                return;
                            }
                            if (updatedPet == null) {
                                send(player, "§c未找到对应宠物。");
                                return;
                            }
                            send(player, "§a宠物已重命名。");
                            openPetMenu(player, updatedPet);
                        });
            });
        });
        builder.closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, () -> openPetMenu(player, pet)));

        if (!FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder)) {
            fallbackJavaGui.openPetMenu(player, pet);
        }
    }

    private void hidePet(Player player, long petId) {
        plugin.getPetManger().hidePetAsync(player, petId)
                .whenComplete((hidden, throwable) -> {
                    if (throwable != null) {
                        send(player, "§c隐藏宠物失败: " + throwable.getMessage());
                        return;
                    }
                    if (!hidden) {
                        send(player, "§c宠物状态已变化，请稍后重试。");
                        return;
                    }
                    send(player, "§a宠物已隐藏。");
                    plugin.getGuiManager().openPetSelectMenu(player);
                });
    }

    private void handleBath(Player player, long petId) {
        Pet pet = plugin.getPetManger().getPet(player, petId);
        if (pet == null) {
            send(player, "§c未找到对应宠物。");
            return;
        }
        if (!plugin.getPetManger().needsBath(pet)) {
            send(player, "§c这只宠物现在还不需要洗澡。");
            openPetMenu(player, pet);
            return;
        }
        plugin.getPetManger().bathPetAsync(player, petId)
                .whenComplete((updatedPet, throwable) -> {
                    if (throwable != null) {
                        send(player, "§c洗澡失败: " + throwable.getMessage());
                        return;
                    }
                    if (updatedPet == null) {
                        send(player, "§c未找到对应宠物。");
                        return;
                    }
                    send(player, "§a洗澡完成，获得 " + plugin.getPetManger().getBathRewardExp(updatedPet) + " 经验。");
                    openPetMenu(player, updatedPet);
                });
    }

    private void handleFeed(Player player, long petId) {
        Pet pet = plugin.getPetManger().getPet(player, petId);
        if (pet == null) {
            send(player, "§c未找到对应宠物。");
            return;
        }
        if (!plugin.getPetManger().needsFeed(pet)) {
            send(player, "§c这只宠物现在还不饿。");
            openPetMenu(player, pet);
            return;
        }
        plugin.getPetManger().feedPetAsync(player, petId)
                .whenComplete((updatedPet, throwable) -> {
                    if (throwable != null) {
                        send(player, "§c喂食失败: " + throwable.getMessage());
                        return;
                    }
                    if (updatedPet == null) {
                        send(player, "§c未找到对应宠物。");
                        return;
                    }
                    send(player, "§a宠物已经吃饱了，获得 " + plugin.getPetManger().getFeedRewardExp(updatedPet) + " 经验。");
                    openPetMenu(player, updatedPet);
                });
    }

    private void toggleMute(Player player, long petId) {
        plugin.getPetManger().togglePetMutedAsync(player, petId)
                .whenComplete((updatedPet, throwable) -> {
                    if (throwable != null) {
                        send(player, "§c切换静音失败: " + throwable.getMessage());
                        return;
                    }
                    if (updatedPet == null) {
                        send(player, "§c未找到对应宠物。");
                        return;
                    }
                    send(player, plugin.getPetManger().isPetMuted(updatedPet)
                            ? "§a宠物已设为静音。"
                            : "§a宠物已恢复发声。");
                    openPetMenu(player, updatedPet);
                });
    }

    private String buildPetContent(Player player, Pet pet, boolean needsBath, boolean needsFeed, boolean muted) {
        boolean blindBox = plugin.getPetManger().isBlindBoxPet(pet);
        PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
        String rarity = resolveRarityText(petConfig);
        String multiplier = formatMultiplier(pet.times());
        String progressBar = plugin.getPetProgressService().buildProgressBar(pet, 16);
        int requiredExp = plugin.getPetProgressService().getRequiredExp(pet);

        StringBuilder content = new StringBuilder();
        content.append("§f名称: §r").append(plugin.getPetManger().getDisplayName(pet, player)).append('\n');
        content.append("§7ID: §f").append(pet.id()).append('\n');
        content.append("§7状态: ").append(pet.show() ? "§a已出战" : "§7未出战").append('\n');
        content.append("§7静音: ").append(muted ? "§e是" : "§7否").append('\n');
        content.append("§7稀有度: ").append(rarity).append('\n');
        content.append("§7类型: §f").append(blindBox ? "???" : pet.type()).append('\n');
        content.append("§7等级: §f").append(blindBox ? "???" : pet.level()).append('\n');
        content.append("§7经验: §f").append(pet.exp()).append(" / ").append(requiredExp).append('\n');
        content.append("§7进度: §f").append(progressBar).append('\n');
        content.append("§7倍率: §f").append(multiplier).append('\n');

        if (petConfig != null) {
            content.append("§7可骑乘: ").append(petConfig.rideable() ? "§a是" : "§7否").append('\n');
            content.append("§7可移动: ").append(petConfig.movable() ? "§a是" : "§7否").append('\n');
            if (!blindBox && petConfig.expPerMinute() > 0) {
                content.append("§7每分钟经验: §f").append(petConfig.expPerMinute()).append('\n');
            }
        }

        content.append('\n');
        content.append(needsBath ? "§9当前需要洗澡" : "§7当前不需要洗澡").append('\n');
        content.append(needsFeed ? "§6当前需要喂食" : "§7当前不需要喂食").append('\n');
        content.append("§8潜行右键宠物也可以打开菜单");
        return content.toString();
    }

    private boolean canUseFloodgateForms(Player player) {
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            return api != null && api.isFloodgatePlayer(player.getUniqueId());
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private static String resolveRarityText(PetConfig petConfig) {
        String rarity = petConfig == null || petConfig.rarity() == null || petConfig.rarity().isBlank()
                ? "&f普通"
                : petConfig.rarity();
        return rarity.replace('&', '§');
    }

    private static String formatMultiplier(double value) {
        String formatted = String.format(Locale.US, "%.2f", value);
        if (formatted.endsWith("00")) {
            return formatted.substring(0, formatted.length() - 3);
        }
        if (formatted.endsWith("0")) {
            return formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private static void send(Player player, String message) {
        player.sendMessage(message);
    }
}
