package com.chua.starter.eduai.dimension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用配置（键值）。
 *
 * <p>存放需要运行时读取的开关，如「老师上传题目自动通过」
 * （{@code teacher-question-auto-approve}）。键唯一，值一律
 * 字符串，由 {@code EduAppConfigService} 负责解释。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_app_config")
public class EduAppConfig {

    @TableId(type = IdType.AUTO)
    /**
     * 配置标识
     */
    private Long configId;

    /**
     * 配置键（业务唯一）
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
