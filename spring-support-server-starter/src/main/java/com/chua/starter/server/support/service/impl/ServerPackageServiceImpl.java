package com.chua.starter.server.support.service.impl;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.InstalledPackage;
import com.chua.starter.server.support.model.PackageManagerInfo;
import com.chua.starter.server.support.model.PackageUpgradeResult;
import com.chua.starter.server.support.model.UpgradablePackage;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerPackageService;
import com.chua.starter.server.support.util.ServerCommandSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerPackageServiceImpl implements ServerPackageService {

    private final ServerHostService serverHostService;
    private final ServerHostCommandExecutor serverHostCommandExecutor;

    @Override
    public PackageManagerInfo checkPackageManager(Integer serverId) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (windows) {
            try {
                String output = execute(host, "winget --version 2>&1");
                if (StringUtils.hasText(output) && !output.toLowerCase().contains("not recognized")) {
                    return PackageManagerInfo.builder()
                            .available(true).name("winget")
                            .version(output.trim())
                            .osType(host.getOsType()).build();
                }
            } catch (Exception ignored) {}
            return PackageManagerInfo.builder()
                    .available(false).message("winget 未安装，请安装应用安装程序 (App Installer)")
                    .osType(host.getOsType()).build();
        } else {
            return detectLinuxPackageManager(host);
        }
    }

    private PackageManagerInfo detectLinuxPackageManager(ServerHost host) throws Exception {
        String[] candidates = {"apt", "yum", "dnf", "zypper"};
        for (String pm : candidates) {
            try {
                String output = execute(host, pm + " --version 2>/dev/null || echo 'not_found'");
                if (StringUtils.hasText(output) && !output.contains("not_found") && !output.contains("not found")) {
                    return PackageManagerInfo.builder()
                            .available(true).name(pm)
                            .version(output.trim().split("\n")[0])
                            .osType(host.getOsType()).build();
                }
            } catch (Exception ignored) {}
        }
        return PackageManagerInfo.builder()
                .available(false).message("未检测到可用的包管理器")
                .osType(host.getOsType()).build();
    }

    @Override
    public List<UpgradablePackage> listUpgradable(Integer serverId) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (windows) {
            String output = execute(host, "winget upgrade --accept-source-agreements 2>&1");
            return parseWingetUpgradable(output);
        } else {
            return detectLinuxUpgradable(host);
        }
    }

    private List<UpgradablePackage> parseWingetUpgradable(String output) {
        List<UpgradablePackage> packages = new ArrayList<>();
        if (!StringUtils.hasText(output)) return packages;
        String[] lines = output.split("\n");
        boolean headerPassed = false;
        Pattern headerPattern = Pattern.compile("^Name\\s+Id\\s+Version\\s+Available\\s+Source");
        Pattern rowPattern = Pattern.compile("^([^│]+?)\\s{2,}([^\\s]+)\\s{2,}([^\\s]+)\\s{2,}([^\\s]+)\\s{2,}(\\S+)");
        Pattern simplePattern = Pattern.compile("^\\S.*\\S\\s{2,}\\S+\\s{2,}\\S+\\s{2,}\\S+\\s{2,}\\S+");

        for (String line : lines) {
            String trimmed = line.trim();
            if (!headerPassed) {
                if (headerPattern.matcher(trimmed).find()) headerPassed = true;
                continue;
            }
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("-")) continue;

            // Split by 2+ spaces
            String[] parts = trimmed.split("\\s{2,}");
            if (parts.length >= 5) {
                String name = parts[0].trim();
                String installed = parts[2].trim();
                String latest = parts[3].trim();
                String source = parts[4].trim();
                if (!name.isEmpty() && !latest.isEmpty() && !"Unknown".equalsIgnoreCase(latest)) {
                    packages.add(UpgradablePackage.builder()
                            .name(name).installedVersion(installed)
                            .latestVersion(latest).source(source).build());
                }
            }
        }
        return packages;
    }

    private List<UpgradablePackage> detectLinuxUpgradable(ServerHost host) throws Exception {
        List<UpgradablePackage> packages = new ArrayList<>();
        String output;

        // Try apt first
        try {
            output = execute(host, "apt list --upgradable 2>/dev/null");
            if (StringUtils.hasText(output) && !output.contains("Listing...")) {
                for (String line : output.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.contains("/") || trimmed.startsWith("Listing")) continue;
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length >= 2) {
                        String name = parts[0].contains("/") ? parts[0].substring(0, parts[0].indexOf('/')) : parts[0];
                        String version = parts[1];
                        String source = parts.length >= 3 ? parts[parts.length - 1].replaceAll("[()]", "") : "apt";
                        String installedVersion = extractVersion(version, false);
                        String latestVersion = extractVersion(version, true);
                        packages.add(UpgradablePackage.builder()
                                .name(name).installedVersion(installedVersion)
                                .latestVersion(latestVersion).source(source).build());
                    }
                }
                return packages;
            }
        } catch (Exception ignored) {}

        // Fallback to yum/dnf
        try {
            output = execute(host, "yum list updates 2>/dev/null || dnf list updates 2>/dev/null");
            if (StringUtils.hasText(output)) {
                for (String line : output.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.contains(".x86_64") || trimmed.contains(".noarch")) {
                        String[] parts = trimmed.split("\\s+");
                        if (parts.length >= 2) {
                            String name = parts[0];
                            String version = parts[1];
                            packages.add(UpgradablePackage.builder()
                                    .name(name).latestVersion(version).source("yum/dnf").build());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return packages;
    }

    private String extractVersion(String versionStr, boolean latest) {
        if (versionStr == null) return "";
        if (versionStr.contains("/")) {
            String[] parts = versionStr.split("/");
            return parts[0].trim();
        }
        if (versionStr.contains("=>")) {
            String[] parts = versionStr.split("=>");
            return latest ? parts[parts.length - 1].trim() : parts[0].trim();
        }
        return versionStr;
    }

    @Override
    public PackageUpgradeResult upgrade(Integer serverId, String packageName) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        try {
            if (windows) {
                execute(host, String.format("winget upgrade --accept-source-agreements --accept-package-agreements \"%s\" 2>&1", packageName));
            } else {
                execute(host, String.format("apt-get install -y %s 2>&1 || yum install -y %s 2>&1 || dnf install -y %s 2>&1",
                        packageName, packageName, packageName));
            }
            return PackageUpgradeResult.builder().name(packageName).success(true).message("升级成功").build();
        } catch (Exception e) {
            return PackageUpgradeResult.builder()
                    .name(packageName).success(false)
                    .message(e.getMessage() != null ? e.getMessage() : "升级失败").build();
        }
    }

    @Override
    public List<PackageUpgradeResult> upgradeAll(Integer serverId) throws Exception {
        List<UpgradablePackage> packages = listUpgradable(serverId);
        List<PackageUpgradeResult> results = new ArrayList<>();
        for (UpgradablePackage pkg : packages) {
            results.add(upgrade(serverId, pkg.getName()));
        }
        return results;
    }

    @Override
    public List<InstalledPackage> listInstalled(Integer serverId, String keyword) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (windows) {
            String cmd = "winget list --accept-source-agreements 2>&1";
            String output = execute(host, cmd);
            return parseWingetInstalled(output, keyword);
        } else {
            return detectLinuxInstalled(host, keyword);
        }
    }

    private List<InstalledPackage> parseWingetInstalled(String output, String keyword) {
        List<InstalledPackage> packages = new ArrayList<>();
        if (!StringUtils.hasText(output)) return packages;
        String[] lines = output.split("\n");
        boolean headerPassed = false;
        Pattern headerPattern = Pattern.compile("^Name\\s+Id\\s+Version\\s+Source");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!headerPassed) {
                if (headerPattern.matcher(trimmed).find()) headerPassed = true;
                continue;
            }
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("-")) continue;
            String[] parts = trimmed.split("\\s{2,}");
            if (parts.length >= 3) {
                String name = parts[0].trim();
                String version = parts[2].trim();
                String source = parts.length >= 4 ? parts[3].trim() : "";
                if (StringUtils.hasText(keyword) && !name.toLowerCase().contains(keyword.toLowerCase())) continue;
                packages.add(InstalledPackage.builder()
                        .name(name).version(version).source(source).build());
            }
        }
        return packages;
    }

    private List<InstalledPackage> detectLinuxInstalled(ServerHost host, String keyword) throws Exception {
        List<InstalledPackage> packages = new ArrayList<>();
        String output;
        try {
            String grepFilter = StringUtils.hasText(keyword) ? " | grep -i " + keyword : "";
            output = execute(host, "dpkg -l" + grepFilter + " 2>/dev/null || rpm -qa" + grepFilter + " 2>/dev/null || echo ''");
            if (output.contains("||") && !output.contains("dpkg")) {
                // rpm output
                for (String line : output.split("\n")) {
                    String trimmed = line.trim();
                    if (StringUtils.hasText(trimmed)) {
                        String[] parts = trimmed.split("-");
                        String name = parts.length > 0 ? parts[0] : trimmed;
                        packages.add(InstalledPackage.builder()
                                .name(trimmed).version("").source("rpm").build());
                    }
                }
            } else {
                // dpkg output
                for (String line : output.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("ii ") || trimmed.startsWith("hi ")) {
                        String[] parts = trimmed.split("\\s+");
                        if (parts.length >= 3) {
                            String name = parts[1];
                            String version = parts[2];
                            if (StringUtils.hasText(keyword) && !name.toLowerCase().contains(keyword.toLowerCase())) continue;
                            packages.add(InstalledPackage.builder()
                                    .name(name).version(version).source("dpkg").build());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return packages;
    }

    @Override
    public List<InstalledPackage> search(Integer serverId, String keyword) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }

        if (windows) {
            String output = execute(host, "winget search --accept-source-agreements \"" + keyword + "\" 2>&1");
            return parseWingetSearchResult(output);
        } else {
            return detectLinuxSearch(host, keyword);
        }
    }

    private List<InstalledPackage> parseWingetSearchResult(String output) {
        List<InstalledPackage> packages = new ArrayList<>();
        if (!StringUtils.hasText(output)) return packages;
        String[] lines = output.split("\n");
        boolean headerPassed = false;
        Pattern headerPattern = Pattern.compile("^Name\\s+Id\\s+Version\\s+Source");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!headerPassed) {
                if (headerPattern.matcher(trimmed).find()) headerPassed = true;
                continue;
            }
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("-")) continue;
            String[] parts = trimmed.split("\\s{2,}");
            if (parts.length >= 3) {
                String name = parts[0].trim();
                String version = parts[2].trim();
                String source = parts.length >= 4 ? parts[3].trim() : "";
                if ("Unknown".equalsIgnoreCase(version)) version = "";
                packages.add(InstalledPackage.builder()
                        .name(name).version(version).source(source).build());
            }
        }
        return packages;
    }

    private List<InstalledPackage> detectLinuxSearch(ServerHost host, String keyword) throws Exception {
        List<InstalledPackage> packages = new ArrayList<>();

        // apt-cache search
        try {
            String output = execute(host, "apt-cache search " + keyword + " 2>/dev/null || yum search " + keyword + " 2>/dev/null || dnf search " + keyword + " 2>/dev/null");
            if (StringUtils.hasText(output)) {
                for (String line : output.split("\n")) {
                    String trimmed = line.trim();
                    if (!StringUtils.hasText(trimmed) || trimmed.startsWith("=") || trimmed.contains("Matched:")) continue;
                    if (trimmed.contains(" - ")) {
                        String[] parts = trimmed.split(" - ", 2);
                        String name = parts[0].trim();
                        String description = parts.length > 1 ? parts[1].trim() : "";
                        packages.add(InstalledPackage.builder()
                                .name(name).description(description).source("apt/yum").build());
                    } else if (trimmed.contains(":")) {
                        // yum format: package.x86_64 : description
                        String[] parts = trimmed.split(":", 2);
                        String name = parts[0].trim();
                        String description = parts.length > 1 ? parts[1].trim() : "";
                        packages.add(InstalledPackage.builder()
                                .name(name).description(description).source("yum/dnf").build());
                    }
                }
            }
        } catch (Exception ignored) {}
        return packages;
    }

    @Override
    public PackageUpgradeResult install(Integer serverId, String packageName) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        try {
            if (windows) {
                execute(host, String.format("winget install --silent --accept-source-agreements --accept-package-agreements \"%s\" 2>&1", packageName));
            } else {
                execute(host, String.format("apt-get install -y %s 2>&1 || yum install -y %s 2>&1 || dnf install -y %s 2>&1",
                        packageName, packageName, packageName));
            }
            return PackageUpgradeResult.builder().name(packageName).success(true).message("安装成功").build();
        } catch (Exception e) {
            return PackageUpgradeResult.builder()
                    .name(packageName).success(false)
                    .message(e.getMessage() != null ? e.getMessage() : "安装失败").build();
        }
    }

    private ServerHost requireHost(Integer serverId) {
        ServerHost host = serverHostService.getHost(serverId);
        if (host == null) throw new IllegalArgumentException("未找到服务器");
        return host;
    }

    private boolean isWindows(ServerHost host) {
        String osType = host == null || !StringUtils.hasText(host.getOsType())
                ? System.getProperty("os.name") : host.getOsType();
        return ServerCommandSupport.isWindows(osType);
    }

    private String execute(ServerHost host, String command) throws Exception {
        ServerCommandSupport.CommandResult result = serverHostCommandExecutor.execute(host, command);
        return result != null ? result.output() : "";
    }
}
