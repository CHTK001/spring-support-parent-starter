package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.entity.CollectSetting;
import com.chua.starter.collect.mapper.CollectSettingMapper;
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.service.CollectSettingsService;
import com.chua.starter.collect.vo.CollectSettingVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 采集平台运行参数服务实现。
 *
 * <p>定义表是唯一的可信来源：键、类型、范围、是否重启都在这里声明，
 * 前端只按接口返回的元数据渲染，不在前端硬编码任何一项。缺省值在构造时从
 * {@link CollectProperties} 抓一份快照，之后 yml 值不会被运行期修改污染。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@Order(-100)
public class CollectSettingsServiceImpl extends ServiceImpl<CollectSettingMapper, CollectSetting>
        implements CollectSettingsService, ApplicationRunner {

    /**
     * 文本值最大长度，与表字段一致。
     */
    private static final int MAX_TEXT_LENGTH = 2000;

    /**
     * 运行期配置。
     */
    private final CollectProperties properties;

    /**
     * 参数定义，保持声明顺序供界面分组渲染。
     */
    private final Map<String, Def> defs = new LinkedHashMap<>();

    /**
     * yml 缺省值快照。
     */
    private final Map<String, String> baseline = new LinkedHashMap<>();

    /**
     * 已被数据库覆盖的键值。
     */
    private final Map<String, String> overrides = new LinkedHashMap<>();

    /**
     * 是否已尝试装载数据库覆盖值。
     */
    private boolean loaded;

    /**
     * 构造服务并抓取缺省值快照。
     *
     * @param properties 运行期配置
     */
    public CollectSettingsServiceImpl(CollectProperties properties) {
        this.properties = properties;
        for (Def def : definitions()) {
            defs.put(def.key(), def);
            baseline.put(def.key(), def.getter().apply(properties));
        }
    }

    /**
     * 应用启动后兜底装载一次（执行器注册表通常已经先调用过）。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        initialize();
    }

    @Override
    public synchronized void initialize() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            List<CollectSetting> rows = list();
            for (CollectSetting row : rows) {
                Def def = defs.get(row.getCollectSettingKey());
                if (def == null) {
                    log.warn("[collect] 忽略未知运行参数 key={}", row.getCollectSettingKey());
                    continue;
                }
                try {
                    String value = normalize(def, row.getCollectSettingValue());
                    def.setter().accept(properties, value);
                    overrides.put(def.key(), value);
                } catch (RuntimeException ex) {
                    log.warn("[collect] 运行参数 {} 的值不合法，已跳过: {}", def.key(), ex.getMessage());
                }
            }
            if (!overrides.isEmpty()) {
                log.info("[collect] 已装载界面覆盖的运行参数 {} 项: {}", overrides.size(), overrides.keySet());
            }
        } catch (RuntimeException ex) {
            log.warn("[collect] 运行参数覆盖装载失败，继续使用 yml 缺省: {}", ex.getMessage());
        }
    }

    @Override
    public synchronized List<CollectSettingVO> listSettings() {
        List<CollectSettingVO> result = new ArrayList<>(defs.size());
        for (Def def : defs.values()) {
            CollectSettingVO vo = new CollectSettingVO();
            vo.setKey(def.key());
            vo.setLabel(def.label());
            vo.setGroup(def.group());
            vo.setType(def.type());
            vo.setDescription(def.description());
            vo.setMin(def.min());
            vo.setMax(def.max());
            vo.setUnit(def.unit());
            vo.setOptions(def.options());
            vo.setRestartRequired(def.restartRequired());
            vo.setReadOnly("enable".equals(def.key()));
            vo.setValue(def.getter().apply(properties));
            vo.setDefaultValue(baseline.get(def.key()));
            vo.setOverridden(overrides.containsKey(def.key()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public synchronized List<CollectSettingVO> saveSettings(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return listSettings();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Def def = defs.get(entry.getKey());
            if (def == null) {
                throw new IllegalArgumentException("未知的采集运行参数: " + entry.getKey());
            }
            if ("enable".equals(def.key())) {
                throw new IllegalArgumentException("平台总开关是部署级配置，只能改 application.yml 的 plugin.collect.enable");
            }
            normalized.put(def.key(), normalize(def, entry.getValue()));
        }
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            persist(entry.getKey(), entry.getValue(), now);
            defs.get(entry.getKey()).setter().accept(properties, entry.getValue());
            overrides.put(entry.getKey(), entry.getValue());
        }
        log.info("[collect] 运行参数已更新 {}: {}", normalized.keySet(), normalized);
        return listSettings();
    }

    @Override
    public synchronized List<CollectSettingVO> resetSettings(List<String> keys) {
        List<String> targets;
        if (keys == null || keys.isEmpty()) {
            targets = new ArrayList<>(overrides.keySet());
        } else {
            targets = new ArrayList<>(keys);
            for (String key : targets) {
                if (!defs.containsKey(key)) {
                    throw new IllegalArgumentException("未知的采集运行参数: " + key);
                }
                if ("enable".equals(key)) {
                    throw new IllegalArgumentException("平台总开关是部署级配置，只能改 application.yml 的 plugin.collect.enable");
                }
            }
        }
        if (targets.isEmpty()) {
            return listSettings();
        }
        remove(Wrappers.<CollectSetting>lambdaQuery().in(CollectSetting::getCollectSettingKey, targets));
        for (String key : targets) {
            Def def = defs.get(key);
            if (def == null) {
                continue;
            }
            def.setter().accept(properties, baseline.get(key));
            overrides.remove(key);
        }
        log.info("[collect] 运行参数已恢复缺省 {}", targets);
        return listSettings();
    }

    /**
     * 写一行覆盖值，存在则更新。
     *
     * @param key   配置键
     * @param value 已校验的值
     * @param now   当前时间
     */
    private void persist(String key, String value, LocalDateTime now) {
        CollectSetting existing = getOne(Wrappers.<CollectSetting>lambdaQuery()
                .eq(CollectSetting::getCollectSettingKey, key), false);
        if (existing == null) {
            CollectSetting row = new CollectSetting();
            row.setCollectSettingKey(key);
            row.setCollectSettingValue(value);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            save(row);
            return;
        }
        existing.setCollectSettingValue(value);
        existing.setUpdateTime(now);
        updateById(existing);
    }

    /**
     * 按定义校验并归一化一个值。
     *
     * @param def 定义
     * @param raw 原始值
     * @return 归一化后的值
     */
    private String normalize(Def def, String raw) {
        String value = raw == null ? "" : raw.trim();
        switch (def.type()) {
            case "boolean":
                return normalizeBoolean(def, value);
            case "number":
                return normalizeNumber(def, value);
            case "select":
                if (def.options() == null || !def.options().contains(value)) {
                    throw new IllegalArgumentException(def.label() + " 只能取 " + def.options());
                }
                return value;
            default:
                return normalizeText(def, value);
        }
    }

    /**
     * 布尔值归一化为 true/false。
     *
     * @param def   定义
     * @param value 原始值
     * @return true 或 false
     */
    private String normalizeBoolean(Def def, String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1", "on", "yes" -> "true";
            case "false", "0", "off", "no" -> "false";
            default -> throw new IllegalArgumentException(def.label() + " 只能是 true 或 false");
        };
    }

    /**
     * 数值校验：整数、上下限。
     *
     * @param def   定义
     * @param value 原始值
     * @return 归一化后的整数
     */
    private String normalizeNumber(Def def, String value) {
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(def.label() + " 必须是整数");
        }
        if (def.min() != null && parsed < Long.parseLong(def.min())) {
            throw new IllegalArgumentException(def.label() + " 不能小于 " + def.min());
        }
        if (def.max() != null && parsed > Long.parseLong(def.max())) {
            throw new IllegalArgumentException(def.label() + " 不能大于 " + def.max());
        }
        return String.valueOf(parsed);
    }

    /**
     * 文本校验：长度、AI 地址与必填项。
     *
     * @param def   定义
     * @param value 原始值
     * @return 归一化后的文本
     */
    private String normalizeText(Def def, String value) {
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(def.label() + " 长度不能超过 " + MAX_TEXT_LENGTH);
        }
        if (("user-agent".equals(def.key()) || "ai-base-url".equals(def.key())
                || "ai-model".equals(def.key()) || "ai-trust-header".equals(def.key())
                || "ai-trust-value".equals(def.key())) && value.isEmpty()) {
            throw new IllegalArgumentException(def.label() + " 不能为空");
        }
        if ("ai-base-url".equals(def.key())
                && !value.startsWith("http://") && !value.startsWith("https://")) {
            throw new IllegalArgumentException("AI 服务地址必须以 http:// 或 https:// 开头");
        }
        return value;
    }

    /**
     * 参数定义清单。
     *
     * <p>键名与 {@code application.yml} 中的 kebab-case 保持一致，避免界面和配置文件两套名字。
     * 平台总开关 {@code plugin.collect.enable} 不在其中：关掉它连本接口都不会装配，
     * 属于部署级开关，只能改 yml。</p>
     *
     * @return 定义列表
     */
    private List<Def> definitions() {
        List<Def> list = new ArrayList<>();
        list.add(new Def("enable", "平台总开关", "平台", "boolean",
                "部署级开关，仅只读展示：关闭后采集引擎、调度任务与全部 /v2/collect 接口停止装配，"
                        + "本配置页也会消失，必须改 application.yml 的 plugin.collect.enable。",
                null, null, null, null, false,
                p -> text(p.isEnable()),
                (p, v) -> p.setEnable(Boolean.parseBoolean(v))));
        list.add(new Def("default-thread-count", "流程默认并行线程数", "执行", "number",
                "流程未显式指定线程数时使用；调大受机器与目标站点承受能力限制。",
                "1", "64", "个", null, false,
                p -> text(p.getDefaultThreadCount()),
                (p, v) -> p.setDefaultThreadCount(Integer.parseInt(v))));
        list.add(new Def("task-timeout-ms", "单任务最长执行时间", "执行", "number",
                "超时后任务置为失败并中断流程线程池，只防线程永久挂死。",
                "60000", "86400000", "毫秒", null, false,
                p -> text(p.getTaskTimeoutMs()),
                (p, v) -> p.setTaskTimeoutMs(Long.parseLong(v))));
        list.add(new Def("max-record-per-task", "单任务最多落库记录数", "执行", "number",
                "超出后丢弃并写任务日志，防止失控抓取撑爆结果表。",
                "1", "10000000", "条", null, false,
                p -> text(p.getMaxRecordPerTask()),
                (p, v) -> p.setMaxRecordPerTask(Integer.parseInt(v))));
        list.add(new Def("preview-length", "节点出参预览长度", "执行", "number",
                "运行详情里每个节点出参保留的字符数。",
                "50", "10000", "字符", null, false,
                p -> text(p.getPreviewLength()),
                (p, v) -> p.setPreviewLength(Integer.parseInt(v))));
        list.add(new Def("node-output-limit", "节点产出留存上限", "执行", "number",
                "供 ${nodes.<id>.*} 引用的节点产出条数上限；0 表示不限。",
                "0", "1000000", "条", null, false,
                p -> text(p.getNodeOutputLimit()),
                (p, v) -> p.setNodeOutputLimit(Integer.parseInt(v))));
        list.add(new Def("exists-batch-size", "「查已存在」分片大小", "执行", "number",
                "IN 查询每片的字面量条数，过大可能顶到数据库 max_allowed_packet。",
                "1", "5000", "条", null, false,
                p -> text(p.getExistsBatchSize()),
                (p, v) -> p.setExistsBatchSize(Integer.parseInt(v))));

        list.add(new Def("request-timeout-ms", "请求默认超时", "抓取", "number",
                "节点未显式配置超时时间时使用。",
                "1000", "600000", "毫秒", null, false,
                p -> text(p.getRequestTimeoutMs()),
                (p, v) -> p.setRequestTimeoutMs(Integer.parseInt(v))));
        list.add(new Def("request-retry-times", "请求默认重试次数", "抓取", "number",
                "节点未显式配置重试次数时使用。",
                "0", "10", "次", null, false,
                p -> text(p.getRequestRetryTimes()),
                (p, v) -> p.setRequestRetryTimes(Integer.parseInt(v))));
        list.add(new Def("user-agent", "默认 User-Agent", "抓取", "text",
                "节点未显式配置请求头时使用。",
                null, null, null, null, false,
                CollectProperties::getUserAgent,
                CollectProperties::setUserAgent));

        list.add(new Def("sql-allow-write", "允许 SQL 节点写库", "安全", "boolean",
                "关闭时 SQL 节点只放行查询；写库请使用 database 节点（预编译绑定）。",
                null, null, null, null, false,
                p -> text(p.isSqlAllowWrite()),
                (p, v) -> p.setSqlAllowWrite(Boolean.parseBoolean(v))));

        list.add(new Def("script-enable", "启用脚本节点", "能力开关", "boolean",
                "关闭后脚本执行器不注册，工具箱不出现脚本节点，含脚本节点的流程校验失败。",
                null, null, null, null, true,
                p -> text(p.getScript().isEnable()),
                (p, v) -> p.getScript().setEnable(Boolean.parseBoolean(v))));
        list.add(new Def("function-enable", "启用自定义函数库", "能力开关", "boolean",
                "关闭后 ${} 表达式与脚本里只剩内置函数，已登记的函数定义保留。",
                null, null, null, null, true,
                p -> text(p.getFunction().isEnable()),
                (p, v) -> p.getFunction().setEnable(Boolean.parseBoolean(v))));
        list.add(new Def("process-enable", "启用子流程节点", "能力开关", "boolean",
                "关闭后子流程执行器不注册，含该节点的流程校验失败。",
                null, null, null, null, true,
                p -> text(p.getProcess().isEnable()),
                (p, v) -> p.getProcess().setEnable(Boolean.parseBoolean(v))));
        list.add(new Def("process-max-depth", "子流程嵌套层数上限", "能力开关", "number",
                "超过后子流程节点直接失败，防止引用链过深耗尽线程池。",
                "1", "20", "层", null, false,
                p -> text(p.getProcess().getMaxDepth()),
                (p, v) -> p.getProcess().setMaxDepth(Integer.parseInt(v))));
        list.add(new Def("notify-enable", "启用执行通知", "能力开关", "boolean",
                "关闭后任务终态不再投递通知，配置页试发会直接失败并给出原因。",
                null, null, null, null, false,
                p -> text(p.getNotify().isEnable()),
                (p, v) -> p.getNotify().setEnable(Boolean.parseBoolean(v))));

        list.add(new Def("ai-enable", "启用 AI 生成流程", "AI", "boolean",
                "关闭后设计器「AI 生成」直接报未启用。",
                null, null, null, null, false,
                p -> text(p.getAi().isEnable()),
                (p, v) -> p.getAi().setEnable(Boolean.parseBoolean(v))));
        list.add(new Def("ai-base-url", "AI 服务地址", "AI", "text",
                "ai-starter 的地址；同机部署用回环地址。",
                null, null, null, null, false,
                p -> p.getAi().getBaseUrl(),
                (p, v) -> p.getAi().setBaseUrl(v)));
        list.add(new Def("ai-model", "AI 生成使用模型", "AI", "text",
                "须为 ai-starter 中已启用的模型。",
                null, null, null, null, false,
                p -> p.getAi().getModel(),
                (p, v) -> p.getAi().setModel(v)));
        list.add(new Def("ai-trust-header", "本地信任头名", "AI", "text",
                "ai-starter 的 LocalTrustAuthFilter 约定头名。",
                null, null, null, null, false,
                p -> p.getAi().getTrustHeader(),
                (p, v) -> p.getAi().setTrustHeader(v)));
        list.add(new Def("ai-trust-value", "本地信任头值", "AI", "text",
                "服务端只要求非空，内容不校验。",
                null, null, null, null, false,
                p -> p.getAi().getTrustValue(),
                (p, v) -> p.getAi().setTrustValue(v)));
        list.add(new Def("ai-timeout-ms", "AI 生成超时", "AI", "number",
                "整图 JSON 生成较慢，默认 3 分钟。",
                "1000", "600000", "毫秒", null, false,
                p -> text(p.getAi().getTimeoutMs()),
                (p, v) -> p.getAi().setTimeoutMs(Integer.parseInt(v))));
        return list;
    }

    /**
     * 值转字符串，null 记为空串。
     *
     * @param value 值
     * @return 字符串
     */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 参数定义。
     *
     * @param key             配置键
     * @param label           显示名
     * @param group           分组
     * @param type            控件类型
     * @param description     说明
     * @param min             数值下限
     * @param max             数值上限
     * @param unit            单位
     * @param options         select 候选项
     * @param restartRequired 是否重启生效
     * @param getter          运行期取值
     * @param setter          运行期写值
     */
    private record Def(String key, String label, String group, String type, String description,
                       String min, String max, String unit, List<String> options, boolean restartRequired,
                       Function<CollectProperties, String> getter,
                       BiConsumer<CollectProperties, String> setter) {
    }
}
