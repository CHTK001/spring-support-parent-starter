package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
* 桌面伙伴共享配置（KV 存储）实体。
*
* <p>持久化到 {@code sys_ai_companion_shared_config} 表。
* 值为 JSON 字符串（服务端解析/序列化为前端 {@code ICompanionSharedConfig}）。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("sys_ai_companion_shared_config")
public class SysAiCompanionSharedConfig implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
    * 配置键（如 共享、consent、disable_全部）
    */
    @TableId(value = "sys_ai_companion_shared_config_key", type = IdType.INPUT)
    private String configKey; // 配置Key

    /**
    * 配置值（JSON 字符串）
    */
    @TableField("sys_ai_companion_shared_config_value")
    private String configValue; // 配置值

    /**
    * 更新时间（轮次 毫秒）
    */
    @TableField("sys_ai_companion_shared_config_updated_at")
    private Long updatedAt; // 更新at
}
