package org.bxwbb.qcpet;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bxwbb.qcpet.command.QcPetCommand;
import org.bxwbb.qcpet.event.LoadSaveEvent;
import org.bxwbb.qcpet.event.PetProtectionListener;
import org.bxwbb.qcpet.gui.GuiManager;
import org.bxwbb.qcpet.pet.PetConfigManger;
import org.bxwbb.qcpet.pet.PetBackpackService;
import org.bxwbb.qcpet.pet.PetManger;
import org.bxwbb.qcpet.pet.PetProgressService;
import org.bxwbb.qcpet.pet.QcPetPlaceholderExpansion;
import org.bxwbb.qcpet.utils.FancyNpcsDownloader;
import org.bxwbb.qcpet.utils.PetUtil;
import org.bxwbb.qcpet.utils.saveUtil.LocalFileSaveUtil;
import org.bxwbb.qcpet.utils.saveUtil.MySqlSaveUtil;
import org.bxwbb.qcpet.utils.saveUtil.PetStorage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class QcPet extends JavaPlugin {

    private static final boolean FOLIA = detectFolia();

    private PetStorage petStorage;
    private PetUtil petUtil;
    private PetManger petManger;
    private GuiManager guiManager;
    private PetConfigManger petConfigManger;
    private PetProgressService petProgressService;
    private PetBackpackService petBackpackService;

    @Override
    public void onLoad() {
        super.onLoad();
        // 在 onLoad 阶段自动下载并加载 FancyNpcs，确保 onEnable 时 PLAYER 宠物可用
        FancyNpcsDownloader.ensurePresent();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        saveDefaultConfig();
        getLogger().info("QcPet 跨服宠物插件已经启用");
        try (InputStream is = getClass().getResourceAsStream("/logo.txt")) {
            if (is == null) {
                getLogger().info("未找到 logo.txt");
                return;
            }
            printIcon(is, "BXWBB");
        } catch (IOException runtimeException) {
            getLogger().info("logo 加载失败");
        }
        petUtil = new PetUtil(this);
        petManger = new PetManger(this);
        readConfig();
        petConfigManger = new PetConfigManger(this);
        petProgressService = new PetProgressService(petConfigManger);
        petBackpackService = new PetBackpackService(this);
        new LoadSaveEvent(this);
        new PetProtectionListener(this);
        new QcPetCommand(this);
        guiManager = new GuiManager(this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new QcPetPlaceholderExpansion(this).register();
            getLogger().info("PAPI 变量注册成功！");
        } else {
            getLogger().warning("未找到 PlaceholderAPI，自定义变量不可用");
        }
    }

    @Override
    public void onDisable() {
        if (petManger != null) {
            petManger.clear();
        }
        if (petStorage != null) {
            petStorage.close();
        }
        super.onDisable();
    }

    public void readConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        getLogger().info("已加载配置文件: " + config.getString("config-version"));

        PetStorage oldStorage = petStorage;
        try {
            getLogger().info("尝试连接 MySQL 数据库...");
            petStorage = new MySqlSaveUtil(
                    requireJdbcUrl(config.getString("save.sql.url")),
                    requireConfigValue(config, "save.sql.user"),
                    config.getString("save.sql.password", ""),
                    requireConfigValue(config, "save.sql.table")
            );
            getLogger().info("MySQL 连接成功，使用数据库存储");
        } catch (Exception mysqlError) {
            getLogger().warning("MySQL 连接失败，降级为本地文件存储: " + mysqlError.getMessage());
            try {
                petStorage = new LocalFileSaveUtil(this);
            } catch (Exception localError) {
                getLogger().severe("本地文件存储也初始化失败，插件将无法保存宠物数据: " + localError.getMessage());
                petStorage = null;
            }
        }
        if (oldStorage != null) {
            try {
                oldStorage.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void reloadPluginState() {
        readConfig();
        petConfigManger = new PetConfigManger(this);
        petProgressService = new PetProgressService(petConfigManger);
    }

    public void printIcon(InputStream is, String pluginName) {
        try {
            List<String> logoLines = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)).lines().toList();
            int maxLen = logoLines.stream().mapToInt(String::length).max().orElse(30);
            String separator = "-=".repeat(maxLen / 2 + 1);
            if (separator.length() > maxLen) {
                separator = separator.substring(0, maxLen);
            }
            getLogger().info(separator);
            getLogger().info(centerText(pluginName, maxLen));
            getLogger().info(separator);
            for (String line : logoLines) {
                getLogger().info(line);
            }
            getLogger().info(separator);
        } catch (Exception exception) {
            getLogger().severe("打印 LOGO 失败: " + exception.getMessage());
        }
    }

    private String centerText(String text, int length) {
        if (text.length() >= length) {
            return text;
        }
        int pad = (length - text.length()) / 2;
        return " ".repeat(pad) + text;
    }

    private static String requireJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank() || !jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("save.sql.url 必须是有效的 MySQL JDBC 地址，例如 jdbc:mysql://localhost:3306/qcpet");
        }
        return jdbcUrl;
    }

    private static String requireConfigValue(FileConfiguration config, String path) {
        String value = config.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(path + " 不能为空");
        }
        return value;
    }

    /**
     * @deprecated 请使用 {@link #getPetStorage()}
     */
    @Deprecated
    public MySqlSaveUtil getMySqlSaveUtil() {
        return petStorage instanceof MySqlSaveUtil mysql ? mysql : null;
    }

    public PetStorage getPetStorage() {
        return petStorage;
    }

    public PetUtil getPetUtil() {
        return petUtil;
    }

    public PetManger getPetManger() {
        return petManger;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    @Deprecated
    public GuiManager getGuiManger() {
        return guiManager;
    }

    public PetConfigManger getPetConfigManger() {
        return petConfigManger;
    }

    public PetProgressService getPetProgressService() {
        return petProgressService;
    }

    public PetBackpackService getPetBackpackService() {
        return petBackpackService;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        try {
            Class.forName(
                    "io.papermc.paper.threadedregions.RegionizedServer",
                    false,
                    QcPet.class.getClassLoader()
            );
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
