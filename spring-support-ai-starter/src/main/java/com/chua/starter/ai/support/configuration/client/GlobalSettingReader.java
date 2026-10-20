package com.chua.starter.ai.support.configuration.client;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiUserSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 全局设置读取工具 —— 统一封装 {@code sys_ai_global_setting} 键值表的类型安全读取。
 *
 * <p>各配置消费者（GlobalPanel、RAG、ChatClient、用量任务）统一通过本工具读取
 * 指定分组的键值配置，避免重复手写字符串解析。</p>
 *
 * <p><b>取值链（自上而下，命中即止）：</b></p>
 * <ol>
 *   <li>YAML 覆盖层 {@code plugin.ai.settings.<group>.<field>}（部署级钦定，重启生效）——
 *       见 {@link AiSettingsYamlProperties}，运维换 layout/OCR 管线走这里；</li>
 *   <li>用户覆盖 {@code sys_ai_user_setting}（设置页按用户保存）；</li>
 *   <li>全局数据库 {@code sys_ai_global_setting}（设置页保存，热更新）；</li>
 *   <li>消费方代码默认值（getBool/getInt 等的 defaultValue 参数）。</li>
 * </ol>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalSettingReader {

    /**
     * 键值全局设置服务
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * 用户级设置服务：读取当前用户的覆盖值（{@code sys_ai_user_setting}，按 userId 隔离）
     */
    private final SysAiUserSettingService userSettingService;

    /**
     * 外部服务器目录模型绑定支持
     */
    private final ExternalModelSupport externalModelSupport;

    /**
     * YAML 覆盖层（{@code plugin.ai.settings.*}）：部署级钦定，优先于用户覆盖与数据库
     */
    private final AiSettingsYamlProperties yamlSettings;

    /**
     * 启动自证：把生效的覆盖清单打进日志，运维改完 yaml 一眼能确认加载成功；
     * 未配置时保持安静（debug），行为与引入本覆盖层之前完全一致。
     */
    @PostConstruct
    public void logYamlOverrides() {
        if (yamlSettings == null || yamlSettings.isEmpty()) {
            log.debug("[GlobalSettingReader] YAML 覆盖层未配置（plugin.ai.settings.*），取值链为 用户覆盖 > 数据库 > 代码默认");
            return;
        }
        log.info("[GlobalSettingReader] YAML 覆盖层已加载，命中键优先于用户覆盖与数据库: {}", yamlSettings.summary());
    }

    /**
     * 读取字符串值。
     *
     * <p><b>分层语义：YAML 覆盖 &gt; 当前用户的覆盖 &gt; 全局默认。</b>
     * 后端把 runtime / rag / face / ocr / image / image_search / generate / layout /
     * input / memory / bot / asr 等分组声明为「每个用户管自己的默认」
     * （见 {@code SettingsController.USER_WRITABLE_GROUPS}），而各业务消费端
     * （FaceClientManager / ImageRecognitionClientManager / LayoutClientManager /
     * RagClientManager / 记忆抽取 / 输入优化 …）统一走本方法取值，
     * 所以用户层必须在这里生效，否则用户在设置页改的模型「保存成功却不生效」。</p>
     *
     * <p>没有用户覆盖时行为与以前完全一致（只读全局层）。</p>
     *
     * @param group 分组
     * @param field 字段名
     * @return 值，未配置时返回 空
     */
    public String getStr(String group, String field) {
        return getStrAs(currentUserIdOrNull(), group, field);
    }

    /**
     * 按**指定用户**读取字符串值（不依赖调用线程的登录上下文）。
     *
     * <p>用于「重建某个账号的实例」这类场景：重建线程上往往没有该账号的上下文
     * （例如由用户 A 触发、却要重建**所有账号**的管线）。此时必须显式传入账号，
     * 否则会把所有账号都按触发者的配置建 —— 那比「改了不生效」更糟。</p>
     *
     * <p>语义与 {@link #getStr(String, String)} 一致：YAML 覆盖最高，其次用户覆盖，
     * 且**有覆盖行就以它为准**（哪怕值为空，即「显式清空」不回退全局）。</p>
     *
     * @param userId 用户标识；为空表示只读全局层
     * @param group  分组
     * @param field  字段名
     * @return 生效值，未配置时返回 空
     */
    public String getStrAs(String userId, String group, String field) {
        String yamlValue = yamlValueOf(group, field);
        if (yamlValue != null) {
            // 与数据库值同权：非空同样走 ONNX 外部目录注册；空串为「强制清空」，原样返回
            return yamlValue.isBlank() ? yamlValue : externalModelSupport.prepare(group, field, yamlValue);
        }
        if (userId != null && !userId.isBlank()) {
            String userValue = userOverrideOf(userId, group, field);
            if (userValue != null) {
                // ONNX 外部目录：若是已存在的服务器目录，按字段模板动态注册为可运行模型
                return externalModelSupport.prepare(group, field, userValue);
            }
        }
        return getStrGlobal(group, field);
    }

    /**
     * 读取 YAML 覆盖层的命中值。
     *
     * @param group 分组
     * @param field 字段名
     * @return 命中返回生效值（可为显式空串）；yaml 未配置该键返回 {@code null}
     */
    private String yamlValueOf(String group, String field) {
        if (yamlSettings == null) {
            return null;
        }
        return yamlSettings.get(group, field);
    }

    /**
     * 取当前登录用户标识；无请求上下文（后台线程、未登录）时返回 空。
     *
     * @return 用户标识或 空
     */
    private static String currentUserIdOrNull() {
        try {
            return StringUtils.trimToNull(AiUserContext.currentUserId());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 只读全局层（不带用户覆盖）。
     *
     * @param group 分组
     * @param field 字段名
     * @return 全局值，未配置时返回 空
     */
    private String getStrGlobal(String group, String field) {
        String raw;
        try {
            raw = sysAiGlobalSettingService.getValue(group, field).orElse(null);
        } catch (Exception e) {
            // Redis 缓存不可达时不阻断调用方（如启动期定时任务），回落到直接读库
            log.warn("[GlobalSettingReader] 缓存读取失败(group={},field={})，回落数据库: {}",
                    group, field, e.getMessage());
            try {
                raw = sysAiGlobalSettingService.getValueDirect(group, field).orElse(null);
            } catch (Exception ex) {
                log.warn("[GlobalSettingReader] 数据库回落仍失败: {}", ex.getMessage());
                raw = null;
            }
        }
        // ONNX 外部目录：若是已存在的服务器目录，按字段模板动态注册为可运行模型
        return externalModelSupport.prepare(group, field, raw);
    }

    /**
     * 读取当前用户在该字段上的覆盖值。
     *
     * <p>取不到用户上下文（后台线程、启动期、未登录）时返回 空，由全局默认兜底。</p>
     *
     * @param group 分组
     * @param field 字段名
     * @return 用户覆盖值；未登录或该字段未覆盖时返回 {@code null}
     */
    private String userOverrideOf(String userId, String group, String field) {
        try {
            if (userId == null || userId.isBlank()) {
                return null;
            }
            return userSettingService.getUserValue(userId, group, field).orElse(null);
        } catch (Exception e) {
            log.debug("[GlobalSettingReader] 读取用户覆盖失败(group={},field={}): {}",
                    group, field, e.getMessage());
            return null;
        }
    }

    /**
     * 读取字符串值（<b>不经缓存</b>，直连数据库）。
     *
     * <p>为什么需要它：{@link #getStr} 走的是 {@code @Cacheable(REDIS_CACHE_ALWAYS)} 的
     * Redis 缓存，只有页面的保存路径会主动失效。绕过页面直接改库（运维改库、脚本改库）时
     * 会读到旧值 —— 线上实测过：默认模型在库里已是新值，缓存里仍是已废弃的旧模型名，
     * 进程就一直按旧值解析，报出来的错误与库里配置完全对不上，极难排查。</p>
     *
     * <p>因此「错一次就整条链路失败」的模型解析必须走本方法。
     * YAML 覆盖层（{@code plugin.ai.settings.*}）不受缓存语义影响，仍在此方法链首命中 ——
     * 部署钦定的值不存在「读到旧值」的问题。</p>
     *
     * @param group 分组
     * @param field 字段名
     * @return 值，未配置时返回 空
     */
    public String getStrDirect(String group, String field) {
        String yamlValue = yamlValueOf(group, field);
        if (yamlValue != null) {
            return yamlValue.isBlank() ? yamlValue : externalModelSupport.prepare(group, field, yamlValue);
        }
        String raw;
        try {
            raw = sysAiGlobalSettingService.getValueDirect(group, field).orElse(null);
        } catch (Exception e) {
            log.warn("[GlobalSettingReader] 直读数据库失败(group={},field={}): {}", group, field, e.getMessage());
            raw = null;
        }
        return externalModelSupport.prepare(group, field, raw);
    }

    /**
     * 读取布尔值。
     *
     * @param group        分组
     * @param field        字段名
     * @param defaultValue 未配置或解析失败时的默认值
     * @return 布尔值
     */
    public boolean getBool(String group, String field, boolean defaultValue) {
        // 统一走 getStr，使其在缓存故障时同样能回落到数据库
        String v = getStr(group, field);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        String lower = v.trim().toLowerCase();
        return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower);
    }

    /**
     * 读取整数值。
     *
     * @param group        分组
     * @param field        字段名
     * @param defaultValue 未配置或解析失败时的默认值
     * @return 整数值
     */
    public int getInt(String group, String field, int defaultValue) {
        String v = getStr(group, field);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            log.debug("[GlobalSettingReader] 字段 {} 非整数: {}", field, v);
            return defaultValue;
        }
    }

    /**
     * 读取可空整数值。
     *
     * @param group 分组
     * @param field 字段名
     * @return 整数值，未配置或解析失败时返回 空
     */
    public Integer getIntOrNull(String group, String field) {
        String v = getStr(group, field);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            log.debug("[GlobalSettingReader] 字段 {} 非整数: {}", field, v);
            return null;
        }
    }

    /**
     * 读取可空长整数值。
     *
     * @param group 分组
     * @param field 字段名
     * @return 长整数值，未配置或解析失败时返回 空
     */
    public Long getLongOrNull(String group, String field) {
        String v = getStr(group, field);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            log.debug("[GlobalSettingReader] 字段 {} 非长整数: {}", field, v);
            return null;
        }
    }

    /**
     * 读取可空小数。
     *
     * @param group 分组
     * @param field 字段名
     * @return 小数值，未配置或解析失败时返回 空
     */
    public BigDecimal getDecimalOrNull(String group, String field) {
        String v = getStr(group, field);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            /**
             * bigdecimal。
             * @param v.trim( v.修剪(
             * @return BigDecimal的结果
             */
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            log.debug("[GlobalSettingReader] 字段 {} 非小数: {}", field, v);
            return null;
        }
    }

    /**
     * 读取小数值，解析失败时返回默认值。
     *
     * @param group        分组
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 小数值
     */
    public double getDouble(String group, String field, double defaultValue) {
        BigDecimal value = getDecimalOrNull(group, field);
        return value != null ? value.doubleValue() : defaultValue;
    }
}
