package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.SoftwarePackage;

import java.util.ArrayList;
import java.util.List;

/**
 * 包管理器 Provider 抽象基类：沉淀通用的字符串工具与表格输出解析，避免各实现重复造轮子。
 *
 * @author CH
 * @since 4.0.0.42
 */
public abstract class AbstractPackageManagerProvider implements PackageManagerProvider {

    /**
     * 小写化（null 安全）
     */
    protected static String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    /**
     * 判断字符串是否包含任意一个关键词
     */
    protected static boolean containsAny(String value, String... keys) {
        if (value == null) {
            return false;
        }
        for (String key : keys) {
            if (value.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为 Linux 主机
     */
    protected static boolean isLinux(com.chua.starter.server.support.entity.MsServerHost host) {
        return "LINUX".equalsIgnoreCase(host.getServerHostOsType());
    }

    /**
     * 构造一个软件包并加入列表
     */
    protected static void add(List<SoftwarePackage> list, String name, String version, String summary) {
        if (name == null || name.isBlank()) {
            return;
        }
        list.add(new SoftwarePackage(
                name.trim(),
                summary != null && !summary.isBlank() ? summary.trim() : null,
                version != null && !version.isBlank() ? version.trim() : null));
    }

    /**
     * 通用列对齐表格解析（2+ 空格分列），供 winget 等使用。
     *
     * @param output   命令输出
     * @param nameCol  包名所在列索引（0 起）
     * @param cols     其余列依次映射 version/summary 的索引（-1 表示该列不存在）
     * @param skipHead 需要跳过的表头关键词
     * @return 软件包列表
     */
    protected static List<SoftwarePackage> parseTable(String output, int nameCol,
                                                      int versionCol, int summaryCol,
                                                      String... skipHead) {        List<SoftwarePackage> list = new ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("-") || line.startsWith("=")) {
                continue;
            }
            String low = line.toLowerCase();
            boolean head = false;
            for (String h : skipHead) {
                if (low.contains(h)) {
                    head = true;
                    break;
                }
            }
            if (head) {
                continue;
            }
            String[] parts = line.split("\\s{2,}");
            int max = Math.max(nameCol, Math.max(versionCol, summaryCol));
            if (parts.length <= max) {
                continue;
            }
            String name = parts[nameCol];
            String version = versionCol >= 0 ? parts[versionCol] : null;
            String summary = summaryCol >= 0 ? parts[summaryCol] : null;
            add(list, name, version, summary);
        }
        return list;
    }

    /**
     * RHEL 系（yum / dnf）search 输出解析。
     * <p>
     * 典型格式 {@code name.arch : summary}，部分包为 {@code name.arch : version : summary}；
     * 需跳过 {@code ==== matched: kw ====} 小节标题与元数据/告警行。
     * </p>
     *
     * @param output 命令输出
     * @return 软件包列表
     */
    protected static List<SoftwarePackage> parseRhelSearch(String output) {
        List<SoftwarePackage> list = new ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("=") || line.startsWith("-")) {
                continue;
            }
            String low = line.toLowerCase();
            if (containsAny(low, "matched:", "metadata", "warning", "loading", "no matches", "error:")) {
                continue;
            }
            if (!line.contains(" : ")) {
                continue;
            }
            String[] parts = line.split("\\s+:\\s+");
            if (parts.length < 2) {
                continue;
            }
            // 去掉架构后缀，如 nginx.x86_64 / nginx-all-modules.noarch
            String name = parts[0].trim().replaceAll("\\.[a-z0-9_]+$", "");
            String version = parts.length >= 3 ? parts[1].trim() : null;
            String summary = parts[parts.length - 1].trim();
            add(list, name, version, summary);
        }
        return list;
    }

    /**
     * RHEL 系（yum / dnf）已安装列表解析（{@code yum list installed} / {@code dnf list installed}）。
     * <p>
     * 输出形如 {@code name.arch  version  repo}（含表头 {@code Installed Packages}）。
     * 每行提取包名（去架构后缀）与版本列。
     * </p>
     *
     * @param output 命令输出
     * @return 软件包列表
     */
    protected static List<SoftwarePackage> parseRhelInstalled(String output) {
        List<SoftwarePackage> list = new ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("-") || line.startsWith("=")) {
                continue;
            }
            String low = line.toLowerCase();
            if (containsAny(low, "installed packages", "loaded plugins", "last metadata",
                    "dependencies resolved", "complete!", "nothing to do")) {
                continue;
            }
            // name.arch version repo（按 2+ 空格分列）
            String[] parts = line.split("\\s{2,}");
            if (parts.length < 2) {
                continue;
            }
            String name = parts[0].trim().replaceAll("\\.[a-z0-9_]+$", "");
            if (name.contains("@")) {
                // 形如 name@repo，取 name 部分
                name = name.substring(0, name.indexOf('@'));
            }
            String version = parts.length >= 2 ? parts[1].trim() : null;
            add(list, name, version, null);
        }
        return list;
    }

    /**
     * 通用管道分隔（name|version|…）解析，供 choco 等使用。
     *
     * @param output 命令输出
     * @return 软件包列表
     */
    protected static List<SoftwarePackage> parsePipe(String output) {
        List<SoftwarePackage> list = new ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (!line.contains("|")) {
                continue;
            }
            String[] parts = line.split("\\|");
            String name = parts[0].trim();
            String version = parts.length >= 2 ? parts[1].trim() : null;
            add(list, name, version, name);
        }
        return list;
    }

    /**
     * APT 可升级列表解析（{@code apt list --upgradable}）。
     *
     * <p>典型行：{@code nginx/jammy-updates 1.18.0-6ubuntu14.4 amd64 [upgradable from: 1.18.0-6ubuntu14.3]}。
     * 第一段 {@code 包名/仓库} 取斜杠前的包名，第二段是仓库里的候选版本。</p>
     *
     * @param output 命令输出
     * @return 可升级列表（version 为候选版本）
     */
    protected static List<SoftwarePackage> parseAptUpgradable(String output) {
        List<SoftwarePackage> list = new ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("Listing") || line.startsWith("WARNING")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 2 || !parts[0].contains("/")) {
                continue;
            }
            String name = parts[0].substring(0, parts[0].indexOf('/'));
            String candidate = parts[1].trim();
            list.add(new SoftwarePackage(name, null, candidate));
        }
        return list;
    }

    /**
     * 表格式可升级列表解析（{@code winget upgrade}）。
     *
     * <p>列序为 名称 / Id / 当前版本 / 可用版本 / 来源，列间以 2+ 空格分隔；
     * 名称本身可能带空格，因此按「倒数第二列是可用版本」取值。</p>
     *
     * @param output 命令输出
     * @return 可升级列表（name 为包 Id）
     */
    protected static List<SoftwarePackage> parseTableUpgradable(String output) {
        List<SoftwarePackage> list = new ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("-") || line.startsWith("=")) {
                continue;
            }
            String low = line.toLowerCase();
            if (low.startsWith("name") || low.contains("upgrades available")
                    || low.contains("no installed package")) {
                continue;
            }
            String[] parts = line.split("\\s{2,}");
            if (parts.length < 4) {
                continue;
            }
            String id = parts[1].trim();
            String candidate = parts[parts.length - 2].trim();
            list.add(new SoftwarePackage(id, null, candidate));
        }
        return list;
    }

    /**
     * 管道式可升级列表解析（{@code choco outdated -r}）。
     *
     * <p>输出形如 {@code name|current|available|pinned}。</p>
     *
     * @param output 命令输出
     * @return 可升级列表（version 为 available）
     */
    protected static List<SoftwarePackage> parsePipeUpgradable(String output) {
        List<SoftwarePackage> list = new ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (!line.contains("|")) {
                continue;
            }
            String[] parts = line.split("\\|");
            if (parts.length < 3) {
                continue;
            }
            list.add(new SoftwarePackage(parts[0].trim(), null, parts[2].trim()));
        }
        return list;
    }

    /**
     * 依赖行解析：保留以指定前缀开头的行，取冒号后的包名。
     *
     * <p>不同包管理器的依赖输出共性是「前缀 + 包名」：apt 的
     * {@code Depends: libc6}、choco 的 {@code - dotnet4.5}。版本约束
     * {@code (>= 1.2)} 与虚拟包尖括号 {@code <libssl3>} 都去掉，
     * 只留可读的包名，前端按行展示。</p>
     *
     * @param output   命令输出
     * @param prefixes 行前缀（如 "Depends:"、"PreDepends:"）
     * @return 去重后的依赖名列表
     */
    protected static List<String> parseDependencyLines(String output, String... prefixes) {
        List<String> list = new ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String value = null;
            for (String prefix : prefixes) {
                if (line.startsWith(prefix)) {
                    value = line.substring(prefix.length()).trim();
                    break;
                }
            }
            if (value == null && line.startsWith("- ")) {
                value = line.substring(2).trim();
            }
            if (value == null) {
                continue;
            }
            value = value.replaceAll("\\(.*?\\)", "")
                    .replace("<", "")
                    .replace(">", "")
                    .trim();
            if (value.isEmpty() || value.contains(" ")) {
                // 依赖名不含空格；带空格的多半是说明文字
                continue;
            }
            if (!list.contains(value)) {
                list.add(value);
            }
        }
        return list;
    }
}
