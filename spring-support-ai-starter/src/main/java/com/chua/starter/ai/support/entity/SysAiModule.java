package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 模块实体，对应表 {@code sys_ai_module}。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_module")
public class SysAiModule {

    /**
     * 模块主键 ID
     */
    @TableId(type = IdType.AUTO)
    /** SYSAImoduleID */
    private Integer sysAiModuleId;

    /**
     * 模块编码
     */
    @TableField("sys_ai_module_code")
    /** SYSAImodule代码 */
    private String sysAiModuleCode;

    /**
     * 模块名称
     */
    @TableField("sys_ai_module_name")
    /** SYSAImodule名称 */
    private String sysAiModuleName;

    /**
     * 模块类型（chat/image/video/colorize/vision/rag 等）
     */
    @TableField("sys_ai_module_type")
    /** SYSAImodule类型 */
    private String sysAiModuleType;

    /**
     * 模块厂商
     */
    @TableField("sys_ai_module_vendor")
    /** SYSAImodulevendor */
    private String sysAiModuleVendor;

    /**
     * 支持的厂商列表（逗号分隔）
     */
    @TableField("sys_ai_module_manufacturers")
    /** SYSAImodulemanufacturers */
    private String sysAiModuleManufacturers;

    /**
     * 是否暴露 API：1 暴露，0 不暴露
     */
    @TableField("sys_ai_module_api_expose")
    /** SYSAImoduleAPIexpose */
    private Integer sysAiModuleApiExpose;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_module_status")
    /** SYSAImodule状态 */
    private Integer sysAiModuleStatus;

    /**
     * 排序值
     */
    @TableField("sys_ai_module_sort")
    /** SYSAImodulesort */
    private Integer sysAiModuleSort;

    /**
     * 模块描述
     */
    @TableField("sys_ai_module_remark")
    /** SYSAImodule描述 */
    private String sysAiModuleDescription;

    /**
     * 创建时间
     */
    @TableField("create_time")
    /** Create时间 */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    /** Update时间 */
    private LocalDateTime updateTime;

    /**
     * 模块的配置项列表（仅内存使用，不映射数据库字段）。
     */
    @TableField(exist = false)
    private transient List<SysAiSetting> sysAiSettings;
}

