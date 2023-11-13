package com.chua.starter.unified.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@TableName(value = "unified_log")
public class UnifiedLog implements Serializable {
    @TableId(value = "unified_log_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer unifiedLogId;

    /**
     * 模块类型
     */
    @TableField(value = "unified_log_type")
    @Size(max = 255,message = "模块类型最大长度要小于 255")
    private String unifiedLogType;

    /**
     * 日志信息
     */
    @TableField(value = "unified_log_msg")
    @Size(max = 255,message = "日志信息最大长度要小于 255")
    private String unifiedLogMsg;

    /**
     * 状态
     */
    @TableField(value = "unified_log_code")
    @Size(max = 255,message = "状态最大长度要小于 255")
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