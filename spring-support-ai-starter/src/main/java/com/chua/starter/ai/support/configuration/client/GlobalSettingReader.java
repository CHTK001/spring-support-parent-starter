package com.chua.starter.ai.support.configuration.client;

import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
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
     * 外部服务器目录模型绑定支持
     */
    private final ExternalModelSupport externalModelSupport;

    /**
     * 读取字符串值。
     *
     * @param group 分组
     * @param field 字段名
     * @return 值，未配置时返回 空
     */
    public String getStr(String group, String field) {
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
