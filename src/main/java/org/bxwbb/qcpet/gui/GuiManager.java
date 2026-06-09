package org.bxwbb.qcpet.gui;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;
import org.bxwbb.qcpet.pet.PetConfig;

import java.util.UUID;

public class GuiManager implements Listener {

    private final QcPet plugin;
    private final PetMenuGui javaPetMenuGui;
    private final PetMenuGui bedrockPetMenuGui;
    private final SelectPetGui javaSelectPetGui;
    private final SelectPetGui bedrockSelectPetGui;

    public GuiManager(QcPet plugin) {
        this.plugin = plugin;
        this.javaPetMenuGui = new PetMenuGuiJava(plugin);
        this.bedrockPetMenuGui = new PetMenuGuiBedrock(plugin);
        this.javaSelectPetGui = new SelectPetGuiJava(plugin, javaPetMenuGui);
        this.bedrockSelectPetGui = new SelectPetGuiBedrock(plugin, javaPetMenuGui);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPetInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() == null || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();
        if (plugin.getPetManger().handleBlindBoxRevealInteract(player, clickedEntity)) {
            event.setCancelled(true);
            return;
        }
        Pet pet = plugin.getPetManger().getPetByEntity(clickedEntity);
        if (pet == null) {
            return;
        }
        if (pet.owner() == null) {
            return;
        }
        if (!pet.owner().getUniqueId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (player.isSneaking()) {
            event.setCancelled(true);
            getPetMenuGui(player).openPetMenu(player, pet);
            return;
        }

        event.setCancelled(true);
        PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
        if (petConfig != null && !petConfig.rideable()) {
            return;
        }
        if (!clickedEntity.getPassengers().contains(player)) {
            clickedEntity.addPassenger(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (javaPetMenuGui.isPetMenu(holder)) {
            event.setCancelled(true);
            javaPetMenuGui.handleInventoryClick(player, event);
            return;
        }
        if (bedrockPetMenuGui.isPetMenu(holder)) {
            event.setCancelled(true);
            bedrockPetMenuGui.handleInventoryClick(player, event);
            return;
        }
        if (javaSelectPetGui.isSelectMenu(holder)) {
            event.setCancelled(true);
            javaSelectPetGui.handleInventoryClick(player, event);
            return;
        }
        if (bedrockSelectPetGui.isSelectMenu(holder)) {
            event.setCancelled(true);
            bedrockSelectPetGui.handleInventoryClick(player, event);
        }
    }

    @EventHandler
    public void onRenameChat(AsyncPlayerChatEvent event) {
        javaPetMenuGui.handleRenameChat(event);
        bedrockPetMenuGui.handleRenameChat(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        javaPetMenuGui.handlePlayerQuit(event.getPlayer());
        bedrockPetMenuGui.handlePlayerQuit(event.getPlayer());
    }

    public void openPetSelectMenu(Player player) {
        getSelectPetGui(player).open(player);
    }

    public void openPetSelectMenu(Player player, int page) {
        getSelectPetGui(player).open(player, page);
    }

    public boolean isFloodgatePlayer(UUID uuid) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, uuid);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public void openPetMenuForPlayer(Player player, Pet pet) {
        getPetMenuGui(player).openPetMenu(player, pet);
    }

    private PetMenuGui getPetMenuGui(Player player) {
        return isFloodgatePlayer(player.getUniqueId()) ? bedrockPetMenuGui : javaPetMenuGui;
    }

    private SelectPetGui getSelectPetGui(Player player) {
        return isFloodgatePlayer(player.getUniqueId()) ? bedrockSelectPetGui : javaSelectPetGui;
    }
}
