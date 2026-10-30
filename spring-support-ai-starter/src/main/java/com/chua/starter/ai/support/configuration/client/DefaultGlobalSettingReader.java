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
 * {@link GlobalSettingReader} 的默认实现：YAML 覆盖 &gt; 用户覆盖 &gt; 全局数据库 &gt; 代码默认。
 *
 * <p>启动时把生效的 YAML 覆盖清单打进日志，运维改完 yaml 一眼能确认加载成功；
 * 未配置时保持安静（debug），行为与引入覆盖层之前完全一致。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGlobalSettingReader implements GlobalSettingReader {

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
     * {@inheritDoc}
     *
     * @param group 分组
     * @param field 字段名
     * @return 值，未配置时返回 空
     */
    @Override
    public String getStr(String group, String field) {
        return getStrAs(currentUserIdOrNull(), group, field);
    }

    /**
     * {@inheritDoc}
     *
     * @param userId 用户标识；为空表示只读全局层
     * @param group  分组
     * @param field  字段名
     * @return 生效值，未配置时返回 空
     */
    @Override
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
     * @param userId 用户标识
     * @param group  分组
     * @param field  字段名
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
     * {@inheritDoc}
     *
     * @param group 分组
     * @param field 字段名
     * @return 值，未配置时返回 空
     */
    @Override
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
     * {@inheritDoc}
     *
     * @param group        分组
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 布尔值
     */
    @Override
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
     * {@inheritDoc}
     *
     * @param group        分组
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 整数值
     */
    @Override
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
     * {@inheritDoc}
     *
     * @param group 分组
     * @param field 字段名
     * @return 整数值，未配置或解析失败时返回 空
     */
    @Override
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
     * {@inheritDoc}
     *
     * @param group 分组
     * @param field 字段名
     * @return 长整数值，未配置或解析失败时返回 空
     */
    @Override
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
     * {@inheritDoc}
     *
     * @param group 分组
     * @param field 字段名
     * @return 小数值，未配置或解析失败时返回 空
     */
    @Override
    public BigDecimal getDecimalOrNull(String group, String field) {
        String v = getStr(group, field);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            log.debug("[GlobalSettingReader] 字段 {} 非小数: {}", field, v);
            return null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param group        分组
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 小数值
     */
    @Override
    public double getDouble(String group, String field, double defaultValue) {
        BigDecimal value = getDecimalOrNull(group, field);
        return value != null ? value.doubleValue() : defaultValue;
    }
}
