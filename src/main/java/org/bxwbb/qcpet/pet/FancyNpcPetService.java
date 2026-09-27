package org.bxwbb.qcpet.pet;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import de.oliver.fancynpcs.api.skins.SkinData;
import de.oliver.fancynpcs.api.skins.SkinGeneratedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.entity.Entity;
import org.bxwbb.qcpet.QcPet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 FancyNpcs API 的玩家类型宠物服务。
 * <p>
 * 当 FancyNpcs 插件可用时，使用其 packet NPC 能力生成带皮肤的假玩家宠物，
 * 替代旧的反射式 NmsPlayerPetController。
 * <p>
 * 生命周期：spawn -> teleport / updateName -> remove
 * 所有方法均可在任意线程调用，FancyNpcs 内部会自行切换线程。
 */
public final class FancyNpcPetService implements Listener {

    private final QcPet plugin;
    private final boolean available;

    /** petId -> 正在运行的 FancyNpcs Npc 实例 */
    private final Map<Long, Npc> npcByPetId = new ConcurrentHashMap<>();
    /** FancyNpcs 自动生成的 npcId -> petId */
    private final Map<String, Long> petIdByNpcId = new ConcurrentHashMap<>();

    /** 等待皮肤异步加载的 petId -> skin identifier */
    private final Map<Long, String> pendingSkin = new ConcurrentHashMap<>();

    public FancyNpcPetService(QcPet plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
        if (available) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("已接入 FancyNpcs API，玩家类型宠物将使用 packet NPC");
        } else {
            plugin.getLogger().warning("未检测到 FancyNpcs 插件，PLAYER 类型宠物不可用。请安装 FancyNpcs。");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * 生成一个玩家类型宠物 Npc。
     *
     * @param owner      宠物主人
     * @param pet        宠物数据（entity 字段此时为 null，由本服务托管）
     * @param displayName 最终显示名（已解析占位符和颜色）
     * @param location   出生位置
     * @param skinId     皮肤标识符（username/UUID/URL），空串或 null 表示不设置皮肤
     * @param skinVariant 皮肤变体（AUTO/SLIM）
     */
    public void spawn(Player owner, Pet pet, String displayName, Location location,
                      String skinId, String skinVariant) {
        if (!available) {
            throw new IllegalStateException("FancyNpcs 不可用，无法生成玩家宠物");
        }

        // 用 petId 作为 NpcData.id，方便反查
        String npcId = String.valueOf(pet.id());
        // 如果已经存在旧实例，先清掉
        Npc old = npcByPetId.remove(pet.id());
        if (old != null) {
            petIdByNpcId.remove(old.getData().getId());
            removeQuietly(old);
        }

        NpcData data = new NpcData(
                "QcPetPet",
                owner.getUniqueId(),
                location.clone()
        );
        data.setDisplayName(displayName);
        data.setShowInTab(false);
        data.setSpawnEntity(true);
        data.setCollidable(false);
        data.setVisibilityDistance(Integer.MAX_VALUE);
        data.setScale(0.7f); // 缩小 NPC，骑乘时不穿进头里
        data.setGlowing(false);

        Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
        npc.setSaveToFile(false);
        npc.create();
        FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
        npc.getData().setDisplayName(displayName);
        npc.spawnForAll();
        npcByPetId.put(pet.id(), npc);
        petIdByNpcId.put(npc.getData().getId(), pet.id());

        // 皮肤异步加载，避免主线程阻塞 Mojang API 请求
        if (skinId != null && !skinId.isBlank()) {
            loadSkinAsync(pet.id(), skinId, skinVariant);
        }
    }

    /**
     * 异步加载皮肤，完成后切回主线程刷新 NPC。
     */
    private void loadSkinAsync(long petId, String skinId, String skinVariant) {
        SkinData.SkinVariant variant = parseVariant(skinVariant);
        Thread.startVirtualThread(() -> {
            try {
                SkinData skin = FancyNpcsPlugin.get().getSkinManager().getByIdentifier(skinId, variant);
                if (skin == null || !skin.hasTexture()) {
                    plugin.getLogger().warning("皮肤 [" + skinId + "] 未加载到纹理，宠物将使用默认皮肤");
                    return;
                }
                // 切回主线程更新 NPC
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    Npc npc = npcByPetId.get(petId);
                    if (npc != null) {
                        npc.getData().setSkinData(skin);
                        npc.updateForAll(false);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("加载玩家宠物皮肤失败 [" + skinId + "]: " + e.getMessage());
            }
        });
    }

    /**
     * 传送宠物到指定位置（每 tick 跟随调用）。
     */
    public void teleport(long petId, Location location) {
        Npc npc = npcByPetId.get(petId);
        if (npc == null) {
            return;
        }
        npc.getData().setLocation(location.clone());
        npc.moveForAll(false);
    }

    /**
     * 获取 NPC 当前位置（如果存在），否则 null。
     */
    public Location getCurrentLocation(long petId) {
        Npc npc = npcByPetId.get(petId);
        return npc == null ? null : npc.getData().getLocation();
    }

    /**
     * 让 NPC 看向某位置。
     */
    public void lookAt(Player owner, long petId, Location target) {
        Npc npc = npcByPetId.get(petId);
        if (npc == null) {
            return;
        }
        npc.lookAt(owner, target);
    }

    /**
     * 更新宠物显示名（改名 / 状态变化时调用）。
     */
    public void updateName(long petId, String displayName) {
        Npc npc = npcByPetId.get(petId);
        if (npc == null) {
            return;
        }
        npc.getData().setDisplayName(displayName);
        npc.updateForAll(false);
    }

    /**
     * 设置 NPC 缩放。
     */
    public void setScale(long petId, double scale) {
        Npc npc = npcByPetId.get(petId);
        if (npc == null) {
            return;
        }
        npc.getData().setScale((float) scale);
        npc.updateForAll(false);
    }

    /**
     * 把村民装备同步到 FancyNpcs NPC。
     */
    public void syncEquipment(long petId, org.bukkit.inventory.EntityEquipment villagerEquip) {
        Npc npc = npcByPetId.get(petId);
        if (npc == null || villagerEquip == null) {
            return;
        }
        var data = npc.getData();
        data.getEquipment().clear();
        if (villagerEquip.getHelmet() != null) data.addEquipment(de.oliver.fancynpcs.api.utils.NpcEquipmentSlot.HEAD, villagerEquip.getHelmet().clone());
        if (villagerEquip.getChestplate() != null) data.addEquipment(de.oliver.fancynpcs.api.utils.NpcEquipmentSlot.CHEST, villagerEquip.getChestplate().clone());
        if (villagerEquip.getLeggings() != null) data.addEquipment(de.oliver.fancynpcs.api.utils.NpcEquipmentSlot.LEGS, villagerEquip.getLeggings().clone());
        if (villagerEquip.getBoots() != null) data.addEquipment(de.oliver.fancynpcs.api.utils.NpcEquipmentSlot.FEET, villagerEquip.getBoots().clone());
        if (villagerEquip.getItemInMainHand() != null) data.addEquipment(de.oliver.fancynpcs.api.utils.NpcEquipmentSlot.MAINHAND, villagerEquip.getItemInMainHand().clone());
        if (villagerEquip.getItemInOffHand() != null) data.addEquipment(de.oliver.fancynpcs.api.utils.NpcEquipmentSlot.OFFHAND, villagerEquip.getItemInOffHand().clone());
        npc.updateForAll(false);
    }

    /**
     * 移除并清理宠物。
     */
    public void remove(long petId) {
        Npc npc = npcByPetId.remove(petId);
        petIdByNpcId.values().remove(petId);
        if (npc != null) {
            removeQuietly(npc);
        }
        pendingSkin.remove(petId);
    }

    /**
     * 移除所有托管的宠物（插件卸载时调用）。
     */
    public void removeAll() {
        for (Npc npc : npcByPetId.values()) {
            removeQuietly(npc);
        }
        npcByPetId.clear();
        pendingSkin.clear();
    }

    /**
     * 当 Npc 被外部（如主人换世界）移除后，清理映射。
     */
    public void handleWorldChange(Player owner) {
        // FancyNpcs 自动处理跨世界可见性，这里无需额外操作
    }

    // ===== 内部方法 =====

    private void removeQuietly(Npc npc) {
        try {
            npc.removeForAll();
            FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);
        } catch (Exception e) {
            plugin.getLogger().warning("清理 FancyNpcs 宠物失败: " + e.getMessage());
        }
    }

    private SkinData.SkinVariant parseVariant(String raw) {
        if (raw == null || raw.isBlank()) {
            return SkinData.SkinVariant.AUTO;
        }
        try {
            return SkinData.SkinVariant.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SkinData.SkinVariant.AUTO;
        }
    }

    // ===== 事件监听 =====

    /**
     * 玩家右键村民（PLAYER 类型宠物的服务端实体）时，打开宠物 GUI。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClickVillager(PlayerInteractAtEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (clicked == null) {
            return;
        }
        
        if (!clicked.getScoreboardTags().contains("qcpet_player_pet")) {
            return;
        }
        Player player = event.getPlayer();
        // 根据实体反查 petId：遍历所有宠物找到 entity == clicked 的
        for (java.util.List<Pet> playerPets : plugin.getPetManger().getAllPetsForLookup()) {
            for (Pet pet : playerPets) {
                if (pet.entity() != null && pet.entity().equals(clicked)) {
                    if (pet.owner() != null && pet.owner().getUniqueId().equals(player.getUniqueId())) {
                        plugin.getGuiManager().openPetMenuForPlayer(player, pet);
                        event.setCancelled(true);
                    }
                    return;
                }
            }
        }
    }

    /**
     * 玩家右键 FancyNpcs NPC 时，转发到宠物 GUI。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcInteract(NpcInteractEvent event) {
        String npcId = event.getNpc().getData().getId();
        Long petId = petIdByNpcId.get(npcId);
        if (petId == null) {
            return;
        }

        Pet pet = findPetById(petId);
        if (pet == null) {
            return;
        }

        Player player = event.getPlayer();
        if (pet.owner() == null || !pet.owner().getUniqueId().equals(player.getUniqueId())) {
            return; // 非主人交互不处理（可按需扩展）
        }

        // 普通右键：骑乘；蹲下右键：打开菜单
        if (player.isSneaking()) {
            plugin.getGuiManager().openPetMenuForPlayer(player, pet);
        } else {
            org.bukkit.entity.Entity entity = pet.entity();
            if (entity != null && entity.isValid()) {
                entity.addPassenger(player);
            }
        }
        event.setCancelled(true);
    }

    /**
     * 皮肤异步加载完成后，刷新等待中的宠物。
     */
    @EventHandler
    public void onSkinGenerated(SkinGeneratedEvent event) {
        if (event.getSkin() == null) {
            return;
        }
        String identifier = event.getId();
        // 反查哪个 petId 在等这个皮肤
        for (Map.Entry<Long, String> entry : pendingSkin.entrySet()) {
            if (entry.getValue().equals(identifier)) {
                Long petId = entry.getKey();
                Npc npc = npcByPetId.get(petId);
                if (npc != null) {
                    npc.getData().setSkinData(event.getSkin());
                    npc.updateForAll(false);
                }
                pendingSkin.remove(petId);
                break;
            }
        }
    }

    private Pet findPetById(long petId) {
        for (java.util.List<Pet> playerPets : plugin.getPetManger().getAllPetsForLookup()) {
            for (Pet p : playerPets) {
                if (p.id() == petId) {
                    return p;
                }
            }
        }
        return null;
    }
}
