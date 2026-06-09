package org.bxwbb.qcpet.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;
import org.bxwbb.qcpet.pet.PetConfig;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SelectPetGuiBedrock implements SelectPetGui {

    private static final int PAGE_SIZE = 18;

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
        if (!canUseFloodgateForms(player)) {
            fallbackJavaGui.open(player, page);
            return;
        }

        List<Pet> pets = new ArrayList<>(plugin.getPetManger().getPets(player));
        int totalPages = Math.max(1, (int) Math.ceil(pets.size() / (double) PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int startIndex = safePage * PAGE_SIZE;
        int petCountOnPage = Math.min(PAGE_SIZE, Math.max(0, pets.size() - startIndex));
        boolean hasPrev = safePage > 0;
        boolean hasNext = safePage + 1 < totalPages;

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§6选择宠物")
                .content(buildPageContent(player, pets.size(), safePage, totalPages));

        for (int index = 0; index < petCountOnPage; index++) {
            Pet pet = pets.get(startIndex + index);
            builder.button(buildPetButtonLabel(player, pet), FormImage.Type.PATH, "textures/items/name_tag");
        }

        if (hasPrev) {
            builder.button("§e上一页", FormImage.Type.PATH, "textures/ui/arrow_left");
        }
        if (hasNext) {
            builder.button("§e下一页", FormImage.Type.PATH, "textures/ui/arrow_right");
        }
        builder.button("§8关闭", FormImage.Type.PATH, "textures/ui/realms_red_x");

        builder.validResultHandler(response -> Bukkit.getScheduler().runTask(plugin, () -> {
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
            if (hasNext && controlIndex == 0) {
                open(player, safePage + 1);
            }
        }));

        if (!FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder)) {
            fallbackJavaGui.open(player, page);
        }
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

    private String buildPageContent(Player player, int totalPets, int safePage, int totalPages) {
        long shownCount = plugin.getPetManger().getPets(player).stream().filter(Pet::show).count();
        return "§7第 §f" + (safePage + 1) + "§7 / §f" + totalPages + " §7页\n"
                + "§7总宠物数: §f" + totalPets + "\n"
                + "§7当前出战: §f" + shownCount + "\n"
                + "§8点击任意宠物即可出战并打开详情面板";
    }

    private String buildPetButtonLabel(Player player, Pet pet) {
        boolean blindBox = plugin.getPetManger().isBlindBoxPet(pet);
        PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
        String rarity = resolveRarityText(petConfig);
        String type = blindBox ? "???" : pet.type();
        String level = blindBox ? "???" : String.valueOf(pet.level());
        String state = pet.show() ? "§a已出战" : "§7未出战";
        String multiplier = formatMultiplier(pet.times());
        return plugin.getPetManger().getDisplayName(pet, player)
                + "\n§7ID: §f" + pet.id()
                + "  §7Lv: §f" + level
                + "  §7类型: §f" + type
                + "\n§7稀有度: " + rarity
                + "  §7倍率: §f" + multiplier
                + "  " + state;
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
}
