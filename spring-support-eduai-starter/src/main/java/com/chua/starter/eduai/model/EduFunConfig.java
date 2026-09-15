package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 趣味模块配置实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_fun_config")
public class EduFunConfig {

    /**
     * 配置标识
     */
    @TableId(type = IdType.AUTO)
    private Long configId;

    /**
     * 模块编码（如 INSECT, IDIOM, IDIOM_GAME）
     */
    private String moduleCode;

    /**
     * 模块名称
     */
    private String moduleName;

    /**
     * 模块描述
     */
    private String moduleDesc;

    /**
     * 模块图标
     */
    private String moduleIcon;

    /**
     * 模块路径（前端路由或iframe URL）
     */
    private String modulePath;

    /**
     * 是否启用（1=启用, 0=禁用）
     */
    private Integer enabled;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 配置JSON（扩展配置）
     */
    private String configJson;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
