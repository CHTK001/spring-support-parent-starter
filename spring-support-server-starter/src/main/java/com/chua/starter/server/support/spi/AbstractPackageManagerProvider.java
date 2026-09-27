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
        SoftwarePackage pkg = new SoftwarePackage();
        pkg.setName(name.trim());
        if (version != null && !version.isBlank()) {
            pkg.setVersion(version.trim());
        }
        if (summary != null && !summary.isBlank()) {
            pkg.setSummary(summary.trim());
        }
        list.add(pkg);
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
}
