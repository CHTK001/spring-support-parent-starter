package com.chua.starter.server.support.service.impl;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.OsConfigApplyResult;
import com.chua.starter.server.support.model.ServerOsConfigItem;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerOsConfigService;
import com.chua.starter.server.support.util.ServerCommandSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * OS 配置服务实现。
 *
 * <p>所有配置项的读取与应用均通过 shell 命令在目标主机执行，
 * 优先使用 Linux 原生工具（ulimit / sysctl / getenforce 等），
 * Windows 主机不支持时返回 supported=false。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerOsConfigServiceImpl implements ServerOsConfigService {

    private final ServerHostService serverHostService;
    private final ServerHostCommandExecutor serverHostCommandExecutor;

    /**
     * 定义所有 OS 配置项的元信息。
     */
    private static List<OsConfigDef> defineConfigs() {
        List<OsConfigDef> list = new ArrayList<>();

        list.add(builder()
                .key("ulimit_open_files")
                .name("最大打开文件数 (ulimit -n)")
                .category("RESOURCE")
                .description("单个进程可打开的最大文件描述符数量")
                .readCommand("sh -lc \"ulimit -n\"")
                .applyCommandTemplate("sh -lc \"ulimit -n %s\" && echo '%s' > /tmp/ulimit_test")
                .defaultValue("1024")
                .possibleValues(List.of("1024", "4096", "65536", "1048576"))
                .tooltip("适用场景：高并发服务器（Nginx、Tomcat、Redis、数据库）报错 \"too many open files\" 或 \"EMFILE\"。\n"
                        + "解决的问题：Linux 默认单进程 1024 个文件描述符，高并发服务很快耗尽。\n"
                        + "注意事项：ulimit -n 仅对当前 shell 生效，持久化需修改 /etc/security/limits.conf。")
                .build());

        list.add(builder()
                .key("selinux_mode")
                .name("SELinux 模式")
                .category("SECURITY")
                .description("SELinux 强制访问控制模式")
                .readCommand("getenforce 2>/dev/null || echo 'Disabled'")
                .applyCommandTemplate("setenforce %s 2>&1 || echo 'FAIL'")
                .defaultValue("Enforcing")
                .possibleValues(List.of("Enforcing", "Permissive", "Disabled"))
                .tooltip("适用场景：应用被 SELinux 拒绝访问（avc denial），或安全合规要求开启 SELinux。\n"
                        + "解决的问题：SELinux 强制模式阻止了进程对文件/端口/上下文的非授权访问。\n"
                        + "注意事项：setenforce 0 = Permissive（仅记录不阻止），setenforce 1 = Enforcing（强制）。"
                        + "\"Disabled\" 需修改 /etc/selinux/config 并重启。")
                .build());

        list.add(builder()
                .key("vm_swappiness")
                .name("Swap 倾向性 (vm.swappiness)")
                .category("KERNEL")
                .description("控制系统使用 swap 的积极程度，值越小越优先使用物理内存")
                .readCommand("sysctl -n vm.swappiness 2>/dev/null || echo '0'")
                .applyCommandTemplate("sysctl -w vm.swappiness=%s 2>&1")
                .defaultValue("60")
                .possibleValues(List.of("0", "1", "10", "30", "60", "100"))
                .tooltip("适用场景：服务器大量使用 swap 导致性能下降，或 Redis/Elasticsearch 等内存型应用建议关闭 swap。\n"
                        + "解决的问题：降低 swappiness 可减少不必要的 swap 换入换出，提升响应速度。\n"
                        + "注意事项：0 = 除非内存完全耗尽否则不用 swap，100 = 激进使用 swap。"
                        + "生产数据库通常设为 1-10。")
                .build());

        list.add(builder()
                .key("fs_file_max")
                .name("系统级最大文件数 (fs.file-max)")
                .category("RESOURCE")
                .description("整个系统可打开的最大文件描述符总数")
                .readCommand("sysctl -n fs.file-max 2>/dev/null || echo '0'")
                .applyCommandTemplate("sysctl -w fs.file-max=%s 2>&1")
                .defaultValue("")
                .possibleValues(List.of("65536", "262144", "1048576", "4194304"))
                .tooltip("适用场景：系统级 \"file-max\" 耗尽，所有进程都无法打开新文件。\n"
                        + "解决的问题：fs.file-max 限制了整个系统 FD 上限，ulimit 超出仍会失败。\n"
                        + "注意事项：建议设为 1048576 以上，持久化需写入 /etc/sysctl.conf。")
                .build());

        list.add(builder()
                .key("kernel_pid_max")
                .name("最大进程 PID (kernel.pid_max)")
                .category("KERNEL")
                .description("系统可分配的最大进程/线程 ID 数量")
                .readCommand("sysctl -n kernel.pid_max 2>/dev/null || echo '32768'")
                .applyCommandTemplate("sysctl -w kernel.pid_max=%s 2>&1")
                .defaultValue("32768")
                .possibleValues(List.of("65536", "131072", "262144", "1048576"))
                .tooltip("适用场景：无法 fork 新进程/线程，报错 \"Cannot allocate memory\" 但内存充足。\n"
                        + "解决的问题：PID 耗尽时操作系统无法创建新进程。\n"
                        + "注意事项：pid_max 最大值受系统架构限制（64位系统可达 2^22）。")
                .build());

        list.add(builder()
                .key("net_somaxconn")
                .name("TCP 监听队列 (net.core.somaxconn)")
                .category("NETWORK")
                .description("TCP 连接 listen 队列的最大长度")
                .readCommand("sysctl -n net.core.somaxconn 2>/dev/null || echo '128'")
                .applyCommandTemplate("sysctl -w net.core.somaxconn=%s 2>&1")
                .defaultValue("128")
                .possibleValues(List.of("128", "512", "1024", "4096", "8192"))
                .tooltip("适用场景：高并发 TCP 服务（Nginx、Tomcat、Netty）在高负载下出现连接拒绝或超时。\n"
                        + "解决的问题：默认 128 的 backlog 在高并发时被填满，新连接被内核拒绝。\n"
                        + "注意事项：需同步调整应用的 backlog 参数（如 nginx listen backlog=1024）。")
                .build());

        list.add(builder()
                .key("vm_max_map_count")
                .name("内存映射区上限 (vm.max_map_count)")
                .category("KERNEL")
                .description("进程可拥有的最大内存映射区域数量")
                .readCommand("sysctl -n vm.max_map_count 2>/dev/null || echo '65530'")
                .applyCommandTemplate("sysctl -w vm.max_map_count=%s 2>&1")
                .defaultValue("65530")
                .possibleValues(List.of("65530", "262144", "524288", "1048576"))
                .tooltip("适用场景：Elasticsearch 报错 \"max virtual memory areas vm.max_map_count is too low\"，"
                        + "或使用 mmap 的应用启动失败。\n"
                        + "解决的问题：Linux 限制进程 mmap 数量，Elasticsearch 等大量使用 mmap 的应用需要更高上限。\n"
                        + "注意事项：ES 官方建议设为 262144 或更高。")
                .build());

        list.add(builder()
                .key("thp_enabled")
                .name("透明大页 (Transparent HugePages)")
                .category("KERNEL")
                .description("内核透明大页（THP）开关，影响内存管理效率")
                .readCommand("cat /sys/kernel/mm/transparent_hugepage/enabled 2>/dev/null "
                        + "|| cat /sys/kernel/mm/transparent_hugepage/enabled 2>/dev/null "
                        + "|| echo '[never]'")
                .applyCommandTemplate("echo '%s' > /sys/kernel/mm/transparent_hugepage/enabled 2>&1 || echo 'FAIL'")
                .defaultValue("always")
                .possibleValues(List.of("always", "madvise", "never"))
                .tooltip("适用场景：数据库（MongoDB、MySQL、PostgreSQL）性能不稳定，出现内存分配延迟尖刺。\n"
                        + "解决的问题：THP 在数据库场景下导致内存碎片和分配延迟，多数数据库官方建议禁用。\n"
                        + "注意事项：设为 never 禁用 THP，madvise 仅对指定 MADV_HUGEPAGE 区域启用。"
                        + "重启后需重新设置或写入 rc.local。")
                .build());

        list.add(builder()
                .key("tcp_tw_reuse")
                .name("TIME_WAIT 复用 (net.ipv4.tcp_tw_reuse)")
                .category("NETWORK")
                .description("允许将 TIME_WAIT 状态的 socket 用于新连接")
                .readCommand("sysctl -n net.ipv4.tcp_tw_reuse 2>/dev/null || echo '0'")
                .applyCommandTemplate("sysctl -w net.ipv4.tcp_tw_reuse=%s 2>&1")
                .defaultValue("0")
                .possibleValues(List.of("0", "1"))
                .tooltip("适用场景：短连接高并发服务出现大量 TIME_WAIT 占用端口/连接资源。\n"
                        + "解决的问题：客户端主动关闭后端口被 TIME_WAIT 占用，tcp_tw_reuse=1 允许重用。\n"
                        + "注意事项：仅对客户端（主动发起连接方）有效，服务端需用 tcp_tw_recycle（内核 4.12+ 已移除）。")
                .build());

        list.add(builder()
                .key("tcp_max_tw_buckets")
                .name("TIME_WAIT 上限 (net.ipv4.tcp_max_tw_buckets)")
                .category("NETWORK")
                .description("系统最大 TIME_WAIT socket 数量，超限后丢弃")
                .readCommand("sysctl -n net.ipv4.tcp_max_tw_buckets 2>/dev/null || echo '65536'")
                .applyCommandTemplate("sysctl -w net.ipv4.tcp_max_tw_buckets=%s 2>&1")
                .defaultValue("65536")
                .possibleValues(List.of("65536", "262144", "1048576"))
                .tooltip("适用场景：TIME_WAIT 连接过多导致 dmesg 报 \"TCP: time wait bucket table overflow\"。\n"
                        + "解决的问题：限制 TIME_WAIT 总数，超限后内核会丢弃旧的 TIME_WAIT 连接。\n"
                        + "注意事项：增大此值可减少连接被丢弃的概率，但会占用更多内核内存。")
                .build());

        return list;
    }

    @Override
    public List<ServerOsConfigItem> listConfigs(Integer serverId) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        List<OsConfigDef> defs = defineConfigs();
        List<ServerOsConfigItem> result = new ArrayList<>();

        for (OsConfigDef def : defs) {
            ServerOsConfigItem item = toItem(def);
            if (windows) {
                // Windows 下大多数 OS 配置不支持
                item.setSupported(false);
                item.setCurrentValue("N/A");
                item.setTooltip(item.getTooltip() + "\n\n⚠️ 当前服务器为 Windows，此项配置不适用。");
            } else {
                try {
                    String value = readValue(host, def);
                    item.setCurrentValue(value);
                } catch (Exception e) {
                    item.setCurrentValue("读取失败");
                    log.warn("读取 OS 配置失败 key={}, msg={}", def.key, e.getMessage());
                }
            }
            result.add(item);
        }
        return result;
    }

    @Override
    public ServerOsConfigItem getConfig(Integer serverId, String key) throws Exception {
        ServerHost host = requireHost(serverId);

        OsConfigDef def = findDef(key);
        if (def == null) {
            throw new IllegalArgumentException("未知配置项: " + key);
        }

        ServerOsConfigItem item = toItem(def);
        if (isWindows(host)) {
            item.setSupported(false);
            item.setCurrentValue("N/A");
        } else {
            item.setCurrentValue(readValue(host, def));
        }
        return item;
    }

    @Override
    public OsConfigApplyResult applyConfig(Integer serverId, String key, String value) throws Exception {
        ServerHost host = requireHost(serverId);

        if (isWindows(host)) {
            return OsConfigApplyResult.builder()
                    .key(key).name(key).success(false)
                    .message("Windows 暂不支持此项配置").build();
        }

        OsConfigDef def = findDef(key);
        if (def == null) {
            return OsConfigApplyResult.builder()
                    .key(key).success(false)
                    .message("未知配置项: " + key).build();
        }

        String command = String.format(def.applyCommandTemplate, value);
        try {
            String output = execute(host, command);
            if (StringUtils.hasText(output) && output.contains("FAIL")) {
                return OsConfigApplyResult.builder()
                        .key(key).name(def.name).success(false)
                        .message("应用失败: " + output).build();
            }
            // 回读验证新值
            String newValue = readValue(host, def);
            return OsConfigApplyResult.builder()
                    .key(key).name(def.name).success(true)
                    .message("配置已应用").newValue(newValue).build();
        } catch (Exception e) {
            return OsConfigApplyResult.builder()
                    .key(key).name(def.name).success(false)
                    .message(e.getMessage() != null ? e.getMessage() : "应用失败").build();
        }
    }

    private String readValue(ServerHost host, OsConfigDef def) throws Exception {
        String output = execute(host, def.readCommand);
        if (!StringUtils.hasText(output)) {
            return "N/A";
        }
        String trimmed = output.trim();
        // 处理 THP enabled 文件格式：[always] madvise never → always
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            int start = trimmed.indexOf('[');
            int end = trimmed.indexOf(']');
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end);
            }
        }
        return trimmed;
    }

    private OsConfigDef findDef(String key) {
        for (OsConfigDef def : defineConfigs()) {
            if (def.key.equals(key)) {
                return def;
            }
        }
        return null;
    }

    private ServerOsConfigItem toItem(OsConfigDef def) {
        return ServerOsConfigItem.builder()
                .key(def.key)
                .name(def.name)
                .category(def.category)
                .description(def.description)
                .currentValue("")
                .defaultValue(def.defaultValue)
                .possibleValues(def.possibleValues)
                .applyCommand(def.readCommand)
                .tooltip(def.tooltip)
                .supported(true)
                .build();
    }

    // ── helper methods ─────────────────────────────────────────

    private ServerHost requireHost(Integer serverId) {
        ServerHost host = serverHostService.getHost(serverId);
        if (host == null) {
            throw new IllegalArgumentException("未找到服务器");
        }
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

    /**
     * OS 配置项内部定义（含读取/应用命令）。
     */
    private static OsConfigDefBuilder builder() {
        return new OsConfigDefBuilder();
    }

    private static class OsConfigDefBuilder {
        private String key;
        private String name;
        private String category;
        private String description;
        private String readCommand;
        private String applyCommandTemplate;
        private String defaultValue;
        private List<String> possibleValues;
        private String tooltip;

        OsConfigDefBuilder key(String key) { this.key = key; return this; }
        OsConfigDefBuilder name(String name) { this.name = name; return this; }
        OsConfigDefBuilder category(String category) { this.category = category; return this; }
        OsConfigDefBuilder description(String description) { this.description = description; return this; }
        OsConfigDefBuilder readCommand(String readCommand) { this.readCommand = readCommand; return this; }
        OsConfigDefBuilder applyCommandTemplate(String applyCommandTemplate) { this.applyCommandTemplate = applyCommandTemplate; return this; }
        OsConfigDefBuilder defaultValue(String defaultValue) { this.defaultValue = defaultValue; return this; }
        OsConfigDefBuilder possibleValues(List<String> possibleValues) { this.possibleValues = possibleValues; return this; }
        OsConfigDefBuilder tooltip(String tooltip) { this.tooltip = tooltip; return this; }

        OsConfigDef build() {
            return new OsConfigDef(key, name, category, description, readCommand, applyCommandTemplate,
                    defaultValue, possibleValues != null ? possibleValues : List.of(), tooltip);
        }
    }

    private record OsConfigDef(
            String key,
            String name,
            String category,
            String description,
            String readCommand,
            String applyCommandTemplate,
            String defaultValue,
            List<String> possibleValues,
            String tooltip
    ) {}
}
