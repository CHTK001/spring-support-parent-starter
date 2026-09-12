package com.chua.starter.software.support.spi.local;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.software.support.entity.SoftSoftware;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* Linux 平台本地软件采集器
*
* <p>通过 dpkg / rpm / pacman 命令读取已安装包信息，结合 .desktop 文件提取图标和应用名称。</p>
*
* @since 2026/7/25
* @author CH
 */
@Slf4j
@Component
public class LinuxLocalSoftwareCollector implements LocalSoftwareCollector {

    /** Desktop_名称 */
    private static final Pattern DESKTOP_NAME = Pattern.compile("^Name=(.+)$", Pattern.CASE_INSENSITIVE);
    /** Desktop_icon */
    private static final Pattern DESKTOP_ICON = Pattern.compile("^Icon=(.+)$", Pattern.CASE_INSENSITIVE);
    /** Desktop_执行 */
    private static final Pattern DESKTOP_EXEC = Pattern.compile("^Exec=(.+)$", Pattern.CASE_INSENSITIVE);

    @Override
    /** 支持 */
    public boolean support() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux") && !os.contains("darwin");
    }

    @Override
    /** Collect */
    public List<SoftSoftware> collect() {
        List<SoftSoftware> result = new ArrayList<>();
        result.addAll(scanDpkg());
        result.addAll(scanRpm());
        result.addAll(scanDesktopFiles());
        return result;
    }

    /**
    * 扫描Dpkg
    *
    * @return 扫描dpkg的结果
     */
    private List<SoftSoftware> scanDpkg() {
        List<SoftSoftware> list = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"bash", "-c", "dpkg -l | grep '^ii' | awk '{print $2, $3}'"}
            );
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        SoftSoftware software = new SoftSoftware();
                        software.setSoftwareName(parts[0]);
                        software.setSoftwareCode(parts[0].replace(".", "_"));
                        software.setSoftwareVersion(parts[1]);
                        software.setSoftwareStatus(0);
                        software.setSoftwareCategory("linux-package");
                        list.add(software);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("dpkg 扫描失败: {}", e.getMessage());
        }
        return list;
    }

    /**
    * 扫描Rpm
    *
    * @return 扫描rpm的结果
     */
    private List<SoftSoftware> scanRpm() {
        List<SoftSoftware> list = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"bash", "-c", "rpm -qa --qf '%{NAME} %{VERSION}\\n'"}
            );
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        SoftSoftware software = new SoftSoftware();
                        software.setSoftwareName(parts[0]);
                        software.setSoftwareCode(parts[0].replace(".", "_"));
                        software.setSoftwareVersion(parts[1]);
                        software.setSoftwareStatus(0);
                        software.setSoftwareCategory("linux-package");
                        list.add(software);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("rpm 扫描失败: {}", e.getMessage());
        }
        return list;
    }

    /**
    * 扫描desktop文件
    *
    * @return 扫描desktop文件的结果
     */
    private List<SoftSoftware> scanDesktopFiles() {
        List<SoftSoftware> list = new ArrayList<>();
        String[] dirs = {
                "/usr/share/applications",
                "/usr/local/share/applications",
                System.getProperty("user.home") + "/.local/share/applications"
        };
        for (String dir : dirs) {
            File d = new File(dir);
            if (!d.exists() || !d.isDirectory()) {
                continue;
            }
            for (File file : d.listFiles((f, name) -> name.endsWith(".desktop"))) {
                SoftSoftware software = parseDesktopFile(file);
                if (software != null && !containsName(list, software.getSoftwareName())) {
                    list.add(software);
                }
            }
        }
        return list;
    }

    /**
    * 解析desktop文件
    *
    * @param file 文件
    * @return 解析desktop文件的结果
     */
    private SoftSoftware parseDesktopFile(File file) {
        SoftSoftware software = null;
        String iconPath = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                Matcher nameMatcher = DESKTOP_NAME.matcher(line);
                if (nameMatcher.find()) {
                    software = new SoftSoftware();
                    software.setSoftwareName(nameMatcher.group(1).trim());
                    software.setSoftwareCode(file.getName().replace(".desktop", "").replace(" ", "_"));
                    software.setSoftwareCategory("desktop");
                }
                Matcher iconMatcher = DESKTOP_ICON.matcher(line);
                if (iconMatcher.find()) {
                    iconPath = iconMatcher.group(1).trim();
                    if (!iconPath.startsWith("/")) {
                        iconPath = null;
                    }
                    if (software != null) {
                        software.setSoftwareIcon(iconPath);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 desktop 文件失败 {}: {}", file.getName(), e.getMessage());
        }
        if (software != null) {
            software.setSoftwareStatus(0);
        }
        return software;
    }

    /**
    * contains名称
    *
    * @param list 列表
    * @param name 名称
    * @return contains名称的结果
     */
    private boolean containsName(List<SoftSoftware> list, String name) {
        return list.stream().anyMatch(s -> name.equals(s.getSoftwareName()));
    }
}