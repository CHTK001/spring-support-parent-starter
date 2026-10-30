package com.chua.starter.software.support.spi.local;

import com.chua.starter.software.support.entity.SoftSoftware;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * macOS 平台本地软件采集器
 *
 * <p>从 /Applications 目录读取 .app 包信息，使用 mdls 提取名称和版本。</p>
 *
 * @since 2026/7/25
 * @author CH
 */
@Slf4j
@Component
public class MacLocalSoftwareCollector implements LocalSoftwareCollector {

    @Override
    /**
     * 支持
     * @return 返回值
    */
    public boolean support() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac") ||
                System.getProperty("os.name", "").toLowerCase().contains("darwin");
    }

    @Override
    /**
     * Collect
     * @return 返回值
    */
    public List<SoftSoftware> collect() {
        List<SoftSoftware> list = new ArrayList<>();
        File appDir = new File("/Applications");
        if (!appDir.exists() || !appDir.isDirectory()) {
            return list;
        }
        File[] apps = appDir.listFiles((dir, name) -> name.endsWith(".app"));
        if (apps == null) {
            return list;
        }
        for (File app : apps) {
            SoftSoftware software = readAppInfo(app);
            if (software != null) {
                list.add(software);
            }
        }
        return list;
    }

    /**
     * 读取app信息
     *
     * @param app app
     * @return 读取app信息的结果
     */
    private SoftSoftware readAppInfo(File app) {
        try {
            SoftSoftware software = new SoftSoftware();
            software.setSoftwareName(app.getName().replace(".app", ""));
            software.setSoftwareCode(app.getName().replace(".app", "").replace(" ", "_"));
            software.setSoftwareDownloadUrl(app.getAbsolutePath());

            String version = getMdls(app, "kMDItemVersion");
            software.setSoftwareVersion(version);

            String iconPath = getMdls(app, "kMDItemIconName");
            if (iconPath != null && new File("/System/Library/CoreServices/CoreTypes.bundle/Contents/Resources/" + iconPath + ".icns").exists()) {
                software.setSoftwareIcon("/System/Library/CoreServices/CoreTypes.bundle/Contents/Resources/" + iconPath + ".icns");
            }

            software.setSoftwareStatus(0);
            return software;
        } catch (Exception e) {
            log.warn("读取 App 信息失败 {}: {}", app.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 获取Mdls
     *
     * @param app app
     * @param attribute attribute
     * @return 获取mdls的结果
     */
    private String getMdls(File app, String attribute) {
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"mdls", "-name", attribute, "-raw", app.getAbsolutePath()}
            );
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String result = reader.readLine();
                if (result != null && !result.equals("(null)")) {
                    return result.replaceAll("^\"|\"$", "").trim();
                }
            }
        } catch (Exception e) {
            log.debug("mdls 读取属性 {} 失败: {}", attribute, e.getMessage());
        }
        return null;
    }
}
