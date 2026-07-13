package org.bxwbb.qcpet.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;
import org.bxwbb.qcpet.pet.PetConfig;
import org.bxwbb.qcpet.utils.FoliaSchedulers;
import org.bxwbb.qcpet.utils.TextComponentUtil;
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
    private static final String IMAGE_PATH_BACKPACK = "textures/items/chest";
    private static final String IMAGE_PATH_TRAVEL = "textures/items/compass_item";
    private static final String IMAGE_PATH_MUTE = "textures/ui/sound_glyph_color_2x";
    private static final String IMAGE_PATH_RIDEABLE = "textures/ui/saddle";
    private static final String IMAGE_PATH_RIDE_LOCK = "textures/ui/lead";
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
            send(player, "&c未找到对应宠物。");
            return;
        }

        boolean needsBath = plugin.getPetManger().needsBath(currentPet);
        boolean needsFeed = plugin.getPetManger().needsFeed(currentPet);
        boolean muted = plugin.getPetManger().isPetMuted(currentPet);
        boolean rideable = plugin.getPetManger().isPetPlayerCanRideable(currentPet);
        boolean rideableType = plugin.getPetManger().isPetRideable(currentPet);
        int unlockedBackpackSlots = plugin.getPetBackpackService().getUnlockedSlots(currentPet);
        int maxBackpackSlots = plugin.getPetBackpackService().getMaxSlots(currentPet);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("宠物面板")
                .content(buildPetContent(player, currentPet, needsBath, needsFeed, muted));

        builder.button("查看宠物信息", FormImage.Type.PATH, IMAGE_PATH_PET);
        builder.button("重命名宠物", FormImage.Type.PATH, IMAGE_PATH_RENAME);
        builder.button("隐藏宠物", FormImage.Type.PATH, IMAGE_PATH_HIDE);
        builder.button(needsBath ? "给宠物洗澡" : "宠物很干净", FormImage.Type.PATH, IMAGE_PATH_BATH);
        builder.button(needsFeed ? "给宠物喂食" : "宠物不饿", FormImage.Type.PATH, IMAGE_PATH_FEED);
        builder.button("宠物背包 " + unlockedBackpackSlots + "/" + maxBackpackSlots, FormImage.Type.PATH, IMAGE_PATH_BACKPACK);
        builder.button("去往目标", FormImage.Type.PATH, IMAGE_PATH_TRAVEL);
        builder.button(muted ? "取消静音" : "静音宠物", FormImage.Type.PATH, IMAGE_PATH_MUTE);
        builder.button(!rideableType ? "该宠物不支持骑乘" : rideable ? "禁止骑乘" : "允许骑乘",
                FormImage.Type.PATH, rideable ? IMAGE_PATH_RIDEABLE : IMAGE_PATH_RIDE_LOCK);
        builder.button("刷新界面", FormImage.Type.PATH, IMAGE_PATH_REFRESH);
        builder.button("返回宠物列表", FormImage.Type.PATH, IMAGE_PATH_BACK);
        builder.button("关闭", FormImage.Type.PATH, IMAGE_PATH_CLOSE);

        builder.validResultHandler(response -> FoliaSchedulers.runPlayer(plugin, player, () -> {
            switch (response.clickedButtonId()) {
                case 0, 9 -> openPetMenu(player, currentPet);
                case 1 -> openRenameForm(player, currentPet);
                case 2 -> hidePet(player, currentPet.id());
                case 3 -> handleBath(player, currentPet.id());
                case 4 -> handleFeed(player, currentPet.id());
                case 5 -> plugin.getGuiManager().openPetBackpack(player, currentPet);
                case 6 -> openTravelForm(player, currentPet);
                case 7 -> toggleMute(player, currentPet.id());
                case 8 -> toggleRideable(player, currentPet.id());
                case 10 -> plugin.getGuiManager().openPetSelectMenu(player);
                default -> {
                }
            }
        }));
        builder.closedOrInvalidResultHandler(() ->
                FoliaSchedulers.runPlayer(plugin, player, () -> plugin.getGuiManager().openPetSelectMenu(player)));

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
                .label("当前名称: " + plugin.getPetManger().getDisplayName(pet, player))
                .label("输入的新名称会保留模板里的前后缀。")
                .input("新名称", "请输入新的宠物名称", pet.name());

        builder.validResultHandler(response -> {
            String newName = response.asInput(2);
            FoliaSchedulers.runPlayer(plugin, player, () -> {
                if (newName == null || newName.trim().isEmpty()) {
                    send(player, "&c名称不能为空。");
                    openRenameForm(player, pet);
                    return;
                }
                plugin.getPetManger().renamePetAsync(player, pet.id(), newName.trim())
                        .whenComplete((updatedPet, throwable) -> {
                            if (throwable != null) {
                                send(player, "&c重命名失败: " + throwable.getMessage());
                                return;
                            }
                            if (updatedPet == null) {
                                send(player, "&c未找到对应宠物。");
                                return;
                            }
                            send(player, "&a宠物已重命名。");
                            openPetMenu(player, updatedPet);
                        });
            });
        });
        builder.closedOrInvalidResultHandler(() -> FoliaSchedulers.runPlayer(plugin, player, () -> openPetMenu(player, pet)));

        if (!FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder)) {
            fallbackJavaGui.openPetMenu(player, pet);
        }
    }

    private void openTravelForm(Player player, Pet pet) {
        if (!canUseFloodgateForms(player)) {
            fallbackJavaGui.openPetMenu(player, pet);
            return;
        }

        CustomForm.Builder builder = CustomForm.builder()
                .title("去往目标")
                .label("请输入目标坐标。")
                .label("设置后请先骑上宠物，宠物会自动前往目标点。")
                .input("目标 X", "例如 100", "")
                .input("目标 Z", "例如 200", "");

        builder.validResultHandler(response -> FoliaSchedulers.runPlayer(plugin, player, () -> {
            String rawX = response.asInput(2);
            String rawZ = response.asInput(3);
            if (rawX == null || rawZ == null || rawX.trim().isEmpty() || rawZ.trim().isEmpty()) {
                send(player, "&c坐标不能为空。");
                openTravelForm(player, pet);
                return;
            }

            double x;
            double z;
            try {
                x = Double.parseDouble(rawX.trim());
                z = Double.parseDouble(rawZ.trim());
            } catch (NumberFormatException exception) {
                send(player, "&c坐标必须是数字。");
                openTravelForm(player, pet);
                return;
            }

            boolean scheduled = plugin.getPetManger().scheduleAutoTravel(player, pet.id(), x, z);
            if (!scheduled) {
                send(player, "&c未找到对应宠物，可能已被收回或删除。");
                return;
            }

            send(player, "&a目标点已设置为 &eX: " + x + " Z: " + z);
            send(player, "&e请先骑上这只宠物，宠物会自动前往目标点。");
            Pet updatedPet = plugin.getPetManger().getPet(player, pet.id());
            if (updatedPet != null) {
                openPetMenu(player, updatedPet);
            }
        }));
        builder.closedOrInvalidResultHandler(() -> FoliaSchedulers.runPlayer(plugin, player, () -> openPetMenu(player, pet)));

        if (!FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder)) {
            fallbackJavaGui.openPetMenu(player, pet);
        }
    }

    private void hidePet(Player player, long petId) {
        plugin.getPetManger().hidePetAsync(player, petId)
                .whenComplete((hidden, throwable) -> {
                    if (throwable != null) {
                        send(player, "&c隐藏宠物失败: " + throwable.getMessage());
                        return;
                    }
                    if (!hidden) {
                        send(player, "&c宠物状态已变化，请稍后重试。");
                        return;
                    }
                    send(player, "&a宠物已隐藏。");
                    plugin.getGuiManager().openPetSelectMenu(player);
                });
    }

    private void handleBath(Player player, long petId) {
        Pet pet = plugin.getPetManger().getPet(player, petId);
        if (pet == null) {
            send(player, "&c未找到对应宠物。");
            return;
        }
        if (!plugin.getPetManger().needsBath(pet)) {
            send(player, "&c这只宠物现在还不需要洗澡。");
            openPetMenu(player, pet);
            return;
        }
        plugin.getPetManger().bathPetAsync(player, petId)
                .whenComplete((updatedPet, throwable) -> {
                    if (throwable != null) {
                        send(player, "&c洗澡失败: " + throwable.getMessage());
                        return;
                    }
                    if (updatedPet == null) {
                        send(player, "&c未找到对应宠物。");
                        return;
                    }
                    send(player, "&a洗澡完成，获得 " + plugin.getPetManger().getBathRewardExp(updatedPet) + " 经验。");
                    openPetMenu(player, updatedPet);
                });
    }

    private void handleFeed(Player player, long petId) {
        Pet pet = plugin.getPetManger().getPet(player, petId);
        if (pet == null) {
            send(player, "&c未找到对应宠物。");
            return;
        }
        if (!plugin.getPetManger().needsFeed(pet)) {
            send(player, "&c这只宠物现在还不饿。");
            openPetMenu(player, pet);
            return;
        }
        plugin.getPetManger().feedPetAsync(player, petId)
                .whenComplete((updatedPet, throwable) -> {
                    if (throwable != null) {
                        send(player, "&c喂食失败: " + throwable.getMessage());
                        return;
                    }
                    if (updatedPet == null) {
                        send(player, "&c未找到对应宠物。");
                        return;
                    }
                    send(player, "&a宠物已经吃饱了，获得 " + plugin.getPetManger().getFeedRewardExp(updatedPet) + " 经验。");
                    openPetMenu(player, updatedPet);
                });
    }

    private void toggleMute(Player player, long petId) {
        plugin.getPetManger().togglePetMutedAsync(player, petId)
                .whenComplete((updatedPet, throwable) -> {
                    if (throwable != null) {
                        send(player, "&c切换静音失败: " + throwable.getMessage());
                        return;
                    }
                    if (updatedPet == null) {
                        send(player, "&c未找到对应宠物。");
                        return;
                    }
                    send(player, plugin.getPetManger().isPetMuted(updatedPet)
                            ? "&a宠物已设为静音。"
                            : "&a宠物已恢复发声。");
                    openPetMenu(player, updatedPet);
                });
    }

    /**
     * 切换宠物可骑乘状态
     * @param player 操作玩家
     * @param petId 目标宠物ID
     */
    private void toggleRideable(Player player, long petId) {
        Pet pet = plugin.getPetManger().getPet(player, petId);
        if (pet == null) {
            send(player, "&c未找到对应宠物。");
            return;
        }
        if (!plugin.getPetManger().isPetRideable(pet)) {
            send(player, "&c该宠物类型不支持骑乘。");
            openPetMenu(player, pet);
            return;
        }
        plugin.getPetManger().togglePetRideableAsync(player, petId)
                .whenComplete((updatedPet, throwable) -> {
                    // 捕获异常提示失败
                    if (throwable != null) {
                        send(player, "&c切换骑乘状态失败: " + throwable.getMessage());
                        return;
                    }
                    // 宠物不存在提示
                    if (updatedPet == null) {
                        send(player, "&c未找到对应宠物。");
                        return;
                    }
                    // 根据当前状态发送提示消息
                    send(player, plugin.getPetManger().isPetPlayerCanRideable(updatedPet)
                            ? "&a宠物已开启可骑乘。"
                            : "&a宠物已禁止骑乘。");
                    // 刷新宠物面板
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
        content.append("名称: ").append(plugin.getPetManger().getDisplayName(pet, player)).append('\n');
        content.append("ID: ").append(pet.id()).append('\n');
        content.append("状态: ").append(pet.show() ? "已出战" : "未出战").append('\n');
        content.append("静音: ").append(muted ? "是" : "否").append('\n');
        content.append("稀有度: ").append(rarity).append('\n');
        content.append("类型: ").append(blindBox ? "???" : pet.type()).append('\n');
        content.append("等级: ").append(blindBox ? "???" : pet.level()).append('\n');
        content.append("经验: ").append(pet.exp()).append(" / ").append(requiredExp).append('\n');
        content.append("进度: ").append(progressBar).append('\n');
        content.append("倍率: ").append(multiplier).append('\n');

        if (petConfig != null) {
            content.append("类型支持骑乘: ").append(petConfig.rideable() ? "是" : "否").append('\n');
            content.append("当前允许骑乘: ").append(plugin.getPetManger().isPetPlayerCanRideable(pet) ? "是" : "否").append('\n');
            content.append("可移动: ").append(petConfig.movable() ? "是" : "否").append('\n');
            if (!blindBox && petConfig.expPerMinute() > 0) {
                content.append("每分钟经验: ").append(petConfig.expPerMinute()).append('\n');
            }
        }

        content.append('\n');
        content.append(needsBath ? "当前需要洗澡" : "当前不需要洗澡").append('\n');
        content.append(needsFeed ? "当前需要喂食" : "当前不需要喂食").append('\n');
        content.append("潜行右键宠物也可以打开菜单");
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
        player.sendMessage(TextComponentUtil.legacy(message));
    }
}
