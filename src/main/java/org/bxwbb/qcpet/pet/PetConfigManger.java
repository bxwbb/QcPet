package org.bxwbb.qcpet.pet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bxwbb.qcpet.QcPet;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PetConfigManger {

    private static final String DEFAULT_LEVEL_EXP_REQUIREMENT = "100 + (%1% * 25)";

    private final QcPet plugin;
    private final FileConfiguration petConfig;
    private PetConfigSummary defaultConfigSummary = new PetConfigSummary(
            "default",
            "ARMOR_STAND",
            "%qcpet_key%",
            "%qcpet_key%",
            1,
            "1",
            0,
            25,
            15,
            false,
            new HashMap<>(),
            DEFAULT_LEVEL_EXP_REQUIREMENT,
            new LinkedHashMap<>(),
            new LinkedHashMap<>()
    );

    public final Map<String, PetConfig> pets = new HashMap<>();

    public PetConfigManger(QcPet plugin) {
        this.plugin = plugin;
        File petConfigFile = new File(plugin.getDataFolder(), "pet.yml");
        if (!petConfigFile.exists()) {
            plugin.saveResource("pet.yml", false);
        }
        petConfig = YamlConfiguration.loadConfiguration(petConfigFile);
        if (petConfig.contains("default")) {
            defaultConfigSummary = getPetConfig("default", petConfig.getConfigurationSection("default"));
        }
        loadPetConfig();
    }

    private void loadPetConfig() {
        if (!petConfig.contains("pets")) {
            plugin.getComponentLogger().error("没有定义任何宠物");
            return;
        }

        ConfigurationSection petsSection = petConfig.getConfigurationSection("pets");
        if (petsSection == null) {
            return;
        }

        for (String key : petsSection.getKeys(false)) {
            ConfigurationSection section = petsSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            plugin.getComponentLogger().info(Component.text("正在加载宠物 " + key, TextColor.color(209, 176, 51)));
            pets.put(key, getPetConfig(key, section).toPetConfig());
            plugin.getComponentLogger().info(Component.text("已加载宠物 " + key, TextColor.color(60, 132, 39)));
        }
    }

    private PetConfigSummary getPetConfig(String key, ConfigurationSection section, PetConfigSummary defaultConfig, ConfigurationSection defaultAdd) {
        Map<String, Object> metaData = new LinkedHashMap<>(defaultConfig.metaData());
        Map<String, List<String>> events = new LinkedHashMap<>(defaultConfig.events());
        Map<String, List<String>> blindBoxEvents = new LinkedHashMap<>(defaultConfig.blindBoxEvents());
        mergeMetaData(metaData, defaultAdd.getConfigurationSection("metaData"));
        mergeMetaData(metaData, section.getConfigurationSection("metaData"));
        mergeEvents(events, defaultAdd.getConfigurationSection("events"));
        mergeEvents(events, section.getConfigurationSection("events"));
        mergeEvents(blindBoxEvents, defaultAdd.getConfigurationSection("blindBoxEvents"));
        mergeEvents(blindBoxEvents, section.getConfigurationSection("blindBoxEvents"));
        return new PetConfigSummary(
                key,
                section.contains("type") ? section.getString("type") : defaultConfig.entityType(),
                defaultAdd.getString("+displayName", "")
                        + section.getString("displayName", defaultConfig.displayName())
                        + defaultAdd.getString("displayName+", ""),
                section.getString("displayName", defaultConfig.baseName()),
                section.getDouble("times", defaultConfig.times()) * defaultAdd.getDouble("times*", 1D)
                        + defaultAdd.getDouble("times+", 0D),
                section.getString("scaleRequirement", defaultConfig.scaleRequirement()),
                section.getInt("expPerMinute", defaultConfig.expPerMinute()),
                section.getInt("bathRewardExp", defaultConfig.bathRewardExp()),
                section.getInt("feedRewardExp", defaultConfig.feedRewardExp()),
                section.getBoolean("saveEntityData", defaultConfig.saveEntityData()),
                metaData,
                section.getString("levelExpRequirement", defaultConfig.levelExpRequirement()),
                events,
                blindBoxEvents
        );
    }

    private static void mergeMetaData(Map<String, Object> target, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        target.putAll(section.getValues(false));
    }

    private static void mergeEvents(Map<String, List<String>> target, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            List<String> commands = new ArrayList<>(section.getStringList(key));
            if (commands.isEmpty()) {
                String singleCommand = section.getString(key);
                if (singleCommand != null && !singleCommand.isBlank()) {
                    commands.add(singleCommand);
                }
            }
            target.put(key, commands);
        }
    }

    private PetConfigSummary getPetConfig(String key, ConfigurationSection section) {
        ConfigurationSection defaultAdd = petConfig.getConfigurationSection("defaultAdd");
        if (defaultAdd == null) {
            throw new PetLoadException("未定义 defaultAdd");
        }
        return getPetConfig(key, section, defaultConfigSummary, Objects.requireNonNull(defaultAdd));
    }

    public record PetConfigSummary(
            String key,
            String entityType,
            String displayName,
            String baseName,
            double times,
            String scaleRequirement,
            int expPerMinute,
            int bathRewardExp,
            int feedRewardExp,
            boolean saveEntityData,
            Map<String, Object> metaData,
            String levelExpRequirement,
            Map<String, List<String>> events,
            Map<String, List<String>> blindBoxEvents
    ) {

        public PetConfig toPetConfig() {
            return new PetConfig(
                    displayName,
                    baseName,
                    key,
                    times,
                    scaleRequirement,
                    expPerMinute,
                    bathRewardExp,
                    feedRewardExp,
                    saveEntityData,
                    new LinkedHashMap<>(metaData),
                    EntityType.valueOf(entityType),
                    levelExpRequirement,
                    copyEvents(events),
                    copyEvents(blindBoxEvents)
            );
        }

        private static Map<String, List<String>> copyEvents(Map<String, List<String>> source) {
            Map<String, List<String>> copied = new LinkedHashMap<>();
            source.forEach((key, value) -> copied.put(key, new ArrayList<>(value)));
            return copied;
        }
    }
}
