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
 * </ul>
 *
 * <p>阅卷运行时设置（重试 / 识别策略 / 匹配模式 / 判分提示词）<b>不在这里</b>：
 * 已迁到 AI 全局设置表的 {@code marking} 分组，见 {@code MarkingSettingService}。
 * eduai 自己的 {@code edu_app_config} 只留业务开关，不再承担模型管线配置。</p>
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
}
