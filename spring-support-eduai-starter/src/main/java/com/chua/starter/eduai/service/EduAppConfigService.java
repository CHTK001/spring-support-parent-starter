package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.starter.eduai.dimension.entity.EduAppConfig;
import com.chua.starter.eduai.dimension.mapper.EduAppConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 应用配置服务：键值开关的读写入口。
 *
 * <p>当前开关：</p>
 * <ul>
 *   <li>{@code teacher-question-auto-approve} — 老师上传题目是否自动通过。
 *       默认 false：老师上传的题目进入待审核队列（{@code question_audit_status=0}），
 *       由管理员在题库「审核状态」里审批；开启后老师上传直接入库为已通过。</li>
 *   <li>{@code marking-*} — 阅卷运行时设置（重试次数 / 退避曲线 / 识别策略），
 *       由系统配置弹窗维护，见 {@code MarkingSettingService}。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class EduAppConfigService {

    /**
     * 老师上传题目自动通过开关键名
     */
    public static final String KEY_TEACHER_QUESTION_AUTO_APPROVE = "teacher-question-auto-approve";

    /**
     * 阅卷模型调用最大重试次数键名
     */
    public static final String KEY_MARKING_RETRY_TIMES = "marking-retry-times";

    /**
     * 阅卷模型调用初始退避毫秒键名
     */
    public static final String KEY_MARKING_RETRY_BACKOFF_MILLIS = "marking-retry-backoff-millis";

    /**
     * 阅卷模型调用退避倍数键名
     */
    public static final String KEY_MARKING_RETRY_BACKOFF_MULTIPLIER = "marking-retry-backoff-multiplier";

    /**
     * 阅卷模型调用退避上限毫秒键名
     */
    public static final String KEY_MARKING_RETRY_MAX_BACKOFF_MILLIS = "marking-retry-max-backoff-millis";

    /**
     * 阅卷模型调用是否重试瞬时故障键名
     */
    public static final String KEY_MARKING_RETRY_ON_TRANSIENT = "marking-retry-on-transient";

    /**
     * 任意卷识别策略键名
     */
    public static final String KEY_MARKING_EXTRACT_STRATEGY = "marking-extract-strategy";

    private final EduAppConfigMapper appConfigMapper;

    /**
     * 读取配置值
     *
     * @param key 配置键
     * @return 配置值；不存在时为 null
     */
    public String get(String key) {
        EduAppConfig config = appConfigMapper.selectOne(
                new QueryWrapper<EduAppConfig>().eq("config_key", key).last("LIMIT 1"));
        return config == null ? null : config.getConfigValue();
    }

    /**
     * 写入配置值（不存在则插入，存在则更新）
     *
     * @param key   配置键
     * @param value 配置值
     */
    @Transactional(rollbackFor = Exception.class)
    public void set(String key, String value) {
        EduAppConfig config = appConfigMapper.selectOne(
                new QueryWrapper<EduAppConfig>().eq("config_key", key).last("LIMIT 1"));
        if (config == null) {
            config = new EduAppConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setUpdateTime(LocalDateTime.now());
            appConfigMapper.insert(config);
            return;
        }
        config.setConfigValue(value);
        config.setUpdateTime(LocalDateTime.now());
        appConfigMapper.updateById(config);
    }

    /**
     * 老师上传题目是否自动通过
     *
     * @return true=老师上传直接入库为已通过；false=进入待审核队列（默认）
     */
    public boolean isTeacherQuestionAutoApprove() {
        String value = get(KEY_TEACHER_QUESTION_AUTO_APPROVE);
        return "1".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }

    /**
     * 读取去空白后的文本值
     *
     * @param key 配置键
     * @return 文本值；不存在或空白时为 null
     */
    public String getText(String key) {
        String value = get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 读取整型值
     *
     * @param key 配置键
     * @return 整型值；不存在或非法时为 null
     */
    public Integer getInteger(String key) {
        String value = getText(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取长整型值
     *
     * @param key 配置键
     * @return 长整型值；不存在或非法时为 null
     */
    public Long getLong(String key) {
        String value = getText(key);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取浮点值
     *
     * @param key 配置键
     * @return 浮点值；不存在或非法时为 null
     */
    public Double getDouble(String key) {
        String value = getText(key);
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取布尔值（1 / true / yes 视为 true）
     *
     * @param key 配置键
     * @return 布尔值；不存在时为 null
     */
    public Boolean getBoolean(String key) {
        String value = getText(key);
        if (value == null) {
            return null;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }
}
