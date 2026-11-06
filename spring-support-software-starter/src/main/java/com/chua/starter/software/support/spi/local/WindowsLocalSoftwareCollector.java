package com.chua.starter.software.support.spi.local;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.software.support.entity.SoftSoftware;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 窗口 平台本地软件采集器
 *
 * <p>从注册表 Uninstall 节点读取已安装软件，提取 DisplayName / DisplayVersion / InstallLocation / DisplayIcon。</p>
 *
 * @since 2026/7/25
 * @author CH
 */
@Slf4j
@Component
public class WindowsLocalSoftwareCollector implements LocalSoftwareCollector {

    /**
     * Uninstall_Key
    */
    private static final Pattern UNINSTALL_KEY = Pattern.compile(
            "SOFTWARE\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Uninstall\\\\([^\\\\]+)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    /**
     * 支持
     * @return 返回值
    */
    public boolean support() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    @Override
    /**
     * Collect
     * @return 返回值
    */
    public List<SoftSoftware> collect() {
        List<SoftSoftware> result = new ArrayList<>();
        result.addAll(scanUninstallKey("HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall"));
        result.addAll(scanUninstallKey("HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall"));
        result.addAll(scanUninstallKey("HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall"));
        return result;
    }

    /**
     * 扫描uninstallKey
     *
     * @param baseKey 基础Key
     * @return 扫描uninstallKey的结果
     */
    private List<SoftSoftware> scanUninstallKey(String baseKey) {
        List<SoftSoftware> list = new ArrayList<>();
        try {
            Process process = new ProcessBuilder(
                    "reg", "query", baseKey
            ).start();
            String output = new String(process.getInputStream().readAllBytes(), "GBK");
            Matcher matcher = UNINSTALL_KEY.matcher(output);
            while (matcher.find()) {
                String subKey = matcher.group(0);
                SoftSoftware software = readSoftwareInfo(subKey);
                if (software != null) {
                    list.add(software);
                }
            }
        } catch (Exception e) {
            log.warn("扫描注册表 {} 失败: {}", baseKey, e.getMessage());
        }
        return list;
    }

    /**
     * 读取software信息
     *
     * @param key Key
     * @return 读取software信息的结果
     */
    private SoftSoftware readSoftwareInfo(String key) {
        try {
            Process process = new ProcessBuilder(
                    "reg", "query", key
            ).start();
            String output = new String(process.getInputStream().readAllBytes(), "GBK");

            String displayName = extractValue(output, "DisplayName");
            if (StringUtils.isBlank(displayName)) {
                return null;
            }

            SoftSoftware software = new SoftSoftware();
            software.setSoftwareName(displayName.trim());
            software.setSoftwareCode(key.replaceAll("\\\\", "_").replaceAll("\\s+", "_"));
            software.setSoftwareVersion(extractValue(output, "DisplayVersion"));
            software.setSoftwareIcon(extractIconPath(output));

            String installLocation = extractValue(output, "InstallLocation");
            software.setSoftwareDownloadUrl(installLocation);

            software.setSoftwareStatus(0);
            return software;
        } catch (Exception e) {
            log.warn("读取软件信息失败 [{}]: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * extract值
     *
     * @param output 输出
     * @param name 名称
     * @return extract值的结果
     */
    private String extractValue(String output, String name) {
        for (String line : output.split("\n")) {
            if (line.contains(name)) {
                String[] parts = line.trim().split("\\s{2,}");
                if (parts.length >= 2) {
                    return parts[parts.length - 1].trim();
                }
            }
        }
        return null;
    }

    /**
     * extracticon路径
     *
     * @param output 输出
     * @return extracticon路径的结果
     */
    private String extractIconPath(String output) {
        String icon = extractValue(output, "DisplayIcon");
        if (icon != null && new File(icon).exists()) {
            return icon;
        }
        return null;
    }
}
