package org.bxwbb.qcpet.gui;

import com.xigua.baseAPI.BaseAPI;
import com.xigua.cumulus.form.CustomForm;
import com.xigua.cumulus.form.SimpleForm;
import com.xigua.cumulus.util.FormImage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;

public class PetMenuGuiBedrock implements PetMenuGui {

    private static final String IMAGE_PATH_PET = "textures/items/name_tag";
    private static final String IMAGE_PATH_RENAME = "textures/ui/pencil_edit_icon";
    private static final String IMAGE_PATH_HIDE = "textures/ui/cancel";
    private static final String IMAGE_PATH_BATH = "textures/items/bucket_water";
    private static final String IMAGE_PATH_FEED = "textures/items/beef_cooked";
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
        BaseAPI baseAPI = getBaseApi();
        if (baseAPI == null) {
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

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§6宠物界面")
                .content(buildPetContent(player, currentPet, needsBath, needsFeed));

        builder.button("§e查看宠物信息", FormImage.Type.PATH, IMAGE_PATH_PET);
        builder.button("§b重命名宠物", FormImage.Type.PATH, IMAGE_PATH_RENAME);
        builder.button("§c隐藏宠物", FormImage.Type.PATH, IMAGE_PATH_HIDE);
        builder.button(needsBath ? "§9给宠物洗澡" : "§7宠物很干净", FormImage.Type.PATH, IMAGE_PATH_BATH);
        builder.button(needsFeed ? "§6给宠物喂食" : "§7宠物不饿", FormImage.Type.PATH, IMAGE_PATH_FEED);
        builder.button("§a刷新界面", FormImage.Type.PATH, IMAGE_PATH_REFRESH);
        builder.button("§e返回宠物列表", FormImage.Type.PATH, IMAGE_PATH_BACK);
        builder.button("§8关闭", FormImage.Type.PATH, IMAGE_PATH_CLOSE);

        builder.validResultHandler((response) -> Bukkit.getScheduler().runTask(plugin, () -> {
            switch (response.clickedButtonId()) {
                case 0 -> openPetMenu(player, currentPet);
                case 1 -> openRenameForm(player, currentPet);
                case 2 -> hidePet(player, currentPet.id());
                case 3 -> handleBath(player, currentPet.id());
                case 4 -> handleFeed(player, currentPet.id());
                case 5 -> openPetMenu(player, currentPet);
                case 6 -> plugin.getGuiManager().openPetSelectMenu(player);
                default -> {
                }
            }
        }));
        builder.closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().openPetSelectMenu(player)));

        baseAPI.sendForm(player.getUniqueId(), builder);
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
        BaseAPI baseAPI = getBaseApi();
        if (baseAPI == null) {
            fallbackJavaGui.openPetMenu(player, pet);
            return;
        }

        CustomForm.Builder builder = CustomForm.builder()
                .title("重命名宠物")
                .label("§7当前名称: §f" + plugin.getPetManger().getDisplayName(pet, player))
                .input("新名称", "请输入新的宠物名称", pet.name());

        builder.validResultHandler((response) -> {
            String newName = response.asInput(1);
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

        baseAPI.sendForm(player.getUniqueId(), builder);
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
            send(player, "§c这只宠物现在还不想洗澡。");
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

    private String buildPetContent(Player player, Pet pet, boolean needsBath, boolean needsFeed) {
        boolean blindBox = plugin.getPetManger().isBlindBoxPet(pet);
        StringBuilder content = new StringBuilder();
        content.append("§f名称: §r").append(plugin.getPetManger().getDisplayName(pet, player)).append('\n');
        content.append("§7ID: §f").append(pet.id()).append('\n');
        content.append("§7状态: ").append(pet.show() ? "§a已出战" : "§7未出战").append('\n');
        content.append("§7等级: §f").append(blindBox ? "???" : pet.level()).append('\n');
        content.append("§7经验: §f").append(pet.exp()).append(" / ").append(plugin.getPetProgressService().getRequiredExp(pet)).append('\n');
        content.append("§7类型: §f").append(blindBox ? "???" : pet.type()).append("\n\n");
        content.append(needsBath ? "§9需要洗澡" : "§7当前不需要洗澡").append('\n');
        content.append(needsFeed ? "§6需要喂食" : "§7当前不需要喂食");
        return content.toString();
    }

    private BaseAPI getBaseApi() {
        return (BaseAPI) Bukkit.getPluginManager().getPlugin("BaseAPI");
    }

    private static void send(Player player, String message) {
        player.sendMessage(message);
    }
}
