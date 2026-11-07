package com.chua.starter.apm.support.config;

import com.chua.starter.apm.support.ApmRegistry;
import com.chua.starter.apm.support.model.ApmDescriptor;
import com.chua.starter.apm.support.properties.ApmProperties;
import com.chua.starter.apm.support.spi.ApmProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「APM 设置」的读写入口：把 {@code plugin.apm.*} 里<b>允许热改</b>的部分做成可运行期修改，
 * 并落到 {@link ApmSettingsStore}（重启后自动套用）。
 *
 * <h3>为什么需要它</h3>
 * <p>在此之前，APM 的开关与引擎只能靠启动参数（{@code -Dplugin.apm.enabled} /
 * {@code -Dplugin.apm.type} / {@code -Dskywalking.url}）决定，页面上只能只读地看
 * {@code /status}。结果是「换个 OAP 地址」这种日常动作要改脚本 + 重启进程，
 * 而重启会打断正在看的观测台。</p>
 *
 * <h3>哪些能热改、哪些不能</h3>
 * <table border="1">
 *   <caption>可改项边界</caption>
 *   <tr><th>键</th><th>热改</th><th>原因</th></tr>
 *   <tr><td>{@code enabled}</td><td>是</td><td>只影响是否对外提供查询，注册表每次描述状态时都会读</td></tr>
 *   <tr><td>{@code type}</td><td>是</td><td>只是换一个已实例化的 Provider，重新探测即可</td></tr>
 *   <tr><td>{@code detectInterval}</td><td>是</td><td>纯缓存时长</td></tr>
 *   <tr><td>{@code oapUrl}（由 Provider 声明）</td><td>是</td><td>改完丢弃旧连接与状态缓存，下次查询自然走新地址</td></tr>
 *   <tr><td>{@code mode}/{@code path}/{@code port}/{@code serverType}</td><td><b>否</b></td>
 *       <td>决定 Servlet 与端口的注册方式，进程起来后再改不会生效</td></tr>
 * </table>
 *
 * <p>「按 Provider 声明」而不是写死 skywalking：新增一种 APM（如 OpenTelemetry）时，
 * 只要实现 {@link ApmProvider#runtimeSettings()} 与 {@link ApmProvider#applyRuntimeSettings(Map)}，
 * 设置页就会自动多出它的可改项，本类无需改动。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class ApmRuntimeConfig {

    /**
     * 不能热改、需要重启的键（仅用于界面提示，不参与写入）
     */
    private static final List<String> RESTART_KEYS =
            List.of("mode", "path", "port", "serverType", "host", "settingsFile");

    /**
     * 最小探测缓存：再小就是每次请求都探活，会把 OAP 打穿
     */
    private static final long MIN_DETECT_INTERVAL = 1_000L;

    /**
     * 运行时快照 Map 默认容量
     */
    private static final int DEFAULT_MAP_CAPACITY = 16;

    /**
     * 配置
     */
    private final ApmProperties properties;

    /**
     * 注册表（改完 type / oapUrl 要重新探测）
     */
    private final ApmRegistry registry;

    /**
     * 落盘存储
     */
    private final ApmSettingsStore store;

    /**
     * 启动参数里固定的引擎名（构造时抓取，之后界面改动不影响它）。
     *
     * <p>用于界面提示「这里选的引擎重启后会回到启动参数」：{@link #restore()} 只在
     * 启动参数没写 {@code plugin.apm.type} 时才用文件里的值，部署侧固定了引擎就该说清楚，
     * 否则用户改完保存、重启又变回去，会以为设置没存住。</p>
     */
    private final String deploymentType;

    /**
     * 构造
     *
     * @param properties 配置
     * @param registry   注册表
     * @param store      存储
     */
    public ApmRuntimeConfig(ApmProperties properties, ApmRegistry registry, ApmSettingsStore store) {
        this.properties = properties;
        this.registry = registry;
        this.store = store;
        this.deploymentType = properties.getType();
    }

    /**
     * 启动时把文件里保存的设置套回内存配置。
     *
     * <p>启动参数优先于设置文件：显式传了 {@code plugin.apm.type} 的环境（例如验收环境用
     * {@code -Dplugin.apm.type=skywalking} 固定引擎）不应该被上一次界面上点过的值悄悄改掉；
     * 这里只在「启动参数没写」时才用文件里的值补。</p>
     *
     * <p>注意 {@code enabled} 的语义：文件里存的是「上次界面上的选择」，也是界面开关唯一的持久化位置，
     * 因此默认**以文件为准**；部署侧若要强制关掉整个模块、不允许界面再打开，必须同时加
     * {@code -Dplugin.apm.enabled.locked=true}（{@link #enabledLocked()}），此时文件值不覆盖启动参数，
     * 设置页也会把开关置灰。</p>
     */
    public void restore() {
        Map<String, String> saved = store.load();
        if (saved.isEmpty()) {
            return;
        }
        String type = saved.get("type");
        if (properties.getType() == null || properties.getType().isBlank()) {
            if (type != null && !type.isBlank()) {
                properties.setType(type.trim());
            }
        } else if (type != null && !properties.getType().equalsIgnoreCase(type.trim())) {
            log.info("[APM] 启动参数固定引擎 {}，忽略设置文件里的 {}", properties.getType(), type);
        }
        String enabled = saved.get("enabled");
        if (enabled != null && !enabled.isBlank() && !enabledLocked()) {
            properties.setEnabled(Boolean.parseBoolean(enabled));
        }
        String interval = saved.get("detectInterval");
        if (interval != null) {
            try {
                long value = Long.parseLong(interval.trim());
                if (value >= MIN_DETECT_INTERVAL) {
                    properties.setDetectInterval(value);
                }
            } catch (NumberFormatException ignored) {
                log.debug("[APM] 设置文件里的 detectInterval 非法: {}", interval);
            }
        }
        String oapUrl = saved.get("oapUrl");
        if (oapUrl != null && !oapUrl.isBlank()) {
            if (oapUrlFromDeployment()) {
                log.info("[APM] 启动参数/环境变量已指定 OAP 地址，忽略设置文件里的 {}", oapUrl);
            } else {
                applyEngineSettings(Map.of("oapUrl", oapUrl));
            }
        }
        log.info("[APM] 已套用设置文件 {}（enabled={} type={} detectInterval={} oapUrl={}）",
                store.file(), properties.isEnabled(), properties.getType(),
                properties.getDetectInterval(), oapUrl);
        registry.detect();
    }

    /**
     * 部署侧是否已显式指定了 OAP 地址。
     *
     * <p>只认「部署侧」的三个来源：{@code -Dskywalking.url}、{@code -Dplugin.apm.skywalking.url}
     * 与 {@code SW_OAP_ADDRESS}。它们一旦出现，说明地址由部署脚本决定，界面里上次保存的值
     * 不应悄悄盖掉它 —— 否则运维改完脚本重启，地址还是界面上那个。</p>
     *
     * @return 是否由部署侧指定
     */
    private boolean oapUrlFromDeployment() {
        if (!isBlank(System.getProperty("skywalking.url"))) {
            return true;
        }
        if (!isBlank(System.getProperty("plugin.apm.skywalking.url"))) {
            return true;
        }
        try {
            return !isBlank(System.getenv("SW_OAP_ADDRESS"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 界面开关是否被部署侧锁定。
     *
     * <p>只认系统属性 {@code -Dplugin.apm.enabled.locked=true}：这是「必须由部署决定」的
     * 紧急开关，不从设置文件读，避免界面自己把自己锁死。锁定后 {@link #restore()} 不覆盖
     * 启动参数，设置页会把开关置灰并说明原因。</p>
     *
     * @return 是否锁定
     */
    private boolean enabledLocked() {
        return Boolean.getBoolean("plugin.apm.enabled.locked");
    }

    /**
     * 引擎是否由启动参数固定（构造时抓取，界面改动不算）。
     *
     * @return 是否由部署侧指定
     */
    private boolean typeFromDeployment() {
        return deploymentType != null && !deploymentType.isBlank();
    }

    /**
     * 空判断
     *
     * @param value 值
     * @return 是否为空
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 当前设置的全貌（界面展示 + 保存后的回显）。
     *
     * @return 视图
     */
    public Map<String, Object> view() {
        ApmDescriptor descriptor = registry.describe();
        Map<String, Object> payload = new LinkedHashMap<>(DEFAULT_MAP_CAPACITY);
        payload.put("enabled", properties.isEnabled());
        payload.put("type", properties.getType() == null ? "" : properties.getType());
        payload.put("mode", properties.getMode());
        payload.put("path", properties.getPath());
        payload.put("host", properties.getHost());
        payload.put("port", properties.getPort());
        payload.put("serverType", properties.getServerType());
        payload.put("detectInterval", properties.getDetectInterval());
        payload.put("settingsFile", store.file().toString());
        payload.put("oapUrlFromDeployment", oapUrlFromDeployment());
        payload.put("typeFromDeployment", typeFromDeployment());
        payload.put("deploymentType", deploymentType == null ? "" : deploymentType);
        payload.put("enabledLocked", enabledLocked());
        payload.put("editable", List.of("enabled", "type", "detectInterval", "oapUrl"));
        payload.put("restartRequired", RESTART_KEYS);

        // 当前选中引擎及其「自己声明的」可改项
        Map<String, Object> engine = new LinkedHashMap<>(8);
        ApmProvider selected = registry.provider().orElse(null);
        if (selected == null) {
            engine.put("name", null);
            engine.put("settings", Map.of());
        } else {
            engine.put("name", selected.name());
            engine.put("mode", selected.mode());
            engine.put("version", selected.status().version());
            engine.put("endpoint", selected.status().endpoint());
            engine.put("settings", new LinkedHashMap<>(selected.runtimeSettings()));
        }
        payload.put("engine", engine);
        payload.put("present", descriptor.present());
        payload.put("message", descriptor.message());
        payload.put("candidates", candidates());
        payload.put("saved", new LinkedHashMap<>(store.load()));
        return payload;
    }

    /**
     * 保存设置（只处理允许热改的键），并立即重新探测。
     *
     * @param params 待应用的键值；{@code action=detect} 表示只重新探测不修改
     * @return 应用后的视图
     */
    public Map<String, Object> update(Map<String, String> params) {
        Map<String, String> input = params == null ? Map.of() : params;
        String action = trim(input.get("action"));
        if ("detect".equalsIgnoreCase(action)) {
            registry.detect();
            return view();
        }

        Boolean enabled = null;
        String type = null;
        Long interval = null;
        Map<String, String> engineSettings = new LinkedHashMap<>();
        Map<String, String> persisted = new LinkedHashMap<>(store.load());

        if (input.containsKey("enabled")) {
            enabled = parseBoolean(input.get("enabled"), "enabled");
            properties.setEnabled(enabled);
            persisted.put("enabled", String.valueOf(enabled));
        }
        if (input.containsKey("type")) {
            type = trim(input.get("type"));
            validateType(type);
            properties.setType(type == null ? "" : type);
            persisted.put("type", type == null ? "" : type);
        }
        if (input.containsKey("detectInterval")) {
            interval = parseInterval(input.get("detectInterval"));
            properties.setDetectInterval(interval);
            persisted.put("detectInterval", String.valueOf(interval));
        }
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = entry.getKey();
            if ("enabled".equals(key) || "type".equals(key) || "detectInterval".equals(key)
                    || "action".equals(key)) {
                continue;
            }
            String value = trim(entry.getValue());
            if (value == null) {
                continue;
            }
            if (RESTART_KEYS.contains(key)) {
                throw new IllegalArgumentException("「" + key + "」不支持热改，改了必须重启："
                        + "它决定 Servlet/端口的注册方式，进程内改了不会生效");
            }
            engineSettings.put(key, value);
        }
        if (!engineSettings.isEmpty()) {
            applyEngineSettings(engineSettings);
            persisted.putAll(engineSettings);
        }

        boolean saved = store.save(persisted);
        registry.detect();
        log.info("[APM] 设置已更新：enabled={} type={} detectInterval={} 引擎项={} 落盘={}（{}）",
                properties.isEnabled(), properties.getType(), properties.getDetectInterval(),
                engineSettings, saved, store.file());

        Map<String, Object> payload = view();
        payload.put("persisted", saved);
        if (!saved) {
            payload.put("persistMessage", "设置已在本次运行内生效，但写入 " + store.file()
                    + " 失败（进程重启后会丢失，请检查目录权限）");
        }
        return payload;
    }

    /**
     * 把引擎级设置交给「自己声明支持它」的 Provider。
     *
     * <p>逐个候选下发而不是只发给当前选中的那个：OAP 地址是先配好、再切引擎的常见顺序，
     * 只发给选中的会导致「切过去还是旧地址」。</p>
     *
     * @param values 键值
     */
    private void applyEngineSettings(Map<String, String> values) {
        boolean applied = false;
        for (ApmProvider provider : registry.candidates()) {
            Map<String, String> supported = provider.runtimeSettings();
            if (supported.isEmpty()) {
                continue;
            }
            Map<String, String> subset = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (supported.containsKey(entry.getKey())) {
                    subset.put(entry.getKey(), entry.getValue());
                }
            }
            if (subset.isEmpty()) {
                continue;
            }
            provider.applyRuntimeSettings(subset);
            applied = true;
        }
        if (!applied) {
            List<String> known = new ArrayList<>();
            for (ApmProvider provider : registry.candidates()) {
                known.addAll(provider.runtimeSettings().keySet());
            }
            throw new IllegalArgumentException("当前引擎不支持这些设置项: " + values.keySet()
                    + (known.isEmpty() ? "（可用引擎没有可改项）" : "（可改项: " + known + "）"));
        }
    }

    /**
     * 校验引擎名：只接受空（自动）或已探测到的候选名
     *
     * @param type 引擎名
     */
    private void validateType(String type) {
        if (type == null || type.isBlank()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (ApmProvider provider : registry.candidates()) {
            names.add(provider.name());
        }
        for (String name : names) {
            if (name.equalsIgnoreCase(type)) {
                return;
            }
        }
        throw new IllegalArgumentException("未引入引擎「" + type + "」，可选：" + names
                + "；留空表示自动选择第一个可用引擎");
    }

    /**
     * 候选引擎（含不可用原因），供界面下拉与排障
     *
     * @return 列表
     */
    private List<Map<String, Object>> candidates() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ApmProvider provider : registry.candidates()) {
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("name", provider.name());
            item.put("available", provider.status().available());
            item.put("mode", provider.mode());
            item.put("version", provider.status().version());
            item.put("endpoint", provider.status().endpoint());
            item.put("message", provider.status().message());
            item.put("settings", new LinkedHashMap<>(provider.runtimeSettings()));
            list.add(item);
        }
        return list;
    }

    /**
     * 布尔解析（接受 true/false/1/0/on/off）
     *
     * @param raw  原始值
     * @param name 键名（错误信息用）
     * @return 布尔值
     */
    private Boolean parseBoolean(String raw, String name) {
        String value = trim(raw);
        if (value == null) {
            throw new IllegalArgumentException("「" + name + "」不能为空");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if ("true".equals(lower) || "1".equals(lower) || "on".equals(lower)) {
            return Boolean.TRUE;
        }
        if ("false".equals(lower) || "0".equals(lower) || "off".equals(lower)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("「" + name + "」只能是 true/false，收到: " + raw);
    }

    /**
     * 探测缓存时长解析
     *
     * @param raw 原始值
     * @return 毫秒
     */
    private Long parseInterval(String raw) {
        String value = trim(raw);
        if (value == null) {
            throw new IllegalArgumentException("「detectInterval」不能为空");
        }
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("「detectInterval」必须是整数毫秒，收到: " + raw);
        }
        if (parsed < MIN_DETECT_INTERVAL) {
            throw new IllegalArgumentException("「detectInterval」不能小于 " + MIN_DETECT_INTERVAL
                    + " 毫秒：再小等于每次请求都去探活");
        }
        return parsed;
    }

    /**
     * 去空白
     *
     * @param raw 原始值
     * @return 去空白后的值；全空白返回 null
     */
    private String trim(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }
}
