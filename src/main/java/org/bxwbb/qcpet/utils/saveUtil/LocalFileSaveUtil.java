package org.bxwbb.qcpet.utils.saveUtil;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * 本地文件存储实现。MySQL 连接失败时的降级方案。
 * 数据存放在 plugins/QcPet/data/ 目录下：
 *  - pets.yml    所有宠物记录
 *  - owners.yml  玩家 -> 宠物ID列表 映射
 *  - next_id.txt 下一个自增宠物ID
 */
public class LocalFileSaveUtil implements PetStorage {

    private final Plugin plugin;
    private final File dataFolder;
    private final File petsFile;
    private final File ownersFile;
    private final File nextIdFile;

    private final YamlConfiguration petsConfig;
    private final YamlConfiguration ownersConfig;
    private final AtomicLong nextId;

    public LocalFileSaveUtil(Plugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("无法创建本地数据目录: " + dataFolder.getAbsolutePath());
        }
        this.petsFile = new File(dataFolder, "pets.yml");
        this.ownersFile = new File(dataFolder, "owners.yml");
        this.nextIdFile = new File(dataFolder, "next_id.txt");

        this.petsConfig = YamlConfiguration.loadConfiguration(petsFile);
        this.ownersConfig = YamlConfiguration.loadConfiguration(ownersFile);
        this.nextId = new AtomicLong(readNextId());

        plugin.getLogger().warning("========================================");
        plugin.getLogger().warning("QcPet 已降级为本地文件存储模式！");
        plugin.getLogger().warning("数据保存在: " + dataFolder.getAbsolutePath());
        plugin.getLogger().warning("请检查 MySQL 配置后重启服务器恢复数据库存储");
        plugin.getLogger().warning("========================================");
    }

    @Override
    public long nextPetId() {
        long id = nextId.getAndIncrement();
        writeNextId();
        return id;
    }

    @Override
    public Optional<MySqlSaveUtil.PetRecord> findPet(long petId) {
        ConfigurationSection section = petsConfig.getConfigurationSection(String.valueOf(petId));
        if (section == null) {
            return Optional.empty();
        }
        return Optional.of(readPet(section, petId));
    }

    @Override
    public List<MySqlSaveUtil.PetRecord> findPetsByPlayer(UUID playerUuid) {
        List<Integer> petIds = ownersConfig.getIntegerList(playerUuid.toString());
        List<MySqlSaveUtil.PetRecord> result = new ArrayList<>();
        for (Integer id : petIds) {
            ConfigurationSection section = petsConfig.getConfigurationSection(String.valueOf(id));
            if (section != null) {
                result.add(readPet(section, id));
            }
        }
        result.sort(Comparator.comparingLong(MySqlSaveUtil.PetRecord::id));
        return result;
    }

    @Override
    public void savePet(MySqlSaveUtil.PetRecord pet) {
        String key = String.valueOf(pet.id());
        petsConfig.set(key + ".name", pet.name());
        petsConfig.set(key + ".type", pet.type());
        petsConfig.set(key + ".level", pet.level());
        petsConfig.set(key + ".exp", pet.exp());
        petsConfig.set(key + ".times", pet.times());
        petsConfig.set(key + ".data", pet.data());
        petsConfig.set(key + ".show", pet.show());
        savePets();
    }

    @Override
    public void bindPetToPlayer(UUID playerUuid, long petId) {
        String playerKey = playerUuid.toString();
        List<Integer> list = new ArrayList<>(ownersConfig.getIntegerList(playerKey));
        if (!list.contains((int) petId)) {
            list.add((int) petId);
            ownersConfig.set(playerKey, list);
            saveOwners();
        }
    }

    @Override
    public boolean deletePet(UUID playerUuid, long petId) {
        String playerKey = playerUuid.toString();
        List<Integer> list = new ArrayList<>(ownersConfig.getIntegerList(playerKey));
        boolean removed = list.remove(Integer.valueOf((int) petId));
        if (!removed) {
            return false;
        }
        ownersConfig.set(playerKey, list.isEmpty() ? null : list);
        petsConfig.set(String.valueOf(petId), null);
        saveOwners();
        savePets();
        return true;
    }

    @Override
    public boolean isAvailable() {
        return dataFolder.exists() && dataFolder.canWrite();
    }

    @Override
    public void close() {
        // 所有写操作都同步落盘了，无需额外关闭
    }

    // ===== 内部方法 =====

    private MySqlSaveUtil.PetRecord readPet(ConfigurationSection section, long id) {
        return new MySqlSaveUtil.PetRecord(
                id,
                section.getString("name", ""),
                section.getString("type", ""),
                section.getInt("level", 1),
                section.getInt("exp", 0),
                section.getDouble("times", 0),
                section.getString("data", "{}"),
                section.getBoolean("show", true)
        );
    }

    private void savePets() {
        try {
            petsConfig.save(petsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "保存 pets.yml 失败", e);
        }
    }

    private void saveOwners() {
        try {
            ownersConfig.save(ownersFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "保存 owners.yml 失败", e);
        }
    }

    private long readNextId() {
        if (!nextIdFile.exists()) {
            return 1;
        }
        try {
            List<String> lines = Files.readAllLines(nextIdFile.toPath());
            if (!lines.isEmpty()) {
                return Long.parseLong(lines.get(0).trim());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "读取 next_id.txt 失败，从 1 开始", e);
        }
        return 1;
    }

    private void writeNextId() {
        try {
            Files.writeString(nextIdFile.toPath(), String.valueOf(nextId.get()));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "写入 next_id.txt 失败", e);
        }
    }
}
