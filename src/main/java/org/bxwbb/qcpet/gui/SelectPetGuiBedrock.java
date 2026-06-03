package org.bxwbb.qcpet.gui;

import com.xigua.baseAPI.BaseAPI;
import com.xigua.cumulus.form.SimpleForm;
import com.xigua.cumulus.util.FormImage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;

import java.util.ArrayList;
import java.util.List;

public class SelectPetGuiBedrock implements SelectPetGui {

    private final QcPet plugin;
    private final SelectPetGuiJava fallbackJavaGui;

    public SelectPetGuiBedrock(QcPet plugin, PetMenuGui javaPetMenuGui) {
        this.plugin = plugin;
        this.fallbackJavaGui = new SelectPetGuiJava(plugin, javaPetMenuGui);
    }

    @Override
    public boolean isSelectMenu(InventoryHolder holder) {
        return false;
    }

    @Override
    public void open(Player player) {
        open(player, 0);
    }

    @Override
    public void open(Player player, int page) {
        BaseAPI baseAPI = (BaseAPI) Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (baseAPI == null) {
            fallbackJavaGui.open(player, page);
            return;
        }

        List<Pet> pets = new ArrayList<>(plugin.getPetManger().getPets(player));
        int pageSize = 18;
        int totalPages = Math.max(1, (int) Math.ceil(pets.size() / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§6选择宠物")
                .content("§7第 §f" + (safePage + 1) + "§7 / §f" + totalPages + " §7页\n§7点击宠物即可出战");

        int startIndex = safePage * pageSize;
        for (int index = 0; index < pageSize; index++) {
            int petIndex = startIndex + index;
            if (petIndex >= pets.size()) {
                break;
            }
            Pet pet = pets.get(petIndex);
            String label = buildPetButtonLabel(player, pet);
            builder.button(label, FormImage.Type.PATH, "textures/items/name_tag");
        }

        if (safePage > 0) {
            builder.button("§e上一页", FormImage.Type.PATH, "textures/ui/arrow_left");
        }
        if (safePage + 1 < totalPages) {
            builder.button("§e下一页", FormImage.Type.PATH, "textures/ui/arrow_right");
        }
        builder.button("§8关闭", FormImage.Type.PATH, "textures/ui/realms_red_x");

        int petCountOnPage = Math.min(pageSize, Math.max(0, pets.size() - startIndex));
        boolean hasPrev = safePage > 0;
        boolean hasNext = safePage + 1 < totalPages;

        builder.validResultHandler((response) -> Bukkit.getScheduler().runTask(plugin, () -> {
            int clicked = response.clickedButtonId();
            if (clicked < petCountOnPage) {
                selectPet(player, pets.get(startIndex + clicked));
                return;
            }

            int controlIndex = clicked - petCountOnPage;
            if (hasPrev) {
                if (controlIndex == 0) {
                    open(player, safePage - 1);
                    return;
                }
                controlIndex--;
            }
            if (hasNext) {
                if (controlIndex == 0) {
                    open(player, safePage + 1);
                    return;
                }
            }
        }));

        baseAPI.sendForm(player.getUniqueId(), builder);
    }

    @Override
    public void handleInventoryClick(Player player, InventoryClickEvent event) {
    }

    private void selectPet(Player player, Pet selectedPet) {
        plugin.getPetManger().selectPetAsync(player, selectedPet.id())
                .whenComplete((selected, throwable) -> {
                    if (throwable != null) {
                        player.sendMessage("§c选择宠物失败: " + throwable.getMessage());
                        return;
                    }
                    if (!selected) {
                        player.sendMessage("§c未找到可选择的宠物，ID: " + selectedPet.id());
                        return;
                    }
                    Pet currentPet = plugin.getPetManger().getPet(player, selectedPet.id());
                    player.sendMessage("§a已出战宠物: §r" + plugin.getPetManger().getDisplayName(selectedPet, player));
                    if (currentPet != null) {
                        plugin.getGuiManager().openPetMenuForPlayer(player, currentPet);
                    }
                });
    }

    private String buildPetButtonLabel(Player player, Pet pet) {
        boolean blindBox = plugin.getPetManger().isBlindBoxPet(pet);
        String type = blindBox ? "???" : pet.type();
        String level = blindBox ? "???" : String.valueOf(pet.level());
        return plugin.getPetManger().getDisplayName(pet, player)
                + "\n§7ID: §f" + pet.id()
                + "  §7Lv: §f" + level
                + "  §7类型: §f" + type;
    }
}
