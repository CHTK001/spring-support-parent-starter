package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.model.DiskPartitionRowVO;
import com.chua.starter.monitor.support.model.DriverRowVO;
import com.chua.starter.monitor.support.model.FirewallRuleRowVO;
import com.chua.starter.monitor.support.model.ProcessThreadRowVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Windows PowerShell {@code ConvertTo-Json} 输出 → 行视图。
 *
 * <p>与 {@link WindowsProfileScripts} 一样按通道共用：本机/Agent 走 cmd 承载的 powershell.exe、
 * SSH/WinRM 走 PowerShell 管道，回传的都是同一份 JSON，解析必须同源。</p>
 *
 * <p>PowerShell 只有一条记录时 {@code ConvertTo-Json} 不带方括号，单对象也要按一行处理，
 * 否则「这台机器只有一条规则」会解析成空列表。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
final class WindowsProfileRows {

    /**
     * JSON 解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 一 GiB 的字节数
     */
    private static final double GIB_BYTES = 1024.0 * 1024.0 * 1024.0;

    private WindowsProfileRows() {
    }

    /**
     * {@code Get-NetFirewallRule} → 规则视图。
     *
     * @param json 命令输出
     * @return 规则列表，解析失败为空列表
     */
    static List<FirewallRuleRowVO> firewallRules(String json) {
        return rows(json, node -> {
            FirewallRuleRowVO vo = new FirewallRuleRowVO();
            vo.setName(text(node, "DisplayName"));
            vo.setChain(vo.getName());
            vo.setDirection(text(node, "Direction"));
            vo.setAction(text(node, "Action"));
            vo.setEnabled(text(node, "Enabled"));
            vo.setProfile(text(node, "Profile"));
            return vo;
        });
    }

    /**
     * {@code Get-WindowsDriver} → 驱动视图。
     *
     * @param json 命令输出
     * @return 驱动列表，解析失败为空列表
     */
    static List<DriverRowVO> drivers(String json) {
        return rows(json, node -> {
            DriverRowVO vo = new DriverRowVO();
            vo.setName(text(node, "Driver"));
            vo.setFileName(text(node, "OriginalFileName"));
            vo.setProvider(text(node, "ProviderName"));
            vo.setVersion(text(node, "Version"));
            vo.setDate(text(node, "Date"));
            vo.setClassName(text(node, "ClassName"));
            vo.setBootCritical("true".equalsIgnoreCase(text(node, "BootCritical")));
            return vo;
        });
    }

    /**
     * PowerShell {@code ThreadObject} → 线程视图。
     *
     * <p>字段取自 {@link WindowsProfileScripts#threads(int)} 的投影结果；Windows 没有线程名，
     * {@code name} 整列留空，Linux 侧才给 {@code comm}。</p>
     *
     * @param json 命令输出
     * @return 线程列表，解析失败为空列表
     */
    static List<ProcessThreadRowVO> threads(String json) {
        return rows(json, node -> {
            ProcessThreadRowVO vo = new ProcessThreadRowVO();
            vo.setTid(number(node, "Id"));
            vo.setState(text(node, "ThreadState"));
            vo.setWaitReason(text(node, "WaitReason"));
            vo.setPriority(text(node, "Priority"));
            vo.setCpu(decimal(node, "Cpu"));
            vo.setCpuTime(clockText(number(node, "CpuMs")));
            return vo;
        });
    }

    /**
     * {@code Get-Volume} → 分区视图（容量统一为 GiB 文本）。
     *
     * @param json 命令输出
     * @return 分区列表，解析失败为空列表
     */
    static List<DiskPartitionRowVO> partitions(String json) {
        return rows(json, node -> {
            long size = number(node, "Size");
            long remaining = number(node, "SizeRemaining");
            String letter = text(node, "DriveLetter");
            String label = text(node, "FileSystemLabel");
            DiskPartitionRowVO vo = new DiskPartitionRowVO();
            vo.setFilesystem(letter.isEmpty() ? label : letter + ":\\");
            vo.setType(text(node, "FileSystem"));
            vo.setSize(gibText(size));
            vo.setUsed(gibText(size - remaining));
            vo.setAvailable(gibText(remaining));
            vo.setUsePercent(size <= 0 ? "" : Math.round((size - remaining) * 100.0 / size) + "%");
            vo.setMountPoint(label);
            return vo;
        });
    }

    /**
     * 把 JSON 解析成结构化行。
     *
     * @param json   JSON 文本
     * @param mapper 节点 → 行视图
     * @param <R>    行类型
     * @return 行列表，解析失败为空列表
     */
    private static <R> List<R> rows(String json, Function<JsonNode, R> mapper) {
        List<R> rows = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return rows;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    rows.add(mapper.apply(node));
                }
            } else if (root.isObject()) {
                rows.add(mapper.apply(root));
            }
        } catch (Exception e) {
            log.warn("解析 PowerShell JSON 失败: {}", e.getMessage());
        }
        return rows;
    }

    /**
     * 取文本字段；PowerShell 把枚举序列化成对象时取其 DisplayValue。
     *
     * @param node  JSON 节点
     * @param field 字段名
     * @return 文本，取不到返回空串
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isObject()) {
            JsonNode display = value.get("DisplayValue");
            return display == null ? value.toString() : display.asText();
        }
        return value.asText();
    }

    /**
     * 取数值字段。
     *
     * @param node  JSON 节点
     * @param field 字段名
     * @return 数值，取不到返回 0
     */
    private static long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? 0L : value.asLong();
    }

    /**
     * 取小数字段（线程 CPU 占用是采样出来的百分比）。
     *
     * @param node  JSON 节点
     * @param field 字段名
     * @return 数值，取不到返回 0
     */
    private static double decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? 0D : value.asDouble();
    }

    /**
     * 毫秒数转 {@code 时:分:秒.毫秒} 展示文本。
     *
     * @param millis 毫秒数
     * @return 形如 {@code 0:12:34.560}
     */
    private static String clockText(long millis) {
        long ms = Math.max(0L, millis);
        return String.format(Locale.ROOT, "%d:%02d:%02d.%03d",
                ms / 3_600_000L, (ms / 60_000L) % 60L, (ms / 1_000L) % 60L, ms % 1_000L);
    }

    /**
     * 字节数转 GiB 展示文本。
     *
     * @param bytes 字节数
     * @return 形如 {@code 12.3G}
     */
    private static String gibText(long bytes) {
        return String.format(Locale.ROOT, "%.1fG", bytes / GIB_BYTES);
    }
}
