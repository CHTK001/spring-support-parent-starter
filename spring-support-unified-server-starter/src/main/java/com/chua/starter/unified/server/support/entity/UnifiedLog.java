package com.chua.starter.unified.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 * @author CH
 */
@Data
@TableName(value = "unified_log")
public class UnifiedLog implements Serializable {
    @TableId(value = "unified_log_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer unifiedLogId;

    /**
     * 模块类型
     */
    @TableField(value = "unified_log_module_type")
    @Size(max = 255, message = "模块类型最大长度要小于 255")
    private String unifiedLogModuleType;

    /**
     * 响应
     */
    @TableField(value = "unified_log_res")
    private String unifiedLogRes;
    /**
     * 请求
     */
    @TableField(value = "unified_log_req")
    private String unifiedLogReq;
    /**
     * 耗时(ms)
     */
    @TableField(value = "unified_log_cost")
    private Long unifiedLogCost;

    /**
     * 日志信息
     */
    @TableField(value = "unified_log_msg")
    @Size(max = 255, message = "日志信息最大长度要小于 255")
    private String unifiedLogMsg;

    /**
     * 状态
     */
    @TableField(value = "unified_log_code")
    @Size(max = 255, message = "状态最大长度要小于 255")
    private String unifiedLogCode;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}