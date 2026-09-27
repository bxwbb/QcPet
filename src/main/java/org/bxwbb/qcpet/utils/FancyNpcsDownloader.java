package org.bxwbb.qcpet.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.logging.Level;

/**
 * 自动下载并加载 FancyNpcs 插件。
 * 当服务器未安装 FancyNpcs 时，从官方 Maven 仓库下载所需版本。
 */
public final class FancyNpcsDownloader {

    /** 需要的 FancyNpcs 版本（与 pom.xml 中 ${fancynpcs.version} 保持一致） */
    private static final String REQUIRED_VERSION = "2.9.2";
    private static final String DOWNLOAD_URL =
            "https://repo.fancyinnovations.com/releases/de/oliver/FancyNpcs/"
                    + REQUIRED_VERSION + "/FancyNpcs-" + REQUIRED_VERSION + ".jar";

    private FancyNpcsDownloader() {
    }

    /**
     * 检查 FancyNpcs 是否可用；不可用则下载并加载。
     * 必须在 QcPet.onLoad() 阶段调用，确保 onEnable() 时 FancyNpcs 已就绪。
     *
     * @return true 如果 FancyNpcs 已可用（已安装或下载加载成功）
     */
    public static boolean ensurePresent() {
        PluginManager pm = Bukkit.getPluginManager();
        Plugin existing = pm.getPlugin("FancyNpcs");
        if (existing != null) {
            Bukkit.getLogger().info("[QcPet] FancyNpcs 已安装，版本: " + existing.getDescription().getVersion());
            return true;
        }

        Bukkit.getLogger().warning("[QcPet] 未检测到 FancyNpcs，正在自动下载 v" + REQUIRED_VERSION + "...");

        File pluginsDir = Bukkit.getPluginsFolder();
        File targetFile = new File(pluginsDir, "FancyNpcs-" + REQUIRED_VERSION + ".jar");

        // 如果 plugins 目录里已有 jar 但未加载，直接加载
        if (targetFile.exists()) {
            Bukkit.getLogger().info("[QcPet] 发现已下载的 FancyNpcs jar，直接加载");
            return loadAndEnable(targetFile);
        }

        // 下载
        try {
            download(DOWNLOAD_URL, targetFile);
            Bukkit.getLogger().info("[QcPet] FancyNpcs 下载完成: " + targetFile.getName());
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[QcPet] 下载 FancyNpcs 失败，请手动安装: " + DOWNLOAD_URL, e);
            return false;
        }

        return loadAndEnable(targetFile);
    }

    private static boolean loadAndEnable(File jarFile) {
        try {
            PluginManager pm = Bukkit.getPluginManager();
            // onLoad 阶段允许 loadPlugin
            Plugin plugin = pm.loadPlugin(jarFile);
            if (plugin == null) {
                Bukkit.getLogger().severe("[QcPet] 加载 FancyNpcs 失败: loadPlugin 返回 null");
                return false;
            }
            pm.enablePlugin(plugin);
            Bukkit.getLogger().info("[QcPet] FancyNpcs 已自动加载并启用，版本: " + plugin.getDescription().getVersion());
            return true;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[QcPet] 加载 FancyNpcs 失败，请重启服务器后重试", e);
            return false;
        }
    }

    private static void download(String url, File target) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("下载失败，HTTP " + response.statusCode());
        }

        Path temp = Files.createTempFile("fancynpcs", ".jar");
        try (InputStream in = response.body()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }

        // 校验文件大小，防止下载到错误页面
        if (Files.size(temp) < 100_000) {
            Files.deleteIfExists(temp);
            throw new RuntimeException("下载的文件过小（" + Files.size(temp) + " 字节），可能不是有效 jar");
        }

        Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
